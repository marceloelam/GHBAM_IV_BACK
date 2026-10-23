#!/bin/bash
set -euo pipefail

# Resolve a pasta do proprio script, mesmo quando ele for chamado de outro
# diretorio ou por um servico.
APP_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$APP_DIR"

# O backend le diretamente config/ghbam-secrets.properties. O arquivo
# permanece fora do artefato, fora do Git e com permissao 0600.
if [[ ! -f "$APP_DIR/config/ghbam-secrets.properties" ]]; then
    echo "Configuracao ausente: $APP_DIR/config/ghbam-secrets.properties" >&2
    exit 1
fi

if [[ ! -f "$APP_DIR/ghbam-4.0.0-runner.jar" ]]; then
    echo "Backend ausente: $APP_DIR/ghbam-4.0.0-runner.jar" >&2
    exit 1
fi

# Sem limite explicito a JVM usa so 25% da RAM; JAVA_OPTS sobrescreve.
JAVA_OPTS="${JAVA_OPTS:--XX:MaxRAMPercentage=70 -XX:+UseG1GC}"

# O Nginx encerra o TLS; o backend aceita HTTP somente na rede interna.
# shellcheck disable=SC2086
exec java $JAVA_OPTS -Duser.timezone=America/Sao_Paulo \
    -Dquarkus.http.insecure-requests=ENABLED \
    -Dghbam.secrets.file="$APP_DIR/config/ghbam-secrets.properties" \
    -jar "$APP_DIR/ghbam-4.0.0-runner.jar"
