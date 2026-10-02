#!/bin/bash
set -euo pipefail

# Atualiza somente o artefato do backend. O diretorio config/ nunca e tocado:
# ele guarda os segredos estaveis, inclusive a identidade VAPID dos aparelhos
# que ja estao pareados.

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# Aceita tanto o script copiado para a pasta da aplicacao quanto executado da
# pasta deploy/ do repositorio.
if [[ -d "$SCRIPT_DIR/config" || -f "$SCRIPT_DIR/ghbam-4.0.0-runner.jar" ]]; then
    APP_DIR="$SCRIPT_DIR"
else
    APP_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
fi
NOVO_JAR="${1:-}"
JAR_DESTINO="$APP_DIR/ghbam-4.0.0-runner.jar"
SECRETS_FILE="$APP_DIR/config/ghbam-secrets.properties"

if [[ -z "$NOVO_JAR" || ! -f "$NOVO_JAR" ]]; then
    echo "Uso: $0 /caminho/para/ghbam-4.0.0-runner.jar" >&2
    exit 64
fi

if [[ ! -f "$SECRETS_FILE" ]]; then
    echo "Atualizacao cancelada: arquivo de segredos ausente em $SECRETS_FILE" >&2
    exit 1
fi

for chave in VAPID_PUBLIC_KEY VAPID_PRIVATE_KEY VAPID_SUBJECT; do
    if ! grep -qE "^${chave}=.+" "$SECRETS_FILE"; then
        echo "Atualizacao cancelada: $chave nao esta configurada em $SECRETS_FILE" >&2
        exit 1
    fi
done

TMP_JAR="$JAR_DESTINO.novo"
cp "$NOVO_JAR" "$TMP_JAR"

if [[ -f "$JAR_DESTINO" ]]; then
    cp -p "$JAR_DESTINO" "$JAR_DESTINO.anterior"
fi

mv -f "$TMP_JAR" "$JAR_DESTINO"
echo "JAR atualizado. O arquivo de segredos e as chaves VAPID existentes foram preservados."
