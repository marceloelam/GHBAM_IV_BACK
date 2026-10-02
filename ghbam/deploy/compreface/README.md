# CompreFace para reset facial

Use esta configuracao primeiro no ambiente local de desenvolvimento. Depois ela pode ir para o servidor interno e, por ultimo, para a VPS, mantendo o mesmo fluxo e mudando apenas a URL que o backend usa.

## Instalacao automatica no servidor/VPS

Copie esta pasta para qualquer lugar do servidor e rode:

```bash
chmod +x instalar-compreface.sh
./instalar-compreface.sh /root/ghbam4
```

O script cria automaticamente:

```text
/root/ghbam4/compreface
```

e copia para la:

- `docker-compose.yml`
- `.env`, se ainda nao existir
- este `README.md`

Tambem sobe o CompreFace com `docker compose up -d`.

## Desenvolvimento local

No PowerShell, dentro desta pasta:

```powershell
.\instalar-compreface.ps1 C:\ghbam4
```

Depois acesse:

```text
http://127.0.0.1:8000
```

Crie um servico do tipo **Face Verification** e copie a API key gerada.

Configure o backend local em:

`C:\Projeto\Backend\backend\ghbam\ghbam\config\ghbam-secrets.properties`

```properties
GHBAM_FACE_VERIFICATION_URL=http://127.0.0.1:8000/api/v1/verification/verify
GHBAM_FACE_API_KEY=COLE_A_API_KEY_DO_FACE_VERIFICATION
GHBAM_FACE_SIMILARITY_THRESHOLD=0.85
GHBAM_FACE_TIMEOUT_SECONDS=20
```

Reinicie o backend depois disso.

## Servidor interno

Copie esta pasta `deploy/compreface` para o servidor e suba com:

```bash
cp .env.example .env
docker compose up -d
```

Se o backend rodar no mesmo servidor do CompreFace:

```properties
GHBAM_FACE_VERIFICATION_URL=http://127.0.0.1:8000/api/v1/verification/verify
```

Se o backend rodar em outra maquina da rede interna:

```properties
GHBAM_FACE_VERIFICATION_URL=http://IP_DO_SERVIDOR_INTERNO:8000/api/v1/verification/verify
```

Nesse segundo caso, troque no `docker-compose.yml` a porta de:

```yaml
"127.0.0.1:8000:80"
```

para:

```yaml
"8000:80"
```

e restrinja essa porta no firewall para ser acessada apenas pelo backend.

## VPS

Na VPS, a recomendacao e rodar CompreFace no mesmo host do backend e manter:

```properties
GHBAM_FACE_VERIFICATION_URL=http://127.0.0.1:8000/api/v1/verification/verify
```

Assim a porta `8000` nao precisa ficar publica na internet. Se algum dia o CompreFace ficar em outra VPS, use rede privada/VPN e libere a porta apenas entre os servidores.

## Observacoes

- O CompreFace local fica acessivel apenas em `127.0.0.1:8000`.
- `SAVE_IMAGES_TO_DB=false` evita salvar as imagens enviadas para verificacao.
- O reset para `123456` so acontece quando o servico facial aprova a comparacao.
