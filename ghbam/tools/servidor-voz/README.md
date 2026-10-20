# Serviço de voz do painel de chamada

Programa Java separado do backend. Carrega o modelo Kokoro **uma vez** e gera a fala com
pausa de respiração nas vírgulas (~350 ms) e nos pontos (~460 ms). Escuta só em
`127.0.0.1` e não grava nada em disco nem registra o texto (contém nome de paciente).

Se ele estiver parado, o backend gera pelo modo linha de comando (sem as pausas longas) e,
por fim, o painel usa a voz do navegador.

## Arquivos necessários

- `sherpa-onnx-jvm-1.13.8.jar` e `sherpa-onnx-native-lib-<linux-x64|win-x64>-1.13.8.jar`
  (https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8)
- Modelo `kokoro-multi-lang-v1_0` (fp32; o int8 é mais lento em CPU)
  (https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2)
- Java 21+

## Ubuntu (produção)

**Jeito rápido:** copie esta pasta para o servidor e rode `sudo bash instalar-ubuntu.sh`.
O script baixa o modelo e as bibliotecas, cria o serviço `ghbam-voz` (systemd, sobe sozinho
com o servidor) e confere se ele respondeu. Pode rodar de novo sem problema.

Passo a passo manual, se preferir:

```bash
sudo mkdir -p /opt/tts/servico && cd /opt/tts
# coloque aqui: kokoro-multi-lang-v1_0/, servico/ServidorVoz.java e os dois .jar em servico/
```

`/etc/systemd/system/ghbam-voz.service`:

```ini
[Unit]
Description=GHBAM - servico de voz do painel
After=network.target

[Service]
WorkingDirectory=/opt/tts/servico
Environment=VOZ_MODELO_DIR=/opt/tts/kokoro-multi-lang-v1_0
ExecStart=/usr/bin/java --enable-native-access=ALL-UNNAMED -cp sherpa-onnx-jvm-1.13.8.jar:sherpa-onnx-native-lib-linux-x64-1.13.8.jar ServidorVoz.java
Restart=always
User=ubuntu

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now ghbam-voz
curl http://127.0.0.1:8099/saude   # deve responder "ok"
```

No backend: `GHBAM_PAINEL_VOZ_HABILITADO=true` e `KOKORO_SERVICO_URL=http://127.0.0.1:8099`.

## Windows (homologação)

```bat
cd /d C:\tts\servico
set VOZ_MODELO_DIR=C:\tts\kokoro-multi-lang-v1_0
java --enable-native-access=ALL-UNNAMED -cp "sherpa-onnx-jvm-1.13.8.jar;sherpa-onnx-native-lib-win-x64-1.13.8.jar" C:\Projeto\Backend\GHBAM_IV_BACK\ghbam\tools\servidor-voz\ServidorVoz.java
```

Variáveis opcionais: `VOZ_PORTA` (padrão 8099), `VOZ_THREADS` (padrão 4).
