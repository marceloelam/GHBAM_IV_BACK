#!/usr/bin/env bash
# Instala OU atualiza o servico de IA de imagem do GHBAM (Ubuntu, sem docker).
# Um comando, rodado de dentro desta pasta (monai-service):
#
#   sudo ./instalar.sh --xrv --nodulo --ich
#
# Opcoes:
#   --xrv      raio-X de torax (TorchXRayVision)
#   --nodulo   TC de torax, nodulo pulmonar (MONAI lung_nodule_ct_detection)
#   --ich      TC de cranio, hemorragia (ianpan/ct-head-hemorrhage-detection)
#   --host H   endereco de escuta (padrao 127.0.0.1; use o IP da rede interna
#              se o backend Quarkus estiver em OUTRO servidor)
#   --porta P  porta (padrao 8000)
#
# Rodar de novo = atualizar: copia o app.py novo, mantem venv e pesos ja
# baixados (confere o hash) e reinicia. Modelo nao listado fica DESLIGADO.
# Todos sao modelos de PESQUISA, sem validacao clinica nem registro ANVISA.
set -euo pipefail

DESTINO=/opt/ghbam-monai
MODELOS=$DESTINO/modelos
USUARIO=ghbam
HOST=127.0.0.1
PORTA=8000
XRV=false; NODULO=false; ICH=false

# Versoes FIXAS (commit) dos pesos e do codigo dos modelos.
HF_ICH=https://huggingface.co/ianpan/ct-head-hemorrhage-detection/resolve/e6d89c253fdcc2a597dd0fd0f5771821f4947b37
GIT_ICH="git+https://github.com/i-pan/ct-head-hemorrhage-detection.git@914c57d"
HF_NODULO=https://huggingface.co/MONAI/lung_nodule_ct_detection/resolve/9d6622fda0e52be0a4155fed2e0afb9c17bcd80b
SHA_NODULO=b5e79231466adae93a6fe8e8594029e9add142914e223b879aa0343bb2402d01

while [ $# -gt 0 ]; do
  case "$1" in
    --xrv) XRV=true ;;
    --nodulo) NODULO=true ;;
    --ich) ICH=true ;;
    --host) HOST="$2"; shift ;;
    --porta) PORTA="$2"; shift ;;
    *) echo "Opcao desconhecida: $1"; exit 1 ;;
  esac
  shift
done

[ "$(id -u)" -eq 0 ] || { echo "Rode com sudo."; exit 1; }
ORIGEM="$(cd "$(dirname "$0")" && pwd)"
[ -f "$ORIGEM/app.py" ] || { echo "Rode de dentro da pasta monai-service."; exit 1; }

passo() { echo; echo "==> $*"; }

# Baixa um arquivo e confere o SHA-256; nao baixa de novo se ja estiver certo.
baixar() {  # url destino sha256
  local url="$1" arq="$2" sha="$3"
  mkdir -p "$(dirname "$arq")"
  if [ -f "$arq" ] && echo "$sha  $arq" | sha256sum -c --quiet - >/dev/null 2>&1; then
    echo "   ok (ja baixado): $arq"; return
  fi
  curl -fL --retry 3 -o "$arq.parcial" "$url"
  echo "$sha  $arq.parcial" | sha256sum -c --quiet - || { echo "HASH DIVERGENTE: $url"; rm -f "$arq.parcial"; exit 1; }
  mv "$arq.parcial" "$arq"
  echo "   ok: $arq"
}

passo "Pacotes do sistema"
apt-get update -qq
apt-get install -y -qq python3 python3-venv python3-pip git curl >/dev/null

passo "Usuario e pasta"
id "$USUARIO" >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin -d "$DESTINO" "$USUARIO"
mkdir -p "$DESTINO" "$MODELOS"
install -m 0644 "$ORIGEM/app.py" "$ORIGEM/requirements.txt" "$DESTINO/"

passo "Ambiente Python"
[ -x "$DESTINO/.venv/bin/python" ] || python3 -m venv "$DESTINO/.venv"
PY="$DESTINO/.venv/bin/python"
PIP="$DESTINO/.venv/bin/pip"
"$PIP" install -q --upgrade pip
"$PIP" install -q -r "$DESTINO/requirements.txt"
if $XRV || $NODULO || $ICH; then
  # CPU (o 119 nao tem GPU): torch CPU e bem menor que a versao CUDA.
  "$PIP" install -q --index-url https://download.pytorch.org/whl/cpu torch torchvision
  "$PIP" install -q pylibjpeg pylibjpeg-libjpeg   # DICOM com compressao JPEG
fi
chown -R "$USUARIO" "$DESTINO"

if $XRV; then
  passo "Raio-X de torax (TorchXRayVision)"
  "$PIP" install -q torchxrayvision scikit-image
  # Pre-baixa os pesos para a pasta do servico (HOME do usuario ghbam).
  sudo -u "$USUARIO" HOME="$DESTINO" "$PY" -c \
    "import torchxrayvision as x; x.models.DenseNet(weights='densenet121-res224-all')" >/dev/null
  echo "   ok"
fi

if $NODULO; then
  passo "TC de torax: nodulo pulmonar (MONAI)"
  "$PIP" install -q monai
  baixar "$HF_NODULO/models/model.pt" "$MODELOS/nodulo/models/model.pt" "$SHA_NODULO"
fi

if $ICH; then
  passo "TC de cranio: hemorragia intracraniana"
  "$PIP" install -q timm safetensors opencv-python-headless einops nibabel huggingface_hub "$GIT_ICH"
  # Hashes do checksums.json publicado pelo autor (no mesmo commit fixo).
  CHECKSUMS="$(curl -fsL "$HF_ICH/checksums.json")"
  for f in inference.py classifier/model.safetensors sequence/model.safetensors \
           segmentation/fold0.safetensors segmentation/fold1.safetensors segmentation/fold2.safetensors \
           segmentation/fold3.safetensors segmentation/fold4.safetensors; do
    sha="$(printf '%s' "$CHECKSUMS" | "$PY" -c "import json,sys;print([e['sha256'] for e in json.load(sys.stdin) if e['path']=='$f'][0])")"
    baixar "$HF_ICH/$f" "$MODELOS/ich/$f" "$sha"
  done
fi

chown -R "$USUARIO" "$DESTINO"

passo "Servico systemd"
cat > /etc/systemd/system/ghbam-monai.service <<EOF
# Gerado por instalar.sh em $(date '+%Y-%m-%d %H:%M'). Rode o instalador de novo para mudar.
[Unit]
Description=GHBAM - servico de IA de imagem
After=network.target

[Service]
Type=simple
User=$USUARIO
WorkingDirectory=$DESTINO
Environment=HOME=$DESTINO
Environment=PYTHONUTF8=1
Environment=XRV_HABILITADO=$XRV
Environment=NODULO_HABILITADO=$NODULO
Environment=NODULO_MODELO_DIR=$MODELOS/nodulo
Environment=ICH_HABILITADO=$ICH
Environment=ICH_MODELO_DIR=$MODELOS/ich
ExecStart=$DESTINO/.venv/bin/uvicorn app:app --host $HOST --port $PORTA --log-level warning
Restart=on-failure
RestartSec=5
# A IA nunca pode derrubar o servidor: limita memoria e baixa a prioridade de CPU.
MemoryMax=12G
Nice=10

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable ghbam-monai >/dev/null 2>&1
systemctl restart ghbam-monai

passo "Conferindo"
for _ in $(seq 1 30); do
  if curl -fs "http://$HOST:$PORTA/health" >/dev/null 2>&1; then
    curl -s "http://$HOST:$PORTA/health"; echo; echo
    echo "Pronto. No GHBAM, tela /unidades: IA de Imagem = Sim, URL do servico = http://$HOST:$PORTA,"
    echo "salve e clique em 'Testar conexao'."
    exit 0
  fi
  sleep 2
done
echo "O servico nao respondeu. Veja: journalctl -u ghbam-monai -n 50"
exit 1
