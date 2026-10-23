<#
.SYNOPSIS
  Importa arquivos DICOM (e .zip com DICOM dentro) de uma pasta para o Orthanc.

.DESCRIPTION
  Uso para testes ou cargas manuais. No dia a dia os aparelhos enviam sozinhos pela rede.
  NUNCA copie arquivos para a pasta de armazenamento do Orthanc (ex.: C:\Orthanc): ele so
  enxerga o que recebe pela rede/API e registra no indice. Este script faz o envio certo,
  igual ao botao Upload da tela web. Pede usuario/senha do Orthanc (nao ficam salvos).

.EXAMPLE
  .\Importar-Dicom.ps1 -Pasta C:\DicomTeste
  .\Importar-Dicom.ps1 -Pasta D:\Exames -Url http://10.200.200.78:8042
#>
param(
    [Parameter(Mandatory = $true)][string]$Pasta,
    [string]$Url = 'http://localhost:8042'
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Pasta)) { Write-Host "Pasta nao encontrada: $Pasta" -ForegroundColor Red; exit 1 }

$cred = Get-Credential -Message 'Usuario e senha do Orthanc'
$arquivos = Get-ChildItem -LiteralPath $Pasta -File -Recurse
if (-not $arquivos) { Write-Host 'Nenhum arquivo na pasta.'; exit 0 }

$total = @{ Success = 0; AlreadyStored = 0; Falha = 0 }
foreach ($f in $arquivos) {
    $tipo = if ($f.Extension -ieq '.zip') { 'application/zip' } else { 'application/dicom' }
    try {
        $r = @(Invoke-RestMethod -Uri "$($Url.TrimEnd('/'))/instances" -Method Post -InFile $f.FullName `
                -ContentType $tipo -Credential $cred -TimeoutSec 1800)
        $porStatus = $r | Group-Object Status
        foreach ($g in $porStatus) { if ($total.ContainsKey($g.Name)) { $total[$g.Name] += $g.Count } }
        Write-Host ("  {0}: {1}" -f $f.Name, (($porStatus | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ' '))
    } catch {
        $total.Falha++
        $msg = $_.Exception.Message
        if ($msg -match '401') { Write-Host 'Usuario/senha do Orthanc recusados.' -ForegroundColor Red; exit 1 }
        Write-Host "  $($f.Name): nao importado (nao e DICOM/ZIP valido?) - $msg" -ForegroundColor Yellow
    }
}
Write-Host ("`nImportadas: {0} | ja existiam: {1} | arquivos com falha: {2}" -f $total.Success, $total.AlreadyStored, $total.Falha) -ForegroundColor Green
Write-Host "Confira em $($Url.TrimEnd('/'))/ui/app/"
