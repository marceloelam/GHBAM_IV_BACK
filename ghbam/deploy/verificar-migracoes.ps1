param(
    [switch]$Strict,
    [string]$MigrationDir
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($MigrationDir)) {
    $MigrationDir = Join-Path $PSScriptRoot '..\src\main\resources\db\migration'
}
$resolvedMigrationDir = (Resolve-Path -LiteralPath $MigrationDir).Path
$files = Get-ChildItem -LiteralPath $resolvedMigrationDir -File -Filter '*.sql'
$versioned = @()
$unversioned = @()

foreach ($file in $files) {
    if ($file.Name -match '^V(\d+)__.+\.sql$') {
        $versioned += [pscustomobject]@{
            Version = [int]$Matches[1]
            Name = $file.Name
        }
    } else {
        $unversioned += $file.Name
    }
}

$duplicates = $versioned |
    Group-Object Version |
    Where-Object Count -gt 1 |
    Sort-Object { [int]$_.Name }

Write-Host "Diretorio: $resolvedMigrationDir"
Write-Host "Migracoes SQL: $($files.Count)"
Write-Host "Versionadas: $($versioned.Count)"
Write-Host "Sem versao: $($unversioned.Count)"
Write-Host "Versoes duplicadas: $($duplicates.Count)"

if ($duplicates.Count -gt 0) {
    Write-Warning 'Versoes duplicadas encontradas:'
    foreach ($duplicate in $duplicates) {
        Write-Host "  V$($duplicate.Name): $($duplicate.Group.Name -join ', ')"
    }
}

if ($unversioned.Count -gt 0) {
    Write-Warning 'Arquivos sem o padrao V<numero>__<descricao>.sql:'
    foreach ($name in $unversioned) {
        Write-Host "  $name"
    }
}

if ($Strict -and ($duplicates.Count -gt 0 -or $unversioned.Count -gt 0)) {
    Write-Error 'Catalogo de migracoes ainda nao esta pronto para ativacao automatica.'
}

if ($duplicates.Count -eq 0 -and $unversioned.Count -eq 0) {
    Write-Host 'Catalogo sem conflitos estruturais.'
}
