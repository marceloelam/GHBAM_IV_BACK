"""
Monta o conjunto LOCAL de validacao de IA de fratura (raio-X), pseudonimizado.

Junta, pelo AccessionNumber (= idimagens do GHBAM):
  - a imagem DICOM do Orthanc interno (so exames que os aparelhos enviaram);
  - o laudo em PDF do RIS (mesma URL de laudo que o GHBAM ja usa por unidade).

Do laudo sai so o ROTULO (FRATURA / SEM_FRATURA / INCERTO); o texto do laudo
nao e gravado. As imagens saem sem dados de identificacao no cabecalho e com
nome trocado por um codigo HMAC (so quem tem o segredo religa ao exame).

Rodar SO dentro da rede do hospital, com o projeto aprovado no CEP. Os dados
nao devem sair do servidor. Uso:

  export FRATURA_SEGREDO='<frase longa guardada pela TI>'
  python montar_validacao.py \
      --orthanc http://usuario:senha@10.200.200.78:8042 \
      --laudo  https://ris.exemplo/laudo \
      --saida  /opt/ghbam-monai/validacao-fratura \
      [--ca /caminho/cadeia-ris.pem] [--max 500]

Depende de: pydicom, pypdf (pip install pydicom pypdf).
"""
import argparse
import base64
import csv
import hashlib
import hmac
import io
import json
import os
import re
import ssl
import sys
import unicodedata
import urllib.parse
import urllib.request

# ---------------------------------------------------------------------------
# Rotulo a partir do texto do laudo
# ---------------------------------------------------------------------------
_NEGA = re.compile(
    r"(sem|nao|ausencia de|nao ha|nao se (observa|identifica|evidencia|caracteriza)[a-z]*|"
    r"inexistencia de|livre de|afastad[ao])"
    r"[^.;\n]{0,60}?\bfratur",
)
_ANTIGA = re.compile(r"\bfratur[a-z]*\b[^.;\n]{0,40}?\b(consolidad|antig|previa|calo osseo|sequela|pos[- ]?operatori)")
_FRATURA = re.compile(r"\bfratur")
_SUSPEITA = re.compile(r"(suspeit|duvidos|possivel|provavel|nao se pode afastar|sugestiv|a esclarecer)[^.;\n]{0,40}?\bfratur"
                       r"|\bfratur[^.;\n]{0,40}?(suspeit|duvidos|a esclarecer)")


def _normalizar(texto: str) -> str:
    t = unicodedata.normalize("NFD", texto or "")
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    return re.sub(r"\s+", " ", t.lower())


def rotular_laudo(texto: str) -> str:
    """FRATURA | SEM_FRATURA | INCERTO. Na duvida, INCERTO (vai para revisao humana).

    Frase a frase: uma frase com fratura AFIRMADA (nao negada, nao antiga, nao
    suspeita) torna o laudo FRATURA. Fratura antiga/consolidada ou suspeita = INCERTO.
    Sem nenhuma mencao a fratura = SEM_FRATURA so se o laudo tiver conteudo.
    """
    t = _normalizar(texto)
    if len(t) < 40:
        return "INCERTO"  # laudo vazio/ilegivel
    afirmada = incerta = False
    for f in re.split(r"[.;\n]", t):
        if not _FRATURA.search(f):
            continue
        if _NEGA.search(f):
            continue
        if _ANTIGA.search(f) or _SUSPEITA.search(f):
            incerta = True
            continue
        afirmada = True
    if afirmada:
        return "FRATURA"
    if incerta:
        return "INCERTO"
    return "SEM_FRATURA"


# ---------------------------------------------------------------------------
# Pseudonimizacao do DICOM (subconjunto do perfil basico do PS3.15)
# ---------------------------------------------------------------------------
_TAGS_REMOVER = [
    "PatientName", "PatientID", "PatientBirthDate", "PatientAddress", "PatientTelephoneNumbers",
    "OtherPatientIDs", "OtherPatientNames", "PatientMotherBirthName", "MedicalRecordLocator",
    "AccessionNumber", "InstitutionName", "InstitutionAddress", "InstitutionalDepartmentName",
    "ReferringPhysicianName", "PerformingPhysicianName", "OperatorsName", "PhysiciansOfRecord",
    "RequestingPhysician", "StationName", "DeviceSerialNumber", "StudyID", "RequestedProcedureID",
    "ScheduledProcedureStepID", "PerformedProcedureStepID", "AdmissionID", "IssuerOfPatientID",
    "StudyDate", "SeriesDate", "AcquisitionDate", "ContentDate", "StudyTime", "SeriesTime",
    "AcquisitionTime", "ContentTime", "AcquisitionDateTime", "InstanceCreationDate", "InstanceCreationTime",
]


def pseudonimizar(raw: bytes, codigo: str) -> bytes:
    import pydicom  # noqa: WPS433
    from pydicom.uid import generate_uid  # noqa: WPS433
    ds = pydicom.dcmread(io.BytesIO(raw))
    for nome in _TAGS_REMOVER:
        if nome in ds:
            delattr(ds, nome)
    ds.remove_private_tags()
    ds.PatientID = codigo
    ds.PatientName = codigo
    # UIDs novos e deterministicos pelo codigo: nao ligam de volta ao estudo original.
    ds.StudyInstanceUID = generate_uid(entropy_srcs=[codigo, "study"])
    ds.SeriesInstanceUID = generate_uid(entropy_srcs=[codigo, "series", str(getattr(ds, "SeriesNumber", ""))])
    ds.SOPInstanceUID = generate_uid(entropy_srcs=[codigo, "sop", str(getattr(ds, "InstanceNumber", ""))])
    if hasattr(ds, "file_meta"):
        ds.file_meta.MediaStorageSOPInstanceUID = ds.SOPInstanceUID
    ds.PatientIdentityRemoved = "YES"
    ds.DeidentificationMethod = "GHBAM fratura: PS3.15 basico (subconjunto), datas removidas"
    out = io.BytesIO()
    ds.save_as(out)
    return out.getvalue()


# ---------------------------------------------------------------------------
# Acesso ao Orthanc e ao RIS
# ---------------------------------------------------------------------------
class Http:
    def __init__(self, ca):
        self.ctx = ssl.create_default_context(cafile=ca) if ca else ssl.create_default_context()

    def get(self, url, auth=None, timeout=60):
        req = urllib.request.Request(url)
        if auth:
            req.add_header("Authorization", auth)
        with urllib.request.urlopen(req, timeout=timeout, context=self.ctx) as r:
            return r.read(), r.headers.get("Content-Type", "")

    def post_json(self, url, corpo, auth):
        req = urllib.request.Request(url, data=json.dumps(corpo).encode(), method="POST",
                                     headers={"Content-Type": "application/json"})
        if auth:
            req.add_header("Authorization", auth)
        with urllib.request.urlopen(req, timeout=60, context=self.ctx) as r:
            return json.loads(r.read())


def separar_credencial(url):
    """(base REST sem credencial e sem /dicom-web, header Basic ou None)."""
    p = urllib.parse.urlsplit(url)
    auth = None
    if p.username:
        par = f"{urllib.parse.unquote(p.username)}:{urllib.parse.unquote(p.password or '')}"
        auth = "Basic " + base64.b64encode(par.encode()).decode()
    host = p.hostname + (f":{p.port}" if p.port else "")
    limpa = urllib.parse.urlunsplit((p.scheme, host, p.path.rstrip("/"), "", ""))
    if limpa.endswith("/dicom-web"):
        limpa = limpa[: -len("/dicom-web")]
    return limpa, auth


def texto_pdf(raw):
    from pypdf import PdfReader  # noqa: WPS433
    return "\n".join((pagina.extract_text() or "") for pagina in PdfReader(io.BytesIO(raw)).pages)


def regiao(desc):
    d = " " + _normalizar(desc) + " "
    for nome, termos in [
        # pe antes da mao: "dedos do pe" nao pode cair em mao_punho.
        ("tornozelo_pe", ("tornozelo", " pe ", " pe/", "dedos do pe", "calcaneo", "artelho", "pododactil")),
        ("mao_punho", (" mao", "punho", "dedos da mao", "quirodactil", "escafoide", "carpo")),
        ("antebraco_cotovelo", ("antebraco", "cotovelo", " radio", " ulna")),
        ("ombro_umero", ("ombro", "umero", "clavicula", "escapula")),
        ("quadril_bacia", ("quadril", "bacia", "pelve", "coxofemoral")),
        ("joelho_perna", ("joelho", "patela", "perna", "tibia", "fibula", "femur")),
        ("coluna", ("coluna", "lombar", "cervical", "toracica", "dorsal", "sacro")),
        ("torax", ("torax", "costela", "arcos costais")),
        ("cranio_face", ("cranio", "face", "nariz", "seios", "mandibula", "orbita")),
    ]:
        if any(t in d for t in termos):
            return nome
    return "outra"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--orthanc", required=True)
    ap.add_argument("--laudo", required=True, help="URL base de laudo do RIS (a do cadastro da unidade)")
    ap.add_argument("--saida", required=True)
    ap.add_argument("--ca", help="cadeia de certificados do RIS (PEM), se o TLS falhar")
    ap.add_argument("--max", type=int, default=0, help="limite de estudos (0 = todos)")
    a = ap.parse_args()

    segredo = os.environ.get("FRATURA_SEGREDO", "")
    if len(segredo) < 16:
        sys.exit("Defina FRATURA_SEGREDO (16+ caracteres) para gerar os codigos pseudonimos.")

    http = Http(a.ca)
    base, auth = separar_credencial(a.orthanc)
    pasta_img = os.path.join(a.saida, "imagens")
    os.makedirs(pasta_img, exist_ok=True)

    estudos = []
    for mod in ("CR", "DX"):
        estudos += http.post_json(base + "/tools/find",
                                  {"Level": "Study", "Query": {"ModalitiesInStudy": mod}, "Expand": True}, auth)
    vistos, linhas = set(), []
    contagem = {"FRATURA": 0, "SEM_FRATURA": 0, "INCERTO": 0, "sem_laudo": 0}

    for est in estudos:
        if est["ID"] in vistos:
            continue
        if a.max and len(vistos) >= a.max:
            break
        vistos.add(est["ID"])
        tags = est.get("MainDicomTags", {})
        acc = str(tags.get("AccessionNumber", "")).strip()
        if not acc.isdigit():
            continue  # sem idimagens do GHBAM: nao da para achar o laudo
        try:
            pdf, tipo = http.get(f"{a.laudo.rstrip('/')}/{urllib.parse.quote(acc)}")
        except Exception:
            contagem["sem_laudo"] += 1
            continue
        if "pdf" not in tipo.lower() or not pdf:
            contagem["sem_laudo"] += 1
            continue
        try:
            rotulo = rotular_laudo(texto_pdf(pdf))
        except Exception:
            rotulo = "INCERTO"
        contagem[rotulo] += 1

        codigo = hmac.new(segredo.encode(), acc.encode(), hashlib.sha256).hexdigest()[:16]
        reg = regiao(tags.get("StudyDescription", ""))
        for n, inst in enumerate(_instancias(http, base, est["ID"], auth)):
            raw, _ = http.get(f"{base}/instances/{inst}/file", auth)
            nome = f"{codigo}_{n}.dcm"
            with open(os.path.join(pasta_img, nome), "wb") as f:
                f.write(pseudonimizar(raw, codigo))
            linhas.append({"caso": codigo, "imagem": nome, "regiao": reg, "rotulo": rotulo})

    with open(os.path.join(a.saida, "rotulos.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["caso", "imagem", "regiao", "rotulo"])
        w.writeheader()
        w.writerows(linhas)
    print(json.dumps({"estudos": len(vistos), "imagens": len(linhas), **contagem}, ensure_ascii=False))
    print("INCERTO precisa de revisao por radiologista antes de entrar na avaliacao.")
    print("Confira imagens com texto gravado nos pixels (nome/prontuario): o cabecalho foi limpo, os pixels nao.")


def _instancias(http, base, estudo_id, auth):
    """IDs das instancias do estudo no Orthanc (GET /studies/{id}/instances)."""
    raw, _ = http.get(f"{base}/studies/{estudo_id}/instances", auth)
    return [i["ID"] for i in json.loads(raw)]


if __name__ == "__main__":
    main()
