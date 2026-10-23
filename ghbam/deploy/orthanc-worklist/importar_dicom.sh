#!/usr/bin/env bash
# Importa arquivos DICOM (e .zip com DICOM dentro) de uma pasta para o Orthanc (Linux).
# Equivalente ao Importar-Dicom.ps1. Uso para testes/cargas manuais; no dia a dia os
# aparelhos enviam sozinhos pela rede.
# NUNCA copie arquivos para a pasta de armazenamento do Orthanc: ele so enxerga o que
# recebe pela rede/API. Este script faz o envio certo (igual ao Upload da tela web).
#
# Uso:  bash importar_dicom.sh /caminho/da/pasta [http://localhost:8042]
set -uo pipefail

PASTA="${1:-}"
URL="${2:-http://localhost:8042}"; URL="${URL%/}"
[ -n "$PASTA" ] && [ -d "$PASTA" ] || { echo "Uso: bash importar_dicom.sh /caminho/da/pasta [url]"; exit 1; }
command -v curl >/dev/null || { echo "curl nao encontrado: apt install curl"; exit 1; }

read -rp "Usuario do Orthanc: " USU
read -rsp "Senha: " SEN; echo
NETRC="$(mktemp)"; chmod 600 "$NETRC"; trap 'rm -f "$NETRC"' EXIT
host="${URL#*://}"; host="${host%%[:/]*}"
printf 'machine %s login %s password %s\n' "$host" "$USU" "$SEN" > "$NETRC"   # senha fora da linha de comando
unset SEN

ok=0; ja=0; falha=0
while IFS= read -r -d '' f; do
  case "${f,,}" in *.zip) tipo=application/zip ;; *) tipo=application/dicom ;; esac
  resp="$(curl -s -w '\n%{http_code}' --netrc-file "$NETRC" -X POST -H "Content-Type: $tipo" \
          --data-binary @"$f" --max-time 1800 "$URL/instances")"
  code="${resp##*$'\n'}"; corpo="${resp%$'\n'*}"
  if [ "$code" = "401" ]; then echo "Usuario/senha do Orthanc recusados."; exit 1; fi
  if [ "$code" = "200" ]; then
    s=$(grep -o '"Success"' <<<"$corpo" | wc -l); a=$(grep -o '"AlreadyStored"' <<<"$corpo" | wc -l)
    ok=$((ok + s)); ja=$((ja + a)); echo "  $(basename "$f"): Success=$s AlreadyStored=$a"
  else
    falha=$((falha + 1)); echo "  $(basename "$f"): nao importado (HTTP $code; nao e DICOM/ZIP valido?)"
  fi
done < <(find "$PASTA" -type f -print0)

printf '\nImportadas: %s | ja existiam: %s | arquivos com falha: %s\n' "$ok" "$ja" "$falha"
echo "Confira em $URL/ui/app/"
