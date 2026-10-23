#!/usr/bin/env python3
"""Gera a Modality Worklist (MWL) do Orthanc a partir da view worklist_orthanc_v do GHBAM (Linux).

Mesma logica e mesmos arquivos do GerarWorklist.ps1 (Windows): le os exames do dia via psql e
mantem na pasta do plugin Worklists um arquivo .wl por exame (ghbam_<idimagens>.wl). O aparelho
consulta a worklist e carimba no DICOM:
    AccessionNumber = idimagens   (chave usada pelo GHBAM para achar a imagem)
    PatientID       = pacienteid  (conferido pelo GHBAM antes da analise por IA)
Remove os .wl de exames que sairam da view; nao toca em .wl sem o prefixo ghbam_.
O log registra so contagens (nunca nome de paciente).

So usa a biblioteca padrao do Python 3 + o psql (pacote postgresql-client).
A senha do banco nunca fica aqui: vem do pgpass.conf (host:porta:banco:usuario:senha, chmod 600).

Uso:
    gerar_worklist.py --simular      # consulta e conta, sem gravar
    gerar_worklist.py                # sincroniza a pasta (uso no timer do systemd)
"""
import argparse
import csv
import datetime
import hashlib
import io
import os
import re
import shutil
import struct
import subprocess
import sys
import unicodedata

# Colunas da view -> campos DICOM (conferido com pg_get_viewdef em 2026-10-21).
COLUNAS = [
    ("accession", "accessionnumber"),
    ("pacienteid", "pacienteid"),
    ("nome", "pacientenome"),
    ("nascimento", "pacientenascimento"),
    ("sexo", "pacientesexo"),
    ("modalidade", "modalidade"),
    ("exame", "examenome"),
    ("data", "atendimentodata"),
    ("hora", "atendimentohora"),
]
PREFIXO = "ghbam_"
VR_LONGO = {"OB", "OW", "OF", "SQ", "UT", "UN", "UC", "UR"}


def escrever_log(caminho, msg):
    linha = f"{datetime.datetime.now():%Y-%m-%d %H:%M:%S} {msg}"
    try:
        with open(caminho, "a", encoding="utf-8") as f:
            f.write(linha + "\n")
    except OSError:
        pass
    print(linha)


def sem_acentos(s):
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")


def limpar(s, maximo):
    """Texto seguro para DICOM: sem acento, maiusculo, sem delimitadores (\\ ^ =), no tamanho do VR."""
    if not s or not s.strip():
        return ""
    s = re.sub(r"[\\^=\r\n\t]", " ", sem_acentos(s).upper())
    s = re.sub(r"\s+", " ", s).strip()
    return s[:maximo].strip()


def data_dicom(v):
    """Aceita aaaa-MM-dd, aaaa/MM/dd, dd/MM/aaaa e 8 digitos (aaaaMMdd ou ddMMaaaa)."""
    v = (v or "").strip()
    if not v:
        return ""
    m = re.match(r"^(\d{4})[-/](\d{2})[-/](\d{2})", v)
    if m:
        return "".join(m.groups())
    m = re.match(r"^(\d{2})/(\d{2})/(\d{4})", v)
    if m:
        return m.group(3) + m.group(2) + m.group(1)
    if re.fullmatch(r"\d{8}", v):
        # A view tira as barras do texto: aaaa/MM/dd vira aaaaMMdd, mas dd/MM/aaaa vira ddMMaaaa.
        for fmt in ("%Y%m%d", "%d%m%Y"):
            try:
                return datetime.datetime.strptime(v, fmt).strftime("%Y%m%d")
            except ValueError:
                pass
    return ""


def hora_dicom(v):
    """atendimentohora vem como HHmm ou HHmmss (a view tira os dois-pontos)."""
    m = re.match(r"^(\d{4})(\d{2})?", (v or "").strip())
    return (m.group(1) + (m.group(2) or "00")) if m else ""


def sexo_dicom(v):
    v = (v or "").strip().upper()
    if not v:
        return ""
    if v.startswith("M"):
        return "M"
    if v.startswith("F"):
        return "F"
    return "O"


def uid_estavel(semente):
    """UID estavel 2.25.<md5 como inteiro>: igual ao da versao Windows para o mesmo exame."""
    return "2.25." + str(int.from_bytes(hashlib.md5(semente.encode("utf-8")).digest(), "big"))


def elemento(grupo, elem, vr, valor):
    """Elemento DICOM em Explicit VR Little Endian."""
    cab = struct.pack("<HH", grupo, elem) + vr.encode("ascii")
    if vr in VR_LONGO:
        cab += struct.pack("<HI", 0, len(valor))
    else:
        cab += struct.pack("<H", len(valor))
    return cab + valor


def texto(grupo, elem, vr, s):
    b = (s or "").encode("latin-1", errors="replace")
    if len(b) % 2:
        b += b"\x00" if vr == "UI" else b" "
    return elemento(grupo, elem, vr, b)


def item_sequencia(conteudo):
    return struct.pack("<HHI", 0xFFFE, 0xE000, len(conteudo)) + conteudo


def arquivo_wl(r):
    sop_uid = uid_estavel(f"ghbam-wl-sop-{r['accession']}")
    study_uid = uid_estavel(f"ghbam-wl-study-{r['accession']}")
    data = r["data"] or datetime.date.today().strftime("%Y%m%d")
    hora = r["hora"] or "000000"

    # Scheduled Procedure Step (tags em ordem crescente)
    sps = b"".join([
        texto(0x0008, 0x0060, "CS", r["modalidade"]),
        texto(0x0040, 0x0002, "DA", data),
        texto(0x0040, 0x0003, "TM", hora),
        texto(0x0040, 0x0007, "LO", r["exame"]),
        texto(0x0040, 0x0009, "SH", r["accession"]),
    ])
    ds = b"".join([
        texto(0x0008, 0x0005, "CS", "ISO_IR 100"),
        texto(0x0008, 0x0050, "SH", r["accession"]),
        texto(0x0010, 0x0010, "PN", r["nome"]),
        texto(0x0010, 0x0020, "LO", r["pacienteid"]),
        texto(0x0010, 0x0030, "DA", r["nascimento"]),
        texto(0x0010, 0x0040, "CS", r["sexo"]),
        texto(0x0020, 0x000D, "UI", study_uid),
        texto(0x0032, 0x1060, "LO", r["exame"]),
        elemento(0x0040, 0x0100, "SQ", item_sequencia(sps)),
        texto(0x0040, 0x1001, "SH", r["accession"]),
    ])
    meta = b"".join([
        elemento(0x0002, 0x0001, "OB", b"\x00\x01"),
        texto(0x0002, 0x0002, "UI", "1.2.840.10008.5.1.4.31"),   # MWL Information Model - FIND
        texto(0x0002, 0x0003, "UI", sop_uid),
        texto(0x0002, 0x0010, "UI", "1.2.840.10008.1.2.1"),      # Explicit VR Little Endian
        texto(0x0002, 0x0012, "UI", uid_estavel("ghbam-worklist-gerador")),
    ])
    return (b"\x00" * 128 + b"DICM"
            + elemento(0x0002, 0x0000, "UL", struct.pack("<I", len(meta)))
            + meta + ds)


def montar_registro(linha):
    acc = limpar(linha.get("accession"), 16)
    id_pac = limpar(linha.get("pacienteid"), 64)
    if not acc or not id_pac or not acc.isdigit():
        return None
    return {
        "accession": acc,
        "pacienteid": id_pac,
        "nome": limpar(linha.get("nome"), 64),
        "nascimento": data_dicom(linha.get("nascimento")),
        "sexo": sexo_dicom(linha.get("sexo")),
        "modalidade": limpar(linha.get("modalidade"), 16),
        "exame": limpar(linha.get("exame"), 64),
        "data": data_dicom(linha.get("data")),
        "hora": hora_dicom(linha.get("hora")),
    }


def sincronizar(a):
    if not os.path.isdir(a.pasta):
        raise RuntimeError(f"Pasta de worklist nao existe: {a.pasta}")
    psql = a.psql or shutil.which("psql")
    if not psql:
        raise RuntimeError("psql nao encontrado (instale o pacote postgresql-client)")
    if not os.access(a.pgpass, os.R_OK):
        raise RuntimeError(f"pgpass nao encontrado ou sem acesso: {a.pgpass}")
    with open(a.pgpass, "rb") as f:
        if f.read(3) == b"\xef\xbb\xbf":
            raise RuntimeError("pgpass.conf salvo com BOM: o psql nao reconhece a linha. Regravar sem BOM.")

    env = dict(os.environ, PGPASSFILE=a.pgpass, PGCLIENTENCODING="UTF8")
    sel = ", ".join(f'"{col}" AS {chave}' for chave, col in COLUNAS)
    proc = subprocess.run(
        [psql, "-h", a.db_host, "-p", str(a.db_port), "-U", a.db_usuario, "-d", a.db_nome,
         "-w", "-X", "--csv", "-c", f"SELECT {sel} FROM {a.view}"],
        env=env, capture_output=True, timeout=120)
    if proc.returncode != 0:
        sys.stderr.write(proc.stderr.decode("utf-8", "replace"))
        raise RuntimeError(f"psql terminou com codigo {proc.returncode} (nada foi alterado)")
    linhas = list(csv.DictReader(io.StringIO(proc.stdout.decode("utf-8", "replace"))))

    desejados, ignorados = {}, 0
    for linha in linhas:
        r = montar_registro(linha)
        if r is None:
            ignorados += 1
            continue
        desejados[f"{PREFIXO}{r['accession']}.wl"] = arquivo_wl(r)

    gravados = iguais = removidos = 0
    for nome, conteudo in desejados.items():
        destino = os.path.join(a.pasta, nome)
        try:
            with open(destino, "rb") as f:
                if f.read() == conteudo:
                    iguais += 1
                    continue
        except OSError:
            pass
        if not a.simular:
            tmp = destino + ".tmp"   # grava e renomeia: o plugin nunca le arquivo pela metade
            with open(tmp, "wb") as f:
                f.write(conteudo)
            os.chmod(tmp, 0o640)
            os.replace(tmp, destino)
        gravados += 1

    for nome in os.listdir(a.pasta):
        if nome.startswith(PREFIXO) and nome.endswith(".wl") and nome not in desejados:
            if not a.simular:
                os.remove(os.path.join(a.pasta, nome))
            removidos += 1

    modo = "SIMULACAO" if a.simular else "OK"
    escrever_log(a.log, f"{modo}: {len(linhas)} exames na view, {gravados} gravados, {iguais} sem mudanca, "
                        f"{removidos} removidos, {ignorados} ignorados")


def main():
    p = argparse.ArgumentParser(description="Gera a worklist do Orthanc a partir do GHBAM.")
    p.add_argument("--pasta", default="/var/lib/orthanc/worklists")
    p.add_argument("--db-host", default="10.200.200.7")
    p.add_argument("--db-port", type=int, default=5432)
    p.add_argument("--db-nome", default="hospitalit")
    p.add_argument("--db-usuario", default="orthanc_worklist")
    p.add_argument("--view", default="worklist_orthanc_v")   # criada pela migration V208/V209
    p.add_argument("--pgpass", default="/etc/ghbam/pgpass.conf")
    p.add_argument("--psql", default="")
    p.add_argument("--log", default="/var/log/ghbam/worklist.log")
    p.add_argument("--simular", action="store_true")
    a = p.parse_args()
    if not re.fullmatch(r"[a-z_][a-z0-9_]*", a.view):
        p.error("nome de view invalido")
    try:
        sincronizar(a)
    except Exception as e:  # noqa: BLE001 - qualquer falha vira linha de log e codigo 1
        escrever_log(a.log, f"ERRO: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
