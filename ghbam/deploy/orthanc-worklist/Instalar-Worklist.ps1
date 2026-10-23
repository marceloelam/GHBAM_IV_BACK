<#
.SYNOPSIS
  Instala/reinstala a worklist do GHBAM num servidor Windows com Orthanc.

.DESCRIPTION
  Rodar NO SERVIDOR, em PowerShell "Executar como administrador", na pasta onde
  estao este arquivo e o GerarWorklist.ps1. Pode ser rodado de novo (idempotente).

  Faz: confere pre-requisitos -> pastas com acesso restrito -> copia o gerador ->
  pgpass.conf (senha digitada aqui, sem BOM) -> simulacao -> ativa o plugin
  Worklists -> orthanc.json (pasta das imagens, AE Title, senha do admin) ->
  reinicia o Orthanc (pede confirmacao; desfaz se nao subir) -> tarefa agendada.

  NAO instala Orthanc nem PostgreSQL (so confere) e NAO cadastra os aparelhos
  (DicomModalities), que dependem do IP/AET de cada equipamento.
  Para importar arquivos DICOM/ZIP de teste: Importar-Dicom.ps1.
#>
param(
    [string]$DbHost = '10.200.200.7',
    [int]$DbPort = 5432,
    [string]$DbNome = 'hospitalit',
    [string]$DbUsuario = 'orthanc_worklist',
    [string]$OrthancDir = 'C:\Program Files\Orthanc Server',
    [string]$Psql = 'C:\Program Files\PostgreSQL\15\bin\psql.exe'
)
$ErrorActionPreference = 'Stop'

$CfgDir = Join-Path $OrthancDir 'Configuration'
$DadosDir = 'C:\ProgramData\GHBAM'
$WlDir = 'C:\OrthancWorklists'
$Gerador = Join-Path $OrthancDir 'GerarWorklist.ps1'
$PgPass = Join-Path $DadosDir 'pgpass.conf'
$LogWl = Join-Path $DadosDir 'worklist.log'
$Tarefa = 'GHBAM Worklist'
$Carimbo = Get-Date -Format yyyyMMddHHmmss
$Backups = @{}   # arquivo -> backup, para desfazer se o Orthanc nao subir

function Passo($t) { Write-Host "`n== $t" -ForegroundColor Cyan }
function Falha($t) { Write-Host "ERRO: $t" -ForegroundColor Red; exit 1 }
function Backup($arquivo) {
    if ((Test-Path $arquivo) -and -not $Backups.ContainsKey($arquivo)) {
        $b = "$arquivo.bak-$Carimbo"; Copy-Item $arquivo $b; $Backups[$arquivo] = $b
        Write-Host "  backup: $b"
    }
}
function TextoSeguro($seg) {
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seg)
    try { [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

Passo '1/9 Pre-requisitos'
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { Falha 'Abra o PowerShell com "Executar como administrador" (o titulo deve comecar com "Administrador:").' }
if (-not (Test-Path (Join-Path $OrthancDir 'Orthanc.exe'))) { Falha "Orthanc nao encontrado em $OrthancDir. Instale o Orthanc para Windows antes." }
if (-not (Get-ChildItem (Join-Path $OrthancDir 'Plugins') -Filter '*Worklist*.dll' -ErrorAction SilentlyContinue)) { Falha 'Plugin OrthancWorklists.dll nao encontrado na pasta Plugins.' }
if (-not (Get-Service Orthanc -ErrorAction SilentlyContinue)) { Falha 'Servico "Orthanc" nao existe.' }
if (-not (Test-Path $Psql)) { Falha "psql nao encontrado em $Psql. Instale o cliente do PostgreSQL ou use -Psql <caminho>." }
if (-not (Test-Path (Join-Path $PSScriptRoot 'GerarWorklist.ps1'))) { Falha 'GerarWorklist.ps1 precisa estar na mesma pasta deste instalador.' }
Write-Host '  ok'

Passo '2/9 Pastas (acesso so de SYSTEM e Administradores)'
foreach ($d in $DadosDir, $WlDir) {
    New-Item -ItemType Directory $d -Force | Out-Null
    icacls $d /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' | Out-Null
    Write-Host "  $d"
}

Passo '3/9 Copiando o gerador'
Copy-Item (Join-Path $PSScriptRoot 'GerarWorklist.ps1') $Gerador -Force
Write-Host "  $Gerador"

Passo "4/9 Senha do usuario $DbUsuario no banco ($DbHost/$DbNome)"
$manter = $false
if (Test-Path $PgPass) { $manter = (Read-Host 'pgpass.conf ja existe. Manter a senha atual? (S/n)') -ne 'n' }
if (-not $manter) {
    $senha = (TextoSeguro (Read-Host 'Senha' -AsSecureString)) -replace '\\', '\\' -replace ':', '\:'   # escape do formato pgpass
    # ASCII = sem BOM (com BOM o psql nao reconhece a linha)
    Set-Content -Path $PgPass -Value "${DbHost}:${DbPort}:${DbNome}:${DbUsuario}:$senha" -Encoding ASCII -NoNewline
    $senha = $null
}
Write-Host "  $PgPass"

Passo '5/9 Simulacao (consulta a view, nao grava nada)'
& $Gerador -Simular -DbHost $DbHost -DbPort $DbPort -DbNome $DbNome -DbUsuario $DbUsuario -Psql $Psql
if ($LASTEXITCODE -ne 0) { Falha "Simulacao falhou. Veja $LogWl (senha, permissao na view ou acesso ao banco)." }

Passo '6/9 Plugin Worklists'
$wlJson = Join-Path $CfgDir 'worklists.json'
$dup = Get-ChildItem $CfgDir -Filter *.json | Where-Object { $_.Name -ne 'worklists.json' -and (Select-String -Path $_.FullName -Pattern '"Worklists"\s*:' -Quiet) }
if ($dup) { Falha "A secao Worklists tambem esta em: $($dup.Name -join ', '). Deixe so no worklists.json." }
Backup $wlJson
@'
{
    /**
    * Configuration of the ModalityWorklists plugin, that can be used
    * to serve DICOM modality worklists.
    **/
    "Worklists" : {
        "Enable": true,
        "Database": "C:\\OrthancWorklists"
    }
}
'@ | Set-Content -Path $wlJson -Encoding ASCII
Write-Host "  $wlJson (Enable true, Database C:\OrthancWorklists)"

Passo '7/9 orthanc.json (Enter mantem o valor atual)'
$ojPath = Join-Path $CfgDir 'orthanc.json'
if (-not (Test-Path $ojPath)) { Falha "orthanc.json nao encontrado em $CfgDir." }
$texto = Get-Content $ojPath -Raw
$texto = [regex]::Replace($texto, '(?s)/\*.*?\*/', '')          # o Orthanc aceita comentarios; o PowerShell nao
$texto = [regex]::Replace($texto, '(?m)^\s*//.*$', '')
try { $oj = $texto | ConvertFrom-Json } catch { Falha "orthanc.json invalido: $($_.Exception.Message)" }
$mudou = $false

$atual = "$($oj.StorageDirectory)"
Write-Host "  As imagens DICOM ficam hoje em: $atual"
Write-Host '  (recomendado: disco de dados separado do Windows, ex.: D:/OrthancData)'
$nova = (Read-Host '  Nova pasta das imagens').Trim() -replace '\\', '/'
if ($nova -and $nova -ne $atual) {
    Write-Host '  ATENCAO: imagens que ja estejam na pasta atual NAO sao movidas e deixam de aparecer no Orthanc.' -ForegroundColor Yellow
    if ((Read-Host '  Confirmar troca da pasta? (s/N)') -eq 's') {
        New-Item -ItemType Directory ($nova -replace '/', '\') -Force | Out-Null
        $oj.StorageDirectory = $nova; $oj.IndexDirectory = $nova; $mudou = $true
    }
}

$aet = (Read-Host "  AE Title do Orthanc na rede [$($oj.DicomAet)]").Trim().ToUpper()
if ($aet -and $aet -ne $oj.DicomAet) {
    if ($aet -notmatch '^[A-Z0-9_-]{1,16}$') { Falha 'AE Title: ate 16 caracteres, so A-Z, 0-9, _ ou -.' }
    $oj.DicomAet = $aet; $mudou = $true
}

if ((Read-Host '  Trocar a senha do usuario admin do Orthanc? (s/N)') -eq 's') {
    $s1 = TextoSeguro (Read-Host '  Nova senha' -AsSecureString)
    $s2 = TextoSeguro (Read-Host '  Repita' -AsSecureString)
    if (-not $s1 -or $s1 -ne $s2) { Falha 'As senhas nao conferem.' }
    if (-not $oj.RegisteredUsers) { $oj | Add-Member -NotePropertyName RegisteredUsers -NotePropertyValue ([pscustomobject]@{}) -Force }
    $oj.RegisteredUsers | Add-Member -NotePropertyName admin -NotePropertyValue $s1 -Force
    $s1 = $null; $s2 = $null; $mudou = $true
    Write-Host '  Lembrete: atualize a URL do Orthanc na tela /unidades do GHBAM (usuario:senha@...).' -ForegroundColor Yellow
}

if ($mudou) {
    Backup $ojPath
    [IO.File]::WriteAllText($ojPath, ($oj | ConvertTo-Json -Depth 20), (New-Object Text.UTF8Encoding $false))
    Write-Host "  $ojPath atualizado"
} else {
    Write-Host '  sem mudancas'
}

Passo '8/9 Reiniciar o Orthanc (exames sendo enviados agora podem precisar de reenvio)'
if ((Read-Host 'Reiniciar agora? (s/N)') -eq 's') {
    Restart-Service Orthanc
    Start-Sleep -Seconds 10
    $st = (Get-Service Orthanc).Status
    Write-Host "  Orthanc: $st"
    if ($st -ne 'Running') {
        foreach ($k in $Backups.Keys) { Copy-Item $Backups[$k] $k -Force }
        Start-Service Orthanc -ErrorAction SilentlyContinue
        Falha 'Orthanc nao voltou. As configuracoes anteriores foram restauradas; veja a pasta Logs.'
    }
} else {
    Write-Host '  Pulado: reinicie depois para aplicar as mudancas.' -ForegroundColor Yellow
}

Passo "9/9 Tarefa agendada '$Tarefa' (SYSTEM, a cada 1 minuto)"
$arg = "-NoProfile -ExecutionPolicy Bypass -File `"$Gerador`" -DbHost $DbHost -DbPort $DbPort -DbNome $DbNome -DbUsuario $DbUsuario -Psql `"$Psql`""
$acao = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arg
$gatilho = New-ScheduledTaskTrigger -Once -At (Get-Date) -RepetitionInterval (New-TimeSpan -Minutes 1)
$cfg = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 5) -StartWhenAvailable
Register-ScheduledTask -TaskName $Tarefa -Action $acao -Trigger $gatilho -Settings $cfg -User 'SYSTEM' -RunLevel Highest -Force | Out-Null
Start-ScheduledTask -TaskName $Tarefa
Start-Sleep -Seconds 20
$info = Get-ScheduledTaskInfo $Tarefa
Write-Host "  ultima execucao: $($info.LastRunTime) | resultado: $($info.LastTaskResult) (0 = ok)"
Write-Host '  log:'; Get-Content $LogWl -Tail 2 | ForEach-Object { "    $_" }
Write-Host "  arquivos .wl: $(@(Get-ChildItem $WlDir -Filter 'ghbam_*.wl').Count)"

Write-Host "`nPronto." -ForegroundColor Green
Write-Host 'Falta a mao: cadastrar os aparelhos em DicomModalities (orthanc.json) e apontar a worklist/envio'
Write-Host 'de cada aparelho para este servidor. Para importar DICOM/ZIP de teste: Importar-Dicom.ps1.'
