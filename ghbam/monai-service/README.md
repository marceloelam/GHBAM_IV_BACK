# Serviço MONAI — GHBAM

Serviço interno de inferência de imagem médica (MONAI/PyTorch). Segue o mesmo
modelo do CompreFace e do Piper: roda na **rede interna**, e **apenas o backend
do GHBAM** conversa com ele. Fica **desligado por padrão**.

> A IA **sugere**, nunca diagnostica. O achado volta ao GHBAM, que exige a
> **ciência e a conduta do médico** antes de qualquer efeito clínico.

## Instalação em produção (um comando)

No servidor (Ubuntu, sem GPU), de dentro desta pasta:

```bash
sudo ./instalar.sh --xrv
```

**Em uso hoje: só raio-X de tórax.** A IA de tomografia (`--nodulo`, `--ich`,
seções abaixo) está pronta mas **desligada**: no backend só liga com
`ghbam.monai.tc.habilitado=true`. Desligada, TC segue o fluxo de 1 imagem e o
serviço responde "Tomografia ainda não tem modelo".

Instala o Python, o ambiente, os modelos escolhidos (pesos em versão **fixa**,
conferidos por SHA-256) e o serviço systemd `ghbam-monai`, com limite de
memória (12 GB) e prioridade de CPU baixa para nunca derrubar o servidor.
Rodar de novo **atualiza** (app.py novo, reaproveita o que já foi baixado).
Modelo não listado fica desligado. Se o backend Quarkus estiver em outro
servidor, use `--host <IP interno>`.

Depois, no GHBAM, tela **/unidades**: IA de Imagem = Sim, URL do serviço e do
Orthanc, salvar e **Testar conexão**: mostra se o serviço e o Orthanc respondem
e quais modelos estão ligados (rota `imagem-ia/status`, só `kadm`).

Se o arquivo for copiado do Windows e der `$'\r': command not found`:
`sed -i 's/\r$//' instalar.sh`.

O processo fica **fora** do Quarkus de propósito: inferência 3D ocupa vários GB
e minutos de CPU; isolada, o pior caso é "IA indisponível", não o backend
do hospital fora do ar.

## Modos

- **stub** (padrão): sem modelo carregado. Lê os metadados do DICOM e devolve um
  achado `INFORMATIVO` dizendo que nenhum modelo está ativo. Serve para testar o
  fluxo ponta a ponta (GHBAM → serviço → GHBAM) sem GPU nem modelo.
- **bundle**: carrega um [MONAI Bundle](https://monai.io/model-zoo.html) do
  Model Zoo (`MONAI_BUNDLE_DIR`) e roda a inferência real. A função
  `_inferir_com_bundle` em `app.py` é o ponto de extensão e **precisa de
  validação clínica** antes de ser ligada.

## Subir (stub)

```bash
cd monai-service
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000
# ou: docker build -t ghbam-monai . && docker run -p 8000:8000 ghbam-monai
```

Teste: `curl http://localhost:8000/health`

## Ligar no backend

Em `application.properties` (ou variáveis de ambiente):

```
ghbam.monai.habilitado=true
ghbam.monai.url=http://10.200.200.x:8000
# ghbam.monai.unidades=HMSFX   # piloto por sigla; vazio = todas
```

Sem `ghbam.monai.url`, o módulo continua desligado mesmo com `habilitado=true`.

## Endpoints

- `GET /health` — status e se há bundle carregado.
- `POST /analisar` — corpo = bytes DICOM (`application/octet-stream`); header
  `X-Exame` = descrição. Resposta JSON: `achado`, `severidade`
  (`INFORMATIVO|ATENCAO|CRITICO`), `score`, `modelo`, `versao_modelo`.
- `POST /analisar-serie` — corpo = ZIP da série de TC (`application/zip`, o
  `/series/{id}/archive` do Orthanc). Exige série axial; responde 422 se não for.
  Roda fora do event loop (o `/health` segue respondendo) e uma TC por vez.

## Tomografia de crânio: hemorragia intracraniana

Fluxo: botão **IA** no PACS → backend acha as séries do AccessionNumber no
Orthanc → escolhe a axial de partes moles (descarta scout, coronal/sagital,
osso/pulmão, menos de 20 ou mais de 600 cortes) → confere o PatientID → grava a
análise como `PENDENTE` e processa **em segundo plano** → o selo "Imagem: IA" do
atendimento mostra o resultado. Clique repetido devolve a análise em andamento.
`PENDENTE` com mais de 30 min aparece como erro (processamento perdido).

Modelo: [`ianpan/ct-head-hemorrhage-detection`](https://huggingface.co/ianpan/ct-head-hemorrhage-detection)
(licença MIT dos pesos; treinado no RSNA ICH 2019, cujos termos de uso valem à
parte). Detecta hemorragia (qualquer) e 5 subtipos. **Modelo de pesquisa:**
negativo NÃO exclui hemorragia.

Instalar (no 119, Ubuntu; no Windows use venv em caminho curto):

```bash
mkdir -p /opt/ghbam-ich && cd /opt/ghbam-ich
# pesos + inference.py (≈150 MB): classifier/, sequence/, segmentation/fold0..4
huggingface-cli download ianpan/ct-head-hemorrhage-detection --local-dir .
pip install timm safetensors opencv-python-headless einops \
  "git+https://github.com/i-pan/ct-head-hemorrhage-detection.git@914c57d"   # pacote skp
```

Variáveis do serviço (systemd):

```
ICH_HABILITADO=true
ICH_MODELO_DIR=/opt/ghbam-ich
ICH_LIMIAR=0.5        # probabilidade de "qualquer hemorragia" para ATENCAO
ICH_BATCH=4
```

Backend: `ghbam.monai.timeout.tc.seconds` (padrão 1500).

## Tomografia de tórax: nódulo pulmonar

Modelo: MONAI Model Zoo [`lung_nodule_ct_detection`](https://huggingface.co/MONAI/lung_nodule_ct_detection)
(Apache-2.0; RetinaNet 3D treinado no LUNA16/LIDC-IDRI, dados CC-BY). A rede é
montada no `app.py` com o pacote `monai` do PyPI; do bundle só se usa
`models/model.pt`, carregado com `weights_only=True`. Reamostra para
0,703 × 0,703 × 1,25 mm. **Não avalia malignidade**; negativo NÃO exclui nódulo.

```bash
mkdir -p /opt/ghbam-monai/modelos/lung_nodule_ct_detection/models
cd /opt/ghbam-monai/modelos/lung_nodule_ct_detection
curl -L -o models/model.pt https://huggingface.co/MONAI/lung_nodule_ct_detection/resolve/main/models/model.pt
sha256sum models/model.pt   # b5e79231466adae93a6fe8e8594029e9add142914e223b879aa0343bb2402d01
/opt/ghbam-monai/.venv/bin/pip install monai
```

```
NODULO_HABILITADO=true
NODULO_MODELO_DIR=/opt/ghbam-monai/modelos/lung_nodule_ct_detection
NODULO_LIMIAR=0.5
```

Roteamento da TC (`_rotear_tc`): texto do exame no GHBAM + descrições do
DICOM. Crânio (sem face/seios/órbita) → hemorragia; tórax/pulmão → nódulo;
o resto → "sem modelo".

## Avisos

- **Entrada de imagens:** o GHBAM hoje não recebe DICOM (só abre o visualizador
  externo). Este serviço só é útil quando houver uma via de DICOM — DICOMweb do
  PACS ou um segundo destino do tomógrafo para um Orthanc interno.
- **Regulatório:** uso assistencial de IA é SaMD e exige registro na ANVISA
  (RDC 657/2022); piloto de pesquisa exige CEP. Os modelos do Model Zoo são de
  pesquisa e **não têm validação clínica**.
- **Não expor** este serviço fora da rede interna.
