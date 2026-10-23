<#
.SYNOPSIS
  Gera a Modality Worklist (MWL) do Orthanc a partir da view worklist_v do GHBAM.

.DESCRIPTION
  Le os exames do dia na worklist_v (via psql) e mantem na pasta do plugin
  OrthancWorklists um arquivo .wl por exame (ghbam_<idimagens>.wl). O equipamento
  consulta a worklist, o tecnico escolhe o paciente e o aparelho carimba no DICOM:
    AccessionNumber = idimagens   (chave usada pelo GHBAM para achar a imagem)
    PatientID       = pacienteid  (conferido pelo GHBAM antes da analise por IA)
  Arquivos de exames que sairam da view sao removidos. Nao toca em .wl sem o prefixo.
  O log registra so contagens (nunca nome de paciente).

  Senha do banco: nunca neste script. Vem do pgpass.conf apontado por -PgPassFile
  (formato: host:porta:banco:usuario:senha), com acesso restrito a SYSTEM/Administradores.

.EXAMPLE
  .\GerarWorklist.ps1 -Simular          # consulta e conta, sem gravar
  .\GerarWorklist.ps1                   # sincroniza a pasta (uso no agendador)
#>
param(
    [string]$PastaWorklist = 'C:\OrthancWorklists',
    [string]$DbHost = '10.200.200.7',
    [int]$DbPort = 5432,
    [string]$DbNome = 'hospitalit',
    [string]$DbUsuario = 'orthanc_worklist',
    [ValidatePattern('^[a-z_][a-z0-9_]*$')]
    [string]$View = 'worklist_orthanc_v',   # criada pela migration V208
    [string]$PgPassFile = 'C:\ProgramData\GHBAM\pgpass.conf',
    [string]$Psql = 'C:\Program Files\PostgreSQL\15\bin\psql.exe',
    [string]$Log = 'C:\ProgramData\GHBAM\worklist.log',
    [switch]$Simular
)

$ErrorActionPreference = 'Stop'

# Colunas da worklist_v -> campos DICOM (conferido com pg_get_viewdef em 2026-10-21).
$Colunas = [ordered]@{
    accession  = 'accessionnumber'
    pacienteid = 'pacienteid'
    nome       = 'pacientenome'
    nascimento = 'pacientenascimento'
    sexo       = 'pacientesexo'
    modalidade = 'modalidade'
    exame      = 'examenome'
    data       = 'atendimentodata'
    hora       = 'atendimentohora'
}
$Prefixo = 'ghbam_'

$Latin1 = [Text.Encoding]::GetEncoding('ISO-8859-1')
$VrLongo = @('OB', 'OW', 'OF', 'SQ', 'UT', 'UN', 'UC', 'UR')

function Escrever-Log([string]$msg) {
    $linha = '{0:yyyy-MM-dd HH:mm:ss} {1}' -f (Get-Date), $msg
    try { Add-Content -Path $Log -Value $linha -Encoding UTF8 } catch { }
    Write-Output $linha
}

function Sem-Acentos([string]$s) {
    $n = $s.Normalize([Text.NormalizationForm]::FormD)
    -join ($n.ToCharArray() | Where-Object {
            [Globalization.CharUnicodeInfo]::GetUnicodeCategory($_) -ne [Globalization.UnicodeCategory]::NonSpacingMark })
}

# Texto seguro para DICOM: sem acento, maiusculo, sem delimitadores (\ ^ =), no tamanho do VR.
function Limpar([string]$s, [int]$max) {
    if ([string]::IsNullOrWhiteSpace($s)) { return '' }
    $s = (Sem-Acentos $s).ToUpperInvariant() -replace '[\\^=\r\n\t]', ' ' -replace '\s+', ' '
    $s = $s.Trim()
    if ($s.Length -gt $max) { $s = $s.Substring(0, $max).Trim() }
    return $s
}

# Aceita aaaa-MM-dd, aaaa/MM/dd (formato da recepcao), dd/MM/aaaa e aaaaMMdd.
function Data-Dicom([string]$v) {
    if ([string]::IsNullOrWhiteSpace($v)) { return '' }
    $v = $v.Trim()
    if ($v -match '^(\d{4})[-/](\d{2})[-/](\d{2})') { return "$($Matches[1])$($Matches[2])$($Matches[3])" }
    if ($v -match '^(\d{2})/(\d{2})/(\d{4})') { return "$($Matches[3])$($Matches[2])$($Matches[1])" }
    if ($v -match '^\d{8}$') {
        # A view tira as barras do texto: aaaa/MM/dd vira aaaaMMdd, mas dd/MM/aaaa vira ddMMaaaa.
        $d = [datetime]::MinValue
        foreach ($fmt in 'yyyyMMdd', 'ddMMyyyy') {
            if ([datetime]::TryParseExact($v, $fmt, $null, 'None', [ref]$d)) { return $d.ToString('yyyyMMdd') }
        }
    }
    return ''
}

# atendimentohora vem como HHmm ou HHmmss (a view tira os dois-pontos).
function Hora-Dicom([string]$v) {
    if ("$v".Trim() -match '^(\d{4})(\d{2})?') { return $Matches[1] + $(if ($Matches[2]) { $Matches[2] } else { '00' }) }
    return ''
}

function Sexo-Dicom([string]$v) {
    $v = ("$v").Trim().ToUpperInvariant()
    if ($v -eq '') { return '' }
    if ($v.StartsWith('M')) { return 'M' }
    if ($v.StartsWith('F')) { return 'F' }
    return 'O'
}

# UID estavel por semente (2.25.<md5 como inteiro>): o mesmo exame gera sempre o mesmo UID.
function Uid-Estavel([string]$semente) {
    $md5 = [Security.Cryptography.MD5]::Create()
    $h = $md5.ComputeHash([Text.Encoding]::UTF8.GetBytes($semente))
    [Array]::Reverse($h)
    '2.25.' + ([bigint]::new([byte[]]($h + [byte]0))).ToString()
}

# Elemento DICOM em Explicit VR Little Endian.
function Elemento([uint16]$grupo, [uint16]$elem, [string]$vr, [byte[]]$valor) {
    $ms = New-Object IO.MemoryStream
    $bw = New-Object IO.BinaryWriter($ms)
    $bw.Write($grupo); $bw.Write($elem)
    $bw.Write([byte[]][char[]]$vr)
    if ($VrLongo -contains $vr) { $bw.Write([uint16]0); $bw.Write([uint32]$valor.Length) }
    else { $bw.Write([uint16]$valor.Length) }
    $bw.Write($valor); $bw.Flush()
    , $ms.ToArray()
}

function Texto([uint16]$g, [uint16]$e, [string]$vr, [string]$s) {
    [byte[]]$b = $Latin1.GetBytes($s)
    if ($b.Length % 2) { $b = [byte[]]($b + [byte]$(if ($vr -eq 'UI') { 0 } else { 0x20 })) }
    , (Elemento $g $e $vr $b)
}

function ItemSequencia([byte[]]$conteudo) {
    $ms = New-Object IO.MemoryStream
    $bw = New-Object IO.BinaryWriter($ms)
    $bw.Write([uint16]0xFFFE); $bw.Write([uint16]0xE000); $bw.Write([uint32]$conteudo.Length)
    $bw.Write($conteudo); $bw.Flush()
    , $ms.ToArray()
}

function Arquivo-Wl($r) {
    $sopUid = Uid-Estavel "ghbam-wl-sop-$($r.Accession)"
    $studyUid = Uid-Estavel "ghbam-wl-study-$($r.Accession)"
    $data = if ($r.Data) { $r.Data } else { Get-Date -Format 'yyyyMMdd' }
    $hora = if ($r.Hora) { $r.Hora } else { '000000' }

    # Scheduled Procedure Step (tags em ordem crescente)
    $sps = New-Object 'Collections.Generic.List[byte]'
    $sps.AddRange([byte[]](Texto 0x0008 0x0060 'CS' $r.Modalidade))
    $sps.AddRange([byte[]](Texto 0x0040 0x0002 'DA' $data))
    $sps.AddRange([byte[]](Texto 0x0040 0x0003 'TM' $hora))
    $sps.AddRange([byte[]](Texto 0x0040 0x0007 'LO' $r.Exame))
    $sps.AddRange([byte[]](Texto 0x0040 0x0009 'SH' $r.Accession))

    $ds = New-Object 'Collections.Generic.List[byte]'
    $ds.AddRange([byte[]](Texto 0x0008 0x0005 'CS' 'ISO_IR 100'))
    $ds.AddRange([byte[]](Texto 0x0008 0x0050 'SH' $r.Accession))
    $ds.AddRange([byte[]](Texto 0x0010 0x0010 'PN' $r.Nome))
    $ds.AddRange([byte[]](Texto 0x0010 0x0020 'LO' $r.PacienteId))
    $ds.AddRange([byte[]](Texto 0x0010 0x0030 'DA' $r.Nascimento))
    $ds.AddRange([byte[]](Texto 0x0010 0x0040 'CS' $r.Sexo))
    $ds.AddRange([byte[]](Texto 0x0020 0x000D 'UI' $studyUid))
    $ds.AddRange([byte[]](Texto 0x0032 0x1060 'LO' $r.Exame))
    $ds.AddRange([byte[]](Elemento 0x0040 0x0100 'SQ' (ItemSequencia $sps.ToArray())))
    $ds.AddRange([byte[]](Texto 0x0040 0x1001 'SH' $r.Accession))

    $meta = New-Object 'Collections.Generic.List[byte]'
    $meta.AddRange([byte[]](Elemento 0x0002 0x0001 'OB' ([byte[]](0, 1))))
    $meta.AddRange([byte[]](Texto 0x0002 0x0002 'UI' '1.2.840.10008.5.1.4.31'))   # MWL Information Model - FIND
    $meta.AddRange([byte[]](Texto 0x0002 0x0003 'UI' $sopUid))
    $meta.AddRange([byte[]](Texto 0x0002 0x0010 'UI' '1.2.840.10008.1.2.1'))      # Explicit VR Little Endian
    $meta.AddRange([byte[]](Texto 0x0002 0x0012 'UI' (Uid-Estavel 'ghbam-worklist-gerador')))

    $out = New-Object 'Collections.Generic.List[byte]'
    $out.AddRange([byte[]]::new(128))
    $out.AddRange([byte[]][char[]]'DICM')
    $out.AddRange([byte[]](Elemento 0x0002 0x0000 'UL' ([BitConverter]::GetBytes([uint32]$meta.Count))))
    $out.AddRange($meta)
    $out.AddRange($ds)
    , $out.ToArray()
}

function Montar-Registro($l) {
    $acc = Limpar $l.accession 16
    $idPac = Limpar $l.pacienteid 64
    if (-not $acc -or -not $idPac -or $acc -notmatch '^\d+$') { return $null }
    [pscustomobject]@{
        Accession  = $acc
        PacienteId = $idPac
        Nome       = Limpar $l.nome 64
        Nascimento = Data-Dicom $l.nascimento
        Sexo       = Sexo-Dicom $l.sexo
        Modalidade = Limpar $l.modalidade 16
        Exame      = Limpar $l.exame 64
        Data       = Data-Dicom $l.data
        Hora       = Hora-Dicom $l.hora
    }
}

function Sincronizar {
    if (-not (Test-Path -LiteralPath $PastaWorklist)) { throw "Pasta de worklist nao existe: $PastaWorklist" }
    if (-not (Test-Path -LiteralPath $Psql)) { throw "psql nao encontrado: $Psql" }
    if (-not (Test-Path -LiteralPath $PgPassFile)) { throw "pgpass nao encontrado ou sem acesso: $PgPassFile (rodar como Administrador/SYSTEM)" }
    $pg = [IO.File]::ReadAllBytes($PgPassFile)
    if ($pg.Length -ge 3 -and $pg[0] -eq 0xEF -and $pg[1] -eq 0xBB -and $pg[2] -eq 0xBF) {
        throw "pgpass.conf salvo com BOM (UTF-8 do Bloco de Notas): o psql nao reconhece a linha. Regravar em ASCII."
    }

    $env:PGPASSFILE = $PgPassFile
    $env:PGCLIENTENCODING = 'UTF8'
    [Console]::OutputEncoding = [Text.Encoding]::UTF8

    $sel = ($Colunas.GetEnumerator() | ForEach-Object { '"{0}" AS {1}' -f $_.Value, $_.Key }) -join ', '
    $saida = & $Psql -h $DbHost -p $DbPort -U $DbUsuario -d $DbNome -w -X --csv -c "SELECT $sel FROM $View"
    if ($LASTEXITCODE -ne 0) { throw "psql terminou com codigo $LASTEXITCODE (nada foi alterado)" }
    $linhas = @($saida | ConvertFrom-Csv)

    $desejados = @{}
    $ignorados = 0
    foreach ($l in $linhas) {
        $r = Montar-Registro $l
        if ($null -eq $r) { $ignorados++; continue }
        $desejados["$Prefixo$($r.Accession).wl"] = Arquivo-Wl $r
    }

    $gravados = 0; $iguais = 0; $removidos = 0
    foreach ($nome in $desejados.Keys) {
        $destino = Join-Path $PastaWorklist $nome
        $bytes = $desejados[$nome]
        if ((Test-Path -LiteralPath $destino) -and
            [Convert]::ToBase64String([IO.File]::ReadAllBytes($destino)) -eq [Convert]::ToBase64String($bytes)) {
            $iguais++; continue
        }
        if (-not $Simular) {
            # grava em .tmp e renomeia: o plugin nunca le arquivo pela metade
            $tmp = "$destino.tmp"
            [IO.File]::WriteAllBytes($tmp, $bytes)
            Move-Item -LiteralPath $tmp -Destination $destino -Force
        }
        $gravados++
    }
    Get-ChildItem -LiteralPath $PastaWorklist -Filter "$Prefixo*.wl" |
        Where-Object { -not $desejados.ContainsKey($_.Name) } |
        ForEach-Object {
            if (-not $Simular) { Remove-Item -LiteralPath $_.FullName -Force }
            $removidos++
        }

    $modo = if ($Simular) { 'SIMULACAO' } else { 'OK' }
    Escrever-Log ('{0}: {1} exames na view, {2} gravados, {3} sem mudanca, {4} removidos, {5} ignorados' -f
        $modo, $linhas.Count, $gravados, $iguais, $removidos, $ignorados)
}

# Dot-source (". .\GerarWorklist.ps1") so carrega as funcoes, para teste.
if ($MyInvocation.InvocationName -ne '.') {
    try { Sincronizar }
    catch { Escrever-Log "ERRO: $($_.Exception.Message)"; exit 1 }
}
