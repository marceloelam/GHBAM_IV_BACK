"""
Servico de inferencia MONAI do GHBAM.

Recebe um estudo DICOM do backend (ImagemIaClient) e devolve um achado SUGERIDO.
A IA nunca diagnostica: o backend guarda o achado e o medico registra ciencia.

Dois modos:
  - bundle: carrega um MONAI Bundle do Model Zoo (env MONAI_BUNDLE_DIR) e roda a
    inferencia de verdade. Use CPU no piloto; GPU quando o volume justificar.
  - stub (padrao): sem bundle carregado, apenas le os metadados do DICOM e
    devolve um achado INFORMATIVO dizendo que nenhum modelo esta ativo. Mantem o
    fluxo ponta a ponta testavel antes de baixar/validar um modelo clinico.

Nao expor na internet: roda na rede interna, so o backend GHBAM o chama.
"""
import io
import os
import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("monai-service")

BUNDLE_DIR = os.environ.get("MONAI_BUNDLE_DIR", "").strip()
MODELO = os.environ.get("MONAI_MODELO", "stub")
VERSAO = os.environ.get("MONAI_VERSAO", "0.1.0")
MAX_BYTES = int(os.environ.get("MONAI_MAX_BYTES", str(200 * 1024 * 1024)))  # 200 MB

app = FastAPI(title="GHBAM MONAI", version=VERSAO)

# Carregamento preguicoso do bundle: so quando o servico sobe com MONAI_BUNDLE_DIR.
_inferer = None


def _carregar_bundle():
    """Carrega o MONAI Bundle configurado. Retorna None se nao houver bundle."""
    global _inferer
    if not BUNDLE_DIR:
        return None
    if _inferer is not None:
        return _inferer
    # Import tardio para o modo stub nao exigir torch/monai instalados.
    from monai.bundle import create_workflow  # noqa: WPS433
    log.info("Carregando MONAI Bundle de %s", BUNDLE_DIR)
    _inferer = create_workflow(
        config_file=os.path.join(BUNDLE_DIR, "configs", "inference.json"),
        workflow_type="infer",
    )
    return _inferer


def _ler_dicom(raw: bytes):
    """Le metadados basicos do DICOM para log e para o modo stub."""
    import pydicom  # noqa: WPS433
    ds = pydicom.dcmread(io.BytesIO(raw), stop_before_pixels=True, force=True)
    return {
        "modality": str(getattr(ds, "Modality", "")),
        "rows": int(getattr(ds, "Rows", 0) or 0),
        "columns": int(getattr(ds, "Columns", 0) or 0),
        "study_description": str(getattr(ds, "StudyDescription", "")),
    }


# ---------------------------------------------------------------------------
# Modo TorchXRayVision (modelo de pesquisa de TORAX, roda em CPU). DEMONSTRACAO:
# em filme que nao e de torax o resultado nao tem valor clinico. Ligado por
# XRV_HABILITADO=true. NUNCA validado para uso assistencial.
# ---------------------------------------------------------------------------
XRV_ON = os.environ.get("XRV_HABILITADO", "").strip().lower() in ("1", "true", "sim")
_xrv_model = None


def _carregar_xrv():
    global _xrv_model
    if _xrv_model is None:
        import torchxrayvision as xrv  # noqa: WPS433
        log.info("Carregando modelo TorchXRayVision densenet121-res224-all")
        _xrv_model = xrv.models.DenseNet(weights="densenet121-res224-all")
        _xrv_model.eval()
    return _xrv_model


# Nomes das patologias do modelo (ingles) traduzidos para o portugues clinico.
PATOLOGIAS_PT = {
    "Atelectasis": "Atelectasia",
    "Consolidation": "Consolidacao",
    "Infiltration": "Infiltrado",
    "Pneumothorax": "Pneumotorax",
    "Edema": "Edema",
    "Emphysema": "Enfisema",
    "Fibrosis": "Fibrose",
    "Effusion": "Derrame pleural",
    "Pneumonia": "Pneumonia",
    "Pleural_Thickening": "Espessamento pleural",
    "Cardiomegaly": "Cardiomegalia (coracao aumentado)",
    "Nodule": "Nodulo",
    "Mass": "Massa",
    "Hernia": "Hernia",
    "Lung Lesion": "Lesao pulmonar",
    "Fracture": "Fratura",
    "Lung Opacity": "Opacidade pulmonar",
    "Enlarged Cardiomediastinum": "Alargamento do mediastino",
}


def _pt(nome: str) -> str:
    return PATOLOGIAS_PT.get(nome, nome)


def _rotear(meta: dict, exame: str) -> str:
    """Decide o tipo de exame pela modalidade + descricao, para escolher o modelo.
    Hoje so ha modelo de TORAX (raio-X). O resto cai em 'sem modelo'."""
    modality = str(meta.get("modality", "")).upper()
    txt = ((exame or "") + " " + str(meta.get("study_description", ""))).upper()

    import re
    def tem(*termos):
        return any(re.search(r"\b" + re.escape(t) + r"\b", txt) for t in termos)

    if modality in ("CR", "DX", "RX"):
        eh_torax = tem("TORAX", "TÓRAX", "CHEST", "PULM")
        eh_outra = tem("COLUNA", "LOMBAR", "CERVICAL", "DORSAL", "CRANIO", "CRÂNIO",
                       "ABDOME", "ABDÔMEN", "BACIA", "MEMBRO", "JOELHO", "PUNHO",
                       "MAO", "MÃO", "PE", "TORNOZELO", "OMBRO", "FEMUR", "FÊMUR")
        return "torax_rx" if (eh_torax and not eh_outra) else "rx_outra_regiao"
    if modality in ("CT",):
        return "tomografia"
    return "modalidade_sem_modelo"


def _sem_modelo_msg(tipo: str) -> str:
    return {
        "rx_outra_regiao": "Raio-X de regiao sem modelo de IA disponivel (hoje so torax).",
        "tomografia": "Tomografia ainda nao tem modelo de IA neste servico.",
        "modalidade_sem_modelo": "Modalidade sem modelo de IA disponivel.",
    }.get(tipo, "Sem modelo de IA para este tipo de exame.")


def _inferir_xrv(raw: bytes) -> dict:
    import io as _io  # noqa: WPS433
    import pydicom  # noqa: WPS433
    import torch  # noqa: WPS433
    import torchvision  # noqa: WPS433
    import torchxrayvision as xrv  # noqa: WPS433

    ds = pydicom.dcmread(_io.BytesIO(raw))
    arr = ds.pixel_array.astype("float32")
    if arr.ndim == 3:
        arr = arr.mean(axis=2)
    if str(getattr(ds, "PhotometricInterpretation", "")) == "MONOCHROME1":
        arr = arr.max() - arr
    maxv = float(arr.max()) if arr.max() > 0 else 255.0
    img = xrv.datasets.normalize(arr, maxv)[None, ...]
    transform = torchvision.transforms.Compose(
        [xrv.datasets.XRayCenterCrop(), xrv.datasets.XRayResizer(224)]
    )
    img = transform(img)
    model = _carregar_xrv()
    with torch.no_grad():
        out = model(torch.from_numpy(img)[None, ...])
    preds = {k: float(v) for k, v in zip(model.pathologies, out[0].numpy()) if k}
    top = sorted(preds.items(), key=lambda kv: kv[1], reverse=True)
    nome, prob = top[0]
    try:
        mapa = _mapa_calor_xrv(model, img, list(model.pathologies).index(nome))
    except Exception as e:  # o mapa e acessorio: nunca derruba a analise
        log.warning("Falha no mapa de calor: %s", e)
        mapa = None
    pct = int(round(prob * 100))
    if prob >= 0.5:
        sev = "ATENCAO"
        achado = ("Sugestao (modelo de pesquisa, NAO validado): %s (probabilidade %d%%). "
                  "Conferir com a radiologia." % (_pt(nome), pct))
    else:
        sev = "INFORMATIVO"
        achado = "Sem achado acima do limiar (maior: %s, %d%%)." % (_pt(nome), pct)
    return {
        "achado": achado,
        "severidade": sev,
        "score": round(float(prob), 4),
        "modelo": "torchxrayvision-densenet121-all",
        "versao_modelo": "1.0",
        "top": {_pt(k): round(v, 3) for k, v in top[:5]},
        "mapa_calor_jpg": mapa,
        "mapa_calor_achado": _pt(nome) if mapa else None,
    }


def _mapa_calor_xrv(model, img, idx: int):
    """Grad-CAM do achado principal sobre a imagem 224x224, em JPEG base64.

    Mostra a regiao que MAIS PESOU na sugestao; nao e delimitacao de lesao.
    Refaz a saida do modelo sem o ReLU in-place da biblioteca (quebraria o
    gradiente) e com o mesmo op_norm do forward.
    """
    import base64  # noqa: WPS433
    import numpy as np  # noqa: WPS433
    import torch  # noqa: WPS433
    import torch.nn.functional as F  # noqa: WPS433
    from PIL import Image  # noqa: WPS433
    from torchxrayvision.models import op_norm  # noqa: WPS433

    x = torch.from_numpy(img)[None].float()
    with torch.enable_grad():
        feats = model.features(x)
        feats.retain_grad()
        pooled = F.adaptive_avg_pool2d(F.relu(feats), (1, 1)).view(1, -1)
        out = torch.sigmoid(model.classifier(pooled))
        if getattr(model, "op_threshs", None) is not None:
            out = op_norm(out, model.op_threshs)
        model.zero_grad()
        out[0, idx].backward()
    pesos = feats.grad[0].mean(dim=(1, 2))
    cam = torch.relu((pesos[:, None, None] * feats[0]).sum(0)).detach().numpy()
    if cam.max() <= 0:
        return None
    cam = cam / cam.max()
    cam = np.array(Image.fromarray((cam * 255).astype("uint8")).resize((224, 224), Image.BILINEAR)) / 255.0

    base = img[0]
    base = (base - base.min()) / ((base.max() - base.min()) or 1.0)
    cinza = np.stack([base] * 3, axis=-1)
    # Paleta quente simples (amarelo -> vermelho), so onde o mapa e relevante.
    cor = np.stack([np.ones_like(cam), 1.0 - cam, np.zeros_like(cam)], axis=-1)
    alfa = (np.clip((cam - 0.35) / 0.65, 0, 1) * 0.55)[..., None]
    rgb = ((1 - alfa) * cinza + alfa * cor) * 255
    jpg = Image.fromarray(rgb.astype("uint8")).resize((448, 448), Image.BILINEAR)
    buf = io.BytesIO()
    jpg.save(buf, format="JPEG", quality=80, optimize=True)  # ~20 KB: vai para o banco a cada analise
    return base64.b64encode(buf.getvalue()).decode("ascii")


# ---------------------------------------------------------------------------
# Tomografia de cranio: hemorragia intracraniana (modelo de PESQUISA, MIT,
# ianpan/ct-head-hemorrhage-detection, treinado no RSNA ICH 2019). Recebe a
# SERIE inteira (ZIP do Orthanc). Ligado por ICH_HABILITADO=true + ICH_MODELO_DIR
# (pasta com inference.py e os pesos .safetensors). NUNCA validado para uso
# assistencial: so sugere, e resultado negativo NAO exclui hemorragia.
# ---------------------------------------------------------------------------
ICH_ON = os.environ.get("ICH_HABILITADO", "").strip().lower() in ("1", "true", "sim")
ICH_DIR = os.environ.get("ICH_MODELO_DIR", "").strip()
ICH_LIMIAR = float(os.environ.get("ICH_LIMIAR", "0.5"))
ICH_BATCH = int(os.environ.get("ICH_BATCH", "4"))
SERIE_MAX_BYTES = int(os.environ.get("MONAI_SERIE_MAX_BYTES", str(600 * 1024 * 1024)))  # 600 MB
_ich_model = None

import threading  # noqa: E402
_ich_lock = threading.Lock()  # CPU: uma TC por vez, para nao disputar nucleos

ICH_CLASSES_PT = {
    "epidural": "extradural (epidural)",
    "intraparenchymal": "intraparenquimatosa",
    "intraventricular": "intraventricular",
    "subarachnoid": "subaracnoidea",
    "subdural": "subdural",
}
ICH_CLASSES = ["epidural", "intraparenchymal", "intraventricular", "subarachnoid", "subdural", "any"]


def _ler_serie_zip(raw: bytes):
    """Abre o ZIP do Orthanc (/series/{id}/archive) e devolve os DICOMs com pixels."""
    import zipfile  # noqa: WPS433
    import pydicom  # noqa: WPS433
    dss = []
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        for nome in z.namelist():
            if nome.endswith("/"):
                continue
            try:
                ds = pydicom.dcmread(io.BytesIO(z.read(nome)), force=True)
            except Exception:
                continue
            if "PixelData" in ds:
                dss.append(ds)
    return dss


def _volume_hu(dss):
    """Ordena os cortes pela posicao fisica e monta o volume (D, H, W) em HU.

    Exige serie AXIAL de CT; os cortes sao ordenados pela projecao da
    ImagePositionPatient na normal do plano (crescente = inferior -> superior).
    """
    import numpy as np  # noqa: WPS433
    if not dss:
        raise ValueError("Serie sem imagens DICOM.")
    if str(getattr(dss[0], "Modality", "")).upper() != "CT":
        raise ValueError("Serie nao e de tomografia (CT).")
    # Fica so com o formato de imagem mais comum (descarta localizador perdido na serie).
    formatos = {}
    for ds in dss:
        formatos.setdefault((int(ds.Rows), int(ds.Columns)), []).append(ds)
    dss = max(formatos.values(), key=len)

    iop = [float(v) for v in getattr(dss[0], "ImageOrientationPatient", [1, 0, 0, 0, 1, 0])]
    normal = np.cross(np.array(iop[:3]), np.array(iop[3:]))
    if abs(float(normal[2])) < 0.8:
        raise ValueError("Serie nao axial (reconstrucao coronal/sagital); o modelo exige cortes axiais.")

    def posicao(ds):
        ipp = getattr(ds, "ImagePositionPatient", None)
        if ipp is not None and len(ipp) == 3:
            return float(np.dot(np.array([float(v) for v in ipp]), normal))
        return float(getattr(ds, "InstanceNumber", 0) or 0)

    dss = sorted(dss, key=posicao)
    posicoes = [posicao(ds) for ds in dss]
    passos = sorted(b - a for a, b in zip(posicoes, posicoes[1:]) if b > a)
    dz = passos[len(passos) // 2] if passos else float(getattr(dss[0], "SliceThickness", 1) or 1)
    ps = [float(v) for v in getattr(dss[0], "PixelSpacing", [1, 1])]
    # Afim LPS dos eixos (coluna, linha, corte) -> mm; usada pelo modelo 3D de nodulo.
    afim = np.eye(4)
    afim[:3, 0] = np.array(iop[:3]) * ps[1]
    afim[:3, 1] = np.array(iop[3:]) * ps[0]
    afim[:3, 2] = normal * dz
    ipp0 = getattr(dss[0], "ImagePositionPatient", None)
    afim[:3, 3] = [float(v) for v in ipp0] if ipp0 is not None and len(ipp0) == 3 else [0, 0, 0]
    fatias = []
    for ds in dss:
        slope = float(getattr(ds, "RescaleSlope", 1) or 1)
        intercept = float(getattr(ds, "RescaleIntercept", 0) or 0)
        fatias.append(ds.pixel_array.astype("float32") * slope + intercept)
    vol = np.stack(fatias).astype("float32")
    if not np.isfinite(vol).all():
        raise ValueError("Volume com valores invalidos.")
    d0 = dss[0]
    meta = {
        "modality": "CT",
        "cortes": len(dss),
        "rows": int(d0.Rows),
        "columns": int(d0.Columns),
        "series_description": str(getattr(d0, "SeriesDescription", "")),
        "study_description": str(getattr(d0, "StudyDescription", "")),
        "body_part": str(getattr(d0, "BodyPartExamined", "")),
        "espessura_mm": str(getattr(d0, "SliceThickness", "")),
        "instancias": [int(getattr(ds, "InstanceNumber", 0) or 0) for ds in dss],
        "posicoes": posicoes,
        "afim_lps": afim.tolist(),
    }
    return vol, meta


def _rotear_tc(meta: dict, exame: str) -> str:
    """TC de cranio (encefalo) vai para o modelo de hemorragia; o resto fica sem modelo."""
    import re
    txt = " ".join([exame or "", meta.get("series_description", ""), meta.get("study_description", ""),
                    meta.get("body_part", "")]).upper()

    def tem(*termos):
        return any(re.search(r"\b" + re.escape(t) + r"\b", txt) for t in termos)

    cabeca = tem("CRANIO", "CRÂNIO", "CRANIANA", "CRANIANO", "CEREBRO", "CÉREBRO", "ENCEFALO", "ENCÉFALO",
                 "HEAD", "BRAIN", "SKULL")
    fora = tem("FACE", "SEIOS", "ORBITA", "ÓRBITA", "MASTOIDE", "MASTÓIDE", "OUVIDO", "PESCOCO", "PESCOÇO")
    if cabeca and not fora:
        return "tc_cranio"
    if tem("TORAX", "TÓRAX", "THORAX", "CHEST", "PULMAO", "PULMÃO", "PULMOES", "PULMÕES", "PULMONAR", "LUNG"):
        return "tc_torax"
    return "tc_outra_regiao"


# ---------------------------------------------------------------------------
# TC de torax: nodulo pulmonar (MONAI Model Zoo lung_nodule_ct_detection,
# Apache-2.0, RetinaNet 3D treinado no LUNA16/LIDC-IDRI). Ligado por
# NODULO_HABILITADO=true + NODULO_MODELO_DIR (pasta do bundle, com
# models/model.pt). Pesos carregados com weights_only (sem pickle executavel).
# Modelo de PESQUISA: negativo NAO exclui nodulo; nao avalia malignidade.
# ---------------------------------------------------------------------------
NODULO_ON = os.environ.get("NODULO_HABILITADO", "").strip().lower() in ("1", "true", "sim")
NODULO_DIR = os.environ.get("NODULO_MODELO_DIR", "").strip()
NODULO_LIMIAR = float(os.environ.get("NODULO_LIMIAR", "0.5"))
NODULO_ESPACAMENTO = (0.703125, 0.703125, 1.25)  # mm, o do treino (LUNA16 reamostrado)
NODULO_JANELA = (512, 512, 192)
_nodulo_det = None


def _carregar_nodulo():
    global _nodulo_det
    if _nodulo_det is None:
        import torch  # noqa: WPS433
        from monai.apps.detection.networks.retinanet_detector import RetinaNetDetector  # noqa: WPS433
        from monai.apps.detection.networks.retinanet_network import (  # noqa: WPS433
            RetinaNet, resnet_fpn_feature_extractor)
        from monai.apps.detection.utils.anchor_utils import AnchorGeneratorWithAnchorShape  # noqa: WPS433
        from monai.networks.nets import resnet  # noqa: WPS433
        pesos = os.path.join(NODULO_DIR, "models", "model.pt")
        if not os.path.isfile(pesos):
            raise RuntimeError("NODULO_MODELO_DIR sem models/model.pt.")
        log.info("Carregando modelo de nodulo pulmonar de %s", pesos)
        # Mesma rede do configs/inference.json do bundle.
        backbone = resnet.resnet50(spatial_dims=3, n_input_channels=1,
                                   conv1_t_stride=[2, 2, 1], conv1_t_size=[7, 7, 7])
        extrator = resnet_fpn_feature_extractor(backbone, 3, False, [1, 2], None)
        rede = RetinaNet(spatial_dims=3, num_classes=1, num_anchors=3, feature_extractor=extrator,
                         size_divisible=[16, 16, 8], use_list_output=False)
        rede.load_state_dict(torch.load(pesos, map_location="cpu", weights_only=True))
        ancoras = AnchorGeneratorWithAnchorShape(
            feature_map_scales=[1, 2, 4], base_anchor_shapes=[[6, 8, 4], [8, 6, 5], [10, 10, 6]])
        det = RetinaNetDetector(network=rede, anchor_generator=ancoras, debug=False,
                                spatial_dims=3, num_classes=1, size_divisible=[16, 16, 8])
        det.set_target_keys(box_key="box", label_key="label")
        det.set_box_selector_parameters(score_thresh=0.02, topk_candidates_per_level=1000,
                                        nms_thresh=0.22, detections_per_img=300)
        det.set_sliding_window_inferer(roi_size=list(NODULO_JANELA), overlap=0.25, sw_batch_size=1,
                                       mode="constant", device="cpu")
        det.eval()
        _nodulo_det = det
    return _nodulo_det


def _inferir_nodulo(vol, meta: dict) -> dict:
    import numpy as np  # noqa: WPS433
    import torch  # noqa: WPS433
    from monai.data import MetaTensor  # noqa: WPS433
    from monai.transforms import Orientation, ScaleIntensityRange, Spacing  # noqa: WPS433

    # (corte, linha, coluna) -> (coluna, linha, corte), eixos da afim; LPS -> RAS.
    arr = np.ascontiguousarray(vol.transpose(2, 1, 0))
    afim_ras = np.diag([-1.0, -1.0, 1.0, 1.0]) @ np.array(meta["afim_lps"])
    img = MetaTensor(torch.from_numpy(arr)[None], affine=torch.tensor(afim_ras))
    img = Orientation(axcodes="RAS")(img)
    img = Spacing(pixdim=NODULO_ESPACAMENTO)(img)
    img = ScaleIntensityRange(a_min=-1024.0, a_max=300.0, b_min=0.0, b_max=1.0, clip=True)(img)
    afim_final = np.array(img.affine)
    entrada = img.as_tensor().float()
    usar_janela = entrada[0].numel() >= int(np.prod(NODULO_JANELA))

    with _ich_lock:
        det = _carregar_nodulo()
        with torch.no_grad():
            saida = det([entrada], use_inferer=usar_janela)[0]
    caixas = saida["box"].cpu().numpy()
    scores = saida["label_scores"].cpu().numpy()

    posicoes = np.array(meta["posicoes"])
    normal = np.array(meta["afim_lps"])[:3, 2]
    normal = normal / (np.linalg.norm(normal) or 1.0)
    achados = []
    for caixa, score in zip(caixas, scores):
        if float(score) < NODULO_LIMIAR:
            continue
        centro = np.array([(caixa[0] + caixa[3]) / 2, (caixa[1] + caixa[4]) / 2, (caixa[2] + caixa[5]) / 2, 1.0])
        ras = afim_final @ centro
        lps = np.array([-ras[0], -ras[1], ras[2]])
        idx = int(np.argmin(np.abs(posicoes - float(np.dot(lps, normal)))))
        diam = float(max((caixa[3] - caixa[0]) * NODULO_ESPACAMENTO[0], (caixa[4] - caixa[1]) * NODULO_ESPACAMENTO[1]))
        achados.append({"score": round(float(score), 3), "diametro_mm": round(diam, 1),
                        "imagem": meta["instancias"][idx] if meta.get("instancias") else idx + 1})
    achados.sort(key=lambda a: a["score"], reverse=True)
    maior_score = float(scores.max()) if len(scores) else 0.0

    if achados:
        top = achados[0]
        achado = ("Sugestao (modelo de pesquisa, NAO validado): %d possivel(is) nodulo(s) pulmonar(es); "
                  "mais provavel com ~%.0f mm na imagem %s (probabilidade %d%%). Nao avalia malignidade; "
                  "conferir com a radiologia." % (len(achados), top["diametro_mm"], top["imagem"],
                                                   int(round(top["score"] * 100))))
        sev, score_final = "ATENCAO", top["score"]
    else:
        achado = ("Nenhum nodulo acima do limiar (maior probabilidade %d%%). Resultado negativo da IA NAO "
                  "exclui nodulo; aguardar o laudo." % int(round(maior_score * 100)))
        sev, score_final = "INFORMATIVO", round(maior_score, 4)
    return {
        "achado": achado,
        "severidade": sev,
        "score": score_final,
        "modelo": "monai-lung_nodule_ct_detection",
        "versao_modelo": "luna16-fold0",
        "nodulos": achados[:10],
    }


def _carregar_ich():
    global _ich_model
    if _ich_model is None:
        import sys  # noqa: WPS433
        if not ICH_DIR or not os.path.isdir(ICH_DIR):
            raise RuntimeError("ICH_MODELO_DIR nao configurado ou inexistente.")
        if ICH_DIR not in sys.path:
            sys.path.insert(0, ICH_DIR)
        from inference import HemorrhageSliceModel  # noqa: WPS433
        log.info("Carregando modelo de hemorragia intracraniana de %s", ICH_DIR)
        _ich_model = HemorrhageSliceModel(local_dir=ICH_DIR, device="cpu")
    return _ich_model


def _inferir_ich(vol, meta: dict) -> dict:
    with _ich_lock:
        model = _carregar_ich()
        out = model.predict_hu_volume(vol, batch_size=ICH_BATCH, return_segmentation=False)
    serie = [float(v) for v in out["series_classification"]]
    probs = dict(zip(ICH_CLASSES, serie))
    p_any = probs["any"]
    subtipos = sorted(((k, v) for k, v in probs.items() if k != "any"), key=lambda kv: kv[1], reverse=True)
    sub_nome, sub_prob = subtipos[0]
    por_corte = [float(v) for v in out["slice_classification"][:, ICH_CLASSES.index("any")]]
    idx = max(range(len(por_corte)), key=lambda i: por_corte[i])
    corte = meta["instancias"][idx] if meta.get("instancias") else idx + 1
    pct = int(round(p_any * 100))
    if p_any >= ICH_LIMIAR:
        sev = "ATENCAO"
        achado = ("Sugestao (modelo de pesquisa, NAO validado): possivel hemorragia intracraniana "
                  "(probabilidade %d%%); padrao mais provavel %s (%d%%); maior suspeita na imagem %s. "
                  "Conferir a imagem e acionar a radiologia." % (pct, ICH_CLASSES_PT[sub_nome],
                                                                int(round(sub_prob * 100)), corte))
    else:
        sev = "INFORMATIVO"
        achado = ("Sem hemorragia acima do limiar (probabilidade %d%%). Resultado negativo da IA NAO "
                  "exclui hemorragia; aguardar o laudo." % pct)
    return {
        "achado": achado,
        "severidade": sev,
        "score": round(p_any, 4),
        "modelo": "ct-head-hemorrhage-detection",
        "versao_modelo": "914c57d",
        "probabilidades": {ICH_CLASSES_PT.get(k, "qualquer"): round(v, 3) for k, v in probs.items()},
        "imagem_maior_suspeita": corte,
    }


def _analisar_serie_sync(raw: bytes, exame: str) -> dict:
    dss = _ler_serie_zip(raw)
    vol, meta = _volume_hu(dss)
    meta_saida = {k: v for k, v in meta.items() if k not in ("instancias", "posicoes", "afim_lps")}
    tipo = _rotear_tc(meta, exame)
    base = {"exame": exame, "dicom": meta_saida, "tipo_exame": tipo}

    def sem_modelo(msg):
        return {**base, "achado": msg, "severidade": "INFORMATIVO", "score": None,
                "modelo": "roteador", "versao_modelo": VERSAO}

    if tipo == "tc_cranio":
        return {**base, **_inferir_ich(vol, meta)} if ICH_ON else sem_modelo(
            "Modelo de TC de cranio nao instalado neste servico.")
    if tipo == "tc_torax":
        return {**base, **_inferir_nodulo(vol, meta)} if NODULO_ON else sem_modelo(
            "Modelo de TC de torax nao instalado neste servico.")
    return sem_modelo("Tomografia de regiao sem modelo de IA disponivel (hoje: cranio e torax).")


@app.post("/analisar-serie")
async def analisar_serie(request: Request):
    """Serie de TC inteira (ZIP de DICOMs). Pode levar minutos em CPU."""
    from starlette.concurrency import run_in_threadpool  # noqa: WPS433
    raw = await request.body()
    exame = request.headers.get("X-Exame", "")
    if not raw:
        return JSONResponse(status_code=400, content={"erro": "Serie vazia."})
    if len(raw) > SERIE_MAX_BYTES:
        return JSONResponse(status_code=413, content={"erro": "Serie acima do limite."})
    try:
        # Fora do event loop: o /health continua respondendo durante a inferencia.
        return await run_in_threadpool(_analisar_serie_sync, raw, exame)
    except ValueError as e:
        return JSONResponse(status_code=422, content={"erro": str(e)})
    except Exception as e:
        log.warning("Falha na analise da serie de TC: %s", e)
        return JSONResponse(status_code=500, content={"erro": "Falha na inferencia: %s" % e})


@app.get("/health")
def health():
    return {"status": "ok", "modelo": "torchxrayvision" if XRV_ON else MODELO,
            "versao": VERSAO, "bundle": bool(BUNDLE_DIR), "xrv": XRV_ON, "ich": ICH_ON, "nodulo": NODULO_ON}


@app.post("/analisar")
async def analisar(request: Request):
    raw = await request.body()
    exame = request.headers.get("X-Exame", "")
    if not raw:
        return JSONResponse(status_code=400, content={"erro": "Imagem DICOM vazia."})
    if len(raw) > MAX_BYTES:
        return JSONResponse(status_code=413, content={"erro": "Estudo acima do limite."})

    try:
        meta = _ler_dicom(raw)
    except Exception as e:  # DICOM invalido nao deve derrubar o servico
        log.warning("DICOM invalido: %s", e)
        meta = {}

    if XRV_ON:
        tipo = _rotear(meta, exame)
        if tipo != "torax_rx":
            return {
                "achado": _sem_modelo_msg(tipo),
                "severidade": "INFORMATIVO",
                "score": None,
                "modelo": "roteador",
                "versao_modelo": VERSAO,
                "tipo_exame": tipo,
                "exame": exame,
                "dicom": meta,
            }
        try:
            r = _inferir_xrv(raw)
            r["exame"] = exame
            r["dicom"] = meta
            r["tipo_exame"] = tipo
            return r
        except Exception as e:
            log.warning("Falha na inferencia XRV: %s", e)
            return JSONResponse(status_code=500, content={"erro": "Falha na inferencia: %s" % e})

    inferer = _carregar_bundle()
    if inferer is None:
        # Modo stub: honesto, nao inventa achado clinico.
        return {
            "achado": "Nenhum modelo de IA ativo neste servico (modo configuracao).",
            "severidade": "INFORMATIVO",
            "score": None,
            "modelo": MODELO,
            "versao_modelo": VERSAO,
            "exame": exame,
            "dicom": meta,
        }

    # Modo bundle: aqui entra a inferencia real do MONAI Bundle.
    # A conversao do estudo recebido para o formato esperado pelo bundle e a
    # traducao da saida (mascara/score) para um achado textual sao especificas
    # de cada modelo e devem ser validadas clinicamente antes de ligar.
    resultado = _inferir_com_bundle(inferer, raw, meta)
    resultado.setdefault("modelo", MODELO)
    resultado.setdefault("versao_modelo", VERSAO)
    resultado.setdefault("exame", exame)
    return resultado


def _inferir_com_bundle(inferer, raw: bytes, meta: dict) -> dict:
    """Ponto de extensao: rodar o bundle e traduzir a saida em achado.

    Mantido explicito e isolado de proposito: cada modelo do Model Zoo tem seu
    pre/pos-processamento, e a traducao para texto e a definicao de severidade
    sao decisao clinica, nunca do codigo generico.
    """
    raise NotImplementedError(
        "Implementar a inferencia do bundle especifico escolhido e validar com a radiologia."
    )
