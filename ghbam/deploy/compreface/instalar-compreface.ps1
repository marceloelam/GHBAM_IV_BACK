param(
    [string]$Destino = "C:\ghbam4"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker nao encontrado. Instale/abra o Docker Desktop antes de continuar."
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$comprefaceDir = Join-Path $Destino "compreface"

New-Item -ItemType Directory -Path $comprefaceDir -Force | Out-Null

Copy-Item -Path (Join-Path $scriptDir "docker-compose.yml") -Destination (Join-Path $comprefaceDir "docker-compose.yml") -Force
Copy-Item -Path (Join-Path $scriptDir "README.md") -Destination (Join-Path $comprefaceDir "README.md") -Force

$envDestino = Join-Path $comprefaceDir ".env"
if (-not (Test-Path $envDestino)) {
    Copy-Item -Path (Join-Path $scriptDir ".env.example") -Destination $envDestino
    Write-Host "Arquivo criado: $envDestino"
    Write-Host "Edite as senhas nesse arquivo antes de usar em producao."
}

Push-Location $comprefaceDir
try {
    docker compose up -d
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "CompreFace iniciado em: http://127.0.0.1:8000"
Write-Host ""
Write-Host "Depois crie um servico Face Verification e adicione no ghbam-secrets.properties:"
Write-Host "GHBAM_FACE_VERIFICATION_URL=http://127.0.0.1:8000/api/v1/verification/verify"
Write-Host "GHBAM_FACE_API_KEY=COLE_A_API_KEY_DO_FACE_VERIFICATION"
Write-Host "GHBAM_FACE_SIMILARITY_THRESHOLD=0.85"
Write-Host "GHBAM_FACE_TIMEOUT_SECONDS=20"
