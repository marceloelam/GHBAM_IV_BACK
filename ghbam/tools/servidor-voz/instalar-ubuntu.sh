#!/usr/bin/env bash
# Instala o serviço de voz do painel de chamada do GHBAM no Ubuntu (systemd).
#
# Uso (na pasta deste script, no servidor):
#   sudo bash instalar-ubuntu.sh [usuario-do-servico]      # padrão: ubuntu
#
# Pode rodar de novo com segurança: só baixa o que falta e reinicia o serviço.
set -euo pipefail

USUARIO="${1:-ubuntu}"
BASE=/opt/tts
VERSAO=1.13.8
RELEASE="https://github.com/k2-fsa/sherpa-onnx/releases/download"
MODELO=kokoro-multi-lang-v1_0
PORTA=8099
AQUI="$(cd "$(dirname "$0")" && pwd)"

[ "$(id -u)" -eq 0 ] || { echo "Rode com sudo."; exit 1; }
id "$USUARIO" >/dev/null 2>&1 || { echo "Usuário '$USUARIO' não existe."; exit 1; }
[ -f "$AQUI/ServidorVoz.java" ] || { echo "ServidorVoz.java não encontrado em $AQUI."; exit 1; }

JAVA="$(command -v java || true)"
[ -n "$JAVA" ] || { echo "Java não encontrado. Instale o Java 21+ (o mesmo do backend)."; exit 1; }
JAVA_MAIOR="$("$JAVA" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}' | cut -d. -f1)"
[ "${JAVA_MAIOR:-0}" -ge 21 ] || { echo "Java $JAVA_MAIOR encontrado; é preciso 21+."; exit 1; }

mkdir -p "$BASE/servico"

if [ ! -f "$BASE/$MODELO/model.onnx" ]; then
  echo "Baixando o modelo de voz (~330 MB)..."
  curl -fSL -o /tmp/$MODELO.tar.bz2 "$RELEASE/tts-models/$MODELO.tar.bz2"
  tar -xjf /tmp/$MODELO.tar.bz2 -C "$BASE"
  rm -f /tmp/$MODELO.tar.bz2   # não deixa o compactado ocupando disco
fi

# Piper: voz "Faber" (mais rápida). O backend a procura em $BASE/piper e $BASE/piper-vozes.
if [ ! -x "$BASE/piper/piper" ]; then
  echo "Baixando o Piper..."
  curl -fSL -o /tmp/piper.tgz "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_x86_64.tar.gz"
  tar -xzf /tmp/piper.tgz -C "$BASE"
  rm -f /tmp/piper.tgz
fi
mkdir -p "$BASE/piper-vozes"
for f in pt_BR-faber-medium.onnx pt_BR-faber-medium.onnx.json; do
  [ -f "$BASE/piper-vozes/$f" ] || curl -fSL -o "$BASE/piper-vozes/$f" \
    "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/pt/pt_BR/faber/medium/$f"
done

for jar in sherpa-onnx-jvm-$VERSAO.jar sherpa-onnx-native-lib-linux-x64-$VERSAO.jar; do
  [ -f "$BASE/servico/$jar" ] || curl -fSL -o "$BASE/servico/$jar" "$RELEASE/v$VERSAO/$jar"
done

install -m 0644 "$AQUI/ServidorVoz.java" "$BASE/servico/ServidorVoz.java"
chown -R "$USUARIO":"$USUARIO" "$BASE"

cat > /etc/systemd/system/ghbam-voz.service <<EOF
[Unit]
Description=GHBAM - servico de voz do painel de chamada
After=network.target

[Service]
User=$USUARIO
WorkingDirectory=$BASE/servico
Environment=VOZ_MODELO_DIR=$BASE/$MODELO
Environment=VOZ_PORTA=$PORTA
ExecStart=$JAVA --enable-native-access=ALL-UNNAMED -cp sherpa-onnx-jvm-$VERSAO.jar:sherpa-onnx-native-lib-linux-x64-$VERSAO.jar ServidorVoz.java
Restart=always
RestartSec=5
# O serviço só escuta em 127.0.0.1 e não grava nada em disco.
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable ghbam-voz >/dev/null
systemctl restart ghbam-voz

echo -n "Aguardando o serviço carregar o modelo"
for _ in $(seq 1 60); do
  if curl -fs "http://127.0.0.1:$PORTA/saude" >/dev/null 2>&1; then
    echo " ok."
    echo
    echo "Serviço de voz instalado e ativo (systemctl status ghbam-voz)."
    echo "No backend do GHBAM, defina e reinicie:"
    echo "  GHBAM_PAINEL_VOZ_HABILITADO=true"
    echo "  KOKORO_SERVICO_URL=http://127.0.0.1:$PORTA"
    exit 0
  fi
  echo -n "."; sleep 2
done
echo
echo "O serviço não respondeu. Veja: journalctl -u ghbam-voz -n 50"
exit 1
