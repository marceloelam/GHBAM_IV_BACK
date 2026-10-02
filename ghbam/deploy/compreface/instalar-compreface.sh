#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${1:-/root/ghbam4}"
COMPFACE_DIR="$APP_DIR/compreface"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker nao encontrado. Instale Docker antes de continuar." >&2
    exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose plugin nao encontrado. Instale 'docker compose' antes de continuar." >&2
    exit 1
fi

mkdir -p "$COMPFACE_DIR"

cp "$SCRIPT_DIR/docker-compose.yml" "$COMPFACE_DIR/docker-compose.yml"
cp "$SCRIPT_DIR/README.md" "$COMPFACE_DIR/README.md"

if [[ ! -f "$COMPFACE_DIR/.env" ]]; then
    cp "$SCRIPT_DIR/.env.example" "$COMPFACE_DIR/.env"
    chmod 600 "$COMPFACE_DIR/.env"
    echo "Arquivo criado: $COMPFACE_DIR/.env"
    echo "Edite as senhas em $COMPFACE_DIR/.env antes de usar em producao."
fi

cd "$COMPFACE_DIR"
docker compose up -d

cat <<EOF

CompreFace iniciado em:
  http://127.0.0.1:8000

Proximo passo:
1. Acesse o painel do CompreFace.
2. Crie um servico Face Verification.
3. Copie a API key.
4. Adicione em $APP_DIR/config/ghbam-secrets.properties:

GHBAM_FACE_VERIFICATION_URL=http://127.0.0.1:8000/api/v1/verification/verify
GHBAM_FACE_API_KEY=COLE_A_API_KEY_DO_FACE_VERIFICATION
GHBAM_FACE_SIMILARITY_THRESHOLD=0.85
GHBAM_FACE_TIMEOUT_SECONDS=20

5. Reinicie o backend GHBAM.

EOF
