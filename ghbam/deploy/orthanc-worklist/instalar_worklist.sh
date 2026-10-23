#!/usr/bin/env bash
# Instala/reinstala a worklist do GHBAM num servidor Linux (Debian/Ubuntu) com Orthanc.
# Equivalente ao Instalar-Worklist.ps1 (Windows). Rodar como root, na pasta onde estao
# este arquivo e o gerar_worklist.py:   sudo bash instalar_worklist.sh
# Pode ser rodado de novo (idempotente).
#
# Faz: confere pre-requisitos -> pastas -> copia o gerador -> pgpass.conf (senha digitada
# aqui) -> simulacao -> ativa o plugin Worklists -> orthanc.json (pasta das imagens, AE Title,
# senha do admin) -> reinicia o Orthanc (pede confirmacao; desfaz se nao subir) -> timer do
# systemd a cada 1 minuto, rodando como o usuario "orthanc".
# NAO instala o Orthanc (so confere) e NAO cadastra os aparelhos (DicomModalities).
# Para importar DICOM/ZIP de teste: importar_dicom.sh.
#
# Parametros por variavel de ambiente, ex.:  sudo DB_HOST=10.200.200.143 bash instalar_worklist.sh
set -euo pipefail

DB_HOST="${DB_HOST:-10.200.200.7}"
DB_PORT="${DB_PORT:-5432}"
DB_NOME="${DB_NOME:-hospitalit}"
DB_USUARIO="${DB_USUARIO:-orthanc_worklist}"
CFG_DIR="${CFG_DIR:-/etc/orthanc}"
WL_DIR="${WL_DIR:-/var/lib/orthanc/worklists}"
USUARIO_ORTHANC="${USUARIO_ORTHANC:-orthanc}"

APP_DIR=/opt/ghbam
DADOS_DIR=/etc/ghbam
LOG_DIR=/var/log/ghbam
GERADOR="$APP_DIR/gerar_worklist.py"
PGPASS="$DADOS_DIR/pgpass.conf"
LOG_WL="$LOG_DIR/worklist.log"
ORIGEM="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CARIMBO="$(date +%Y%m%d%H%M%S)"
BACKUPS=()   # "arquivo|backup", para desfazer se o Orthanc nao subir

passo() { printf '\n\033[36m== %s\033[0m\n' "$1"; }
falha() { printf '\033[31mERRO: %s\033[0m\n' "$1"; exit 1; }
aviso() { printf '\033[33m%s\033[0m\n' "$1"; }
backup() {
  local f="$1"
  for b in "${BACKUPS[@]:-}"; do [ "${b%%|*}" = "$f" ] && return 0; done
  if [ -f "$f" ]; then cp -p "$f" "$f.bak-$CARIMBO"; BACKUPS+=("$f|$f.bak-$CARIMBO"); echo "  backup: $f.bak-$CARIMBO"; fi
}

passo "1/9 Pre-requisitos"
[ "$(id -u)" -eq 0 ] || falha "Rode como root: sudo bash instalar_worklist.sh"
systemctl cat orthanc >/dev/null 2>&1 || falha "Servico 'orthanc' nao encontrado. Instale antes: apt install orthanc"
id "$USUARIO_ORTHANC" >/dev/null 2>&1 || falha "Usuario '$USUARIO_ORTHANC' nao existe (vem com o pacote orthanc)."
find /usr/share/orthanc /usr/lib/orthanc /usr/local/share/orthanc -iname '*worklist*' 2>/dev/null | grep -q . \
  || falha "Plugin de worklist nao encontrado (procure libModalityWorklists.so / libOrthancWorklists.so)."
command -v python3 >/dev/null || falha "python3 nao encontrado: apt install python3"
if ! command -v psql >/dev/null; then
  read -rp "psql nao encontrado. Instalar postgresql-client agora? (s/N) " r
  [ "$r" = "s" ] && apt-get install -y postgresql-client || falha "Instale o psql: apt install postgresql-client"
fi
[ -f "$ORIGEM/gerar_worklist.py" ] || falha "gerar_worklist.py precisa estar na mesma pasta deste instalador."
[ -f "$CFG_DIR/orthanc.json" ] || falha "orthanc.json nao encontrado em $CFG_DIR."
echo "  ok"

passo "2/9 Pastas"
install -d -m 750 -o "$USUARIO_ORTHANC" -g "$USUARIO_ORTHANC" "$WL_DIR" "$LOG_DIR"
install -d -m 750 -o root -g "$USUARIO_ORTHANC" "$DADOS_DIR"
install -d -m 755 -o root -g root "$APP_DIR"
echo "  $WL_DIR  $LOG_DIR  $DADOS_DIR  $APP_DIR"

passo "3/9 Copiando o gerador"
install -m 755 -o root -g root "$ORIGEM/gerar_worklist.py" "$GERADOR"
echo "  $GERADOR"

passo "4/9 Senha do usuario $DB_USUARIO no banco ($DB_HOST/$DB_NOME)"
manter=n
if [ -f "$PGPASS" ]; then read -rp "pgpass.conf ja existe. Manter a senha atual? (S/n) " r; [ "$r" = "n" ] || manter=s; fi
if [ "$manter" != "s" ]; then
  read -rsp "Senha: " senha; echo
  senha="${senha//\\/\\\\}"; senha="${senha//:/\\:}"   # escape do formato pgpass
  ( umask 077; printf '%s:%s:%s:%s:%s' "$DB_HOST" "$DB_PORT" "$DB_NOME" "$DB_USUARIO" "$senha" > "$PGPASS" )
  unset senha
fi
chown "$USUARIO_ORTHANC:$USUARIO_ORTHANC" "$PGPASS"; chmod 600 "$PGPASS"
echo "  $PGPASS"

ARGS=(--pasta "$WL_DIR" --db-host "$DB_HOST" --db-port "$DB_PORT" --db-nome "$DB_NOME"
      --db-usuario "$DB_USUARIO" --pgpass "$PGPASS" --log "$LOG_WL")

passo "5/9 Simulacao (consulta a view, nao grava nada)"
runuser -u "$USUARIO_ORTHANC" -- python3 "$GERADOR" "${ARGS[@]}" --simular \
  || falha "Simulacao falhou. Veja $LOG_WL (senha, permissao na view, pg_hba ou rede)."

passo "6/9 Plugin Worklists"
WL_JSON="$CFG_DIR/worklists.json"
dup=$(grep -l '"Worklists"[[:space:]]*:' "$CFG_DIR"/*.json 2>/dev/null | grep -v "/worklists.json$" || true)
[ -z "$dup" ] || falha "A secao Worklists tambem esta em: $dup. Deixe so no worklists.json."
backup "$WL_JSON"
cat > "$WL_JSON" <<EOF
{
    /**
    * Configuration of the ModalityWorklists plugin, that can be used
    * to serve DICOM modality worklists.
    **/
    "Worklists" : {
        "Enable": true,
        "Database": "$WL_DIR"
    }
}
EOF
chmod 644 "$WL_JSON"
echo "  $WL_JSON (Enable true, Database $WL_DIR)"

passo "7/9 orthanc.json (Enter mantem o valor atual)"
OJ="$CFG_DIR/orthanc.json"
# O Orthanc aceita comentarios no JSON; o Python nao: le sem comentarios.
ler_oj() { python3 - "$OJ" "$1" <<'PY'
import json, re, sys
t = open(sys.argv[1], encoding="utf-8").read()
t = re.sub(r"/\*.*?\*/", "", t, flags=re.S); t = re.sub(r"(?m)^\s*//.*$", "", t)
print(json.loads(t).get(sys.argv[2], ""))
PY
}
ATUAL_STORAGE="$(ler_oj StorageDirectory)" || falha "orthanc.json invalido."
ATUAL_AET="$(ler_oj DicomAet)"
NOVO_STORAGE=""; NOVO_AET=""; NOVA_SENHA=""
echo "  As imagens DICOM ficam hoje em: $ATUAL_STORAGE"
echo "  (recomendado: disco/particao de dados, ex.: /srv/orthanc)"
read -rp "  Nova pasta das imagens: " p
if [ -n "$p" ] && [ "$p" != "$ATUAL_STORAGE" ]; then
  aviso "  ATENCAO: imagens que ja estejam na pasta atual NAO sao movidas e deixam de aparecer no Orthanc."
  read -rp "  Confirmar troca da pasta? (s/N) " r
  if [ "$r" = "s" ]; then
    install -d -m 750 -o "$USUARIO_ORTHANC" -g "$USUARIO_ORTHANC" "$p"; NOVO_STORAGE="$p"
  fi
fi
read -rp "  AE Title do Orthanc na rede [$ATUAL_AET]: " a
a="$(echo "$a" | tr '[:lower:]' '[:upper:]' | xargs)"
if [ -n "$a" ] && [ "$a" != "$ATUAL_AET" ]; then
  [[ "$a" =~ ^[A-Z0-9_-]{1,16}$ ]] || falha "AE Title: ate 16 caracteres, so A-Z, 0-9, _ ou -."
  NOVO_AET="$a"
fi
read -rp "  Trocar a senha do usuario admin do Orthanc? (s/N) " r
if [ "$r" = "s" ]; then
  read -rsp "  Nova senha: " s1; echo; read -rsp "  Repita: " s2; echo
  { [ -n "$s1" ] && [ "$s1" = "$s2" ]; } || falha "As senhas nao conferem."
  NOVA_SENHA="$s1"; unset s1 s2
  aviso "  Lembrete: atualize a URL do Orthanc na tela /unidades do GHBAM (usuario:senha@...)."
fi
if [ -n "$NOVO_STORAGE$NOVO_AET$NOVA_SENHA" ]; then
  backup "$OJ"
  NOVO_STORAGE="$NOVO_STORAGE" NOVO_AET="$NOVO_AET" NOVA_SENHA="$NOVA_SENHA" python3 - "$OJ" <<'PY'
import json, os, re, sys
p = sys.argv[1]
t = open(p, encoding="utf-8").read()
t = re.sub(r"/\*.*?\*/", "", t, flags=re.S); t = re.sub(r"(?m)^\s*//.*$", "", t)
c = json.loads(t)
if os.environ["NOVO_STORAGE"]:
    c["StorageDirectory"] = c["IndexDirectory"] = os.environ["NOVO_STORAGE"]
if os.environ["NOVO_AET"]:
    c["DicomAet"] = os.environ["NOVO_AET"]
if os.environ["NOVA_SENHA"]:
    c.setdefault("RegisteredUsers", {})["admin"] = os.environ["NOVA_SENHA"]
with open(p, "w", encoding="utf-8") as f:
    json.dump(c, f, indent=2, ensure_ascii=False)
PY
  unset NOVA_SENHA
  echo "  $OJ atualizado"
else
  echo "  sem mudancas"
fi

passo "8/9 Reiniciar o Orthanc (exames sendo enviados agora podem precisar de reenvio)"
read -rp "Reiniciar agora? (s/N) " r
if [ "$r" = "s" ]; then
  systemctl restart orthanc; sleep 8
  if systemctl is-active --quiet orthanc; then
    echo "  Orthanc: ativo"
  else
    for b in "${BACKUPS[@]:-}"; do [ -n "$b" ] && cp -p "${b#*|}" "${b%%|*}"; done
    systemctl restart orthanc || true
    falha "Orthanc nao voltou. As configuracoes anteriores foram restauradas; veja: journalctl -u orthanc"
  fi
else
  aviso "  Pulado: reinicie depois (systemctl restart orthanc) para aplicar as mudancas."
fi

passo "9/9 Timer do systemd (ghbam-worklist, a cada 1 minuto, usuario $USUARIO_ORTHANC)"
cat > /etc/systemd/system/ghbam-worklist.service <<EOF
[Unit]
Description=GHBAM - gera a worklist do Orthanc
After=network-online.target

[Service]
Type=oneshot
User=$USUARIO_ORTHANC
ExecStart=/usr/bin/python3 $GERADOR ${ARGS[*]}
TimeoutStartSec=300
EOF
cat > /etc/systemd/system/ghbam-worklist.timer <<EOF
[Unit]
Description=GHBAM - worklist do Orthanc a cada minuto

[Timer]
OnBootSec=1min
OnUnitActiveSec=1min
AccuracySec=5s
Persistent=true

[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable --now ghbam-worklist.timer >/dev/null
systemctl start ghbam-worklist.service || true
echo "  timer: $(systemctl is-active ghbam-worklist.timer)"
echo "  log:"; tail -n 2 "$LOG_WL" | sed 's/^/    /'
echo "  arquivos .wl: $(find "$WL_DIR" -maxdepth 1 -name 'ghbam_*.wl' | wc -l)"

printf '\n\033[32mPronto.\033[0m\n'
echo "Falta a mao: cadastrar os aparelhos em DicomModalities (orthanc.json) e apontar a worklist/envio"
echo "de cada aparelho para este servidor. Para importar DICOM/ZIP de teste: importar_dicom.sh."
