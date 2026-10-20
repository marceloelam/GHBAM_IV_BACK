$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
if (-not (Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
    throw "JDK 21 nao encontrado em $env:JAVA_HOME"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Push-Location $PSScriptRoot
try {
    & mvn -version
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & mvn clean quarkus:dev @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}