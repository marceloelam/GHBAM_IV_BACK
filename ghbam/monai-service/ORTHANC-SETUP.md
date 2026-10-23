# Orthanc interno do GHBAM (Ubuntu, sem Docker)

Objetivo: ter um PACS próprio (Orthanc) na rede do município para alimentar o
MONAI com DICOM real, por canal **interno e autorizado** — sem tocar no PACS do
fornecedor. O PACS atual é on-premise (10.200.200.90/100); o Orthanc fica na
mesma LAN.

## 1. Instalar (apt)

```bash
sudo apt update
sudo apt install -y orthanc orthanc-dicomweb
sudo systemctl enable --now orthanc
```

## 2. Configurar

Edite `/etc/orthanc/orthanc.json` (e os arquivos em `/etc/orthanc/`):

```jsonc
{
  "Name": "GHBAM-ORTHANC",
  "HttpPort": 8042,
  "DicomAet": "GHBAM_ORTHANC",
  "DicomPort": 4242,
  "DicomWeb": { "Enable": true, "Root": "/dicom-web/" },
  "RegisteredUsers": { "ghbam": "SENHA_FORTE_AQUI" },
  "StorageDirectory": "/var/lib/orthanc/db"
}
```

```bash
sudo systemctl restart orthanc
curl -u ghbam:SENHA_FORTE_AQUI http://127.0.0.1:8042/system
```

## 3. Alimentar com as imagens (escolher uma via)

- **Recomendado — 2º destino DICOM nos equipamentos:** a biomédica/técnico do
  fabricante adiciona no tomógrafo e no RX um destino DICOM:
  `AET=GHBAM_ORTHANC`, `IP=<ip-do-orthanc>`, `Porta=4242`.
  A partir daí, todo exame novo chega **também** no Orthanc. Não encosta no PACS
  do fornecedor.
- **Alternativa — Query/Retrieve:** cadastrar o PACS atual como modality em
  `DicomModalities` e puxar estudos sob demanda (C-MOVE), se o PACS permitir.

## 4. Ligar no GHBAM

O Orthanc expõe DICOMweb em `http://<ip-do-orthanc>:8042/dicom-web/`. É esse
endereço **interno** que o GHBAM usa para buscar o estudo (nunca o host do
fornecedor). Config no backend (`.env`/ambiente do 119):

```
GHBAM_MONAI_HABILITADO=true
GHBAM_MONAI_URL=http://127.0.0.1:8000
# (quando o retrieve interno estiver ligado:)
# GHBAM_MONAI_ORTHANC_URL=http://<ip-do-orthanc>:8042/dicom-web
```

## Observações

- **Disco:** TC/RM pesam; dimensionar `StorageDirectory` com folga.
- **Sem GPU no 119:** inferência real em CPU leva minutos/exame — ok para
  piloto, não para urgência. Volume exige GPU NVIDIA.
- **Formalidade:** avisar o fornecedor do 2º destino (é direito do município,
  mas evita alegação de quebra de suporte).
- **Regulatório:** uso assistencial = SaMD (ANVISA RDC 657/2022); piloto = CEP.
