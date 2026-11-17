# API da plataforma — guia de integração

Referência completa das rotas: [`openapi.json`](openapi.json) (OpenAPI 3, exportado do backend).
Este guia cobre o que a especificação não explica: login, unidade da sessão e erros.

## 1. Onde ler a especificação

| Ambiente | Como acessar |
|---|---|
| Desenvolvimento / homologação | `GET /q/openapi?format=json` e Swagger em `/q/swagger-ui` |
| Produção | **fechado** (`q-docs` = deny). Use o `openapi.json` desta pasta |

Para atualizar o arquivo, com o backend de homologação no ar:

```bash
curl -s "http://<host>:8080/q/openapi?format=json" -o docs/api/openapi.json
```

### Versão

Integradores devem chamar sempre com o prefixo **`/v1`** (ex.: `POST /v1/auth/cliente/token`, `GET /v1/pacientes/...`). Hoje `/v1/x` responde igual a `/x`, com as mesmas regras; quando uma rota mudar de forma incompatível, a `v1` continua respondendo como antes. As rotas sem versão são do frontend do próprio sistema e podem mudar sem aviso.

## 2. Autenticação

1. `POST /auth/login` → devolve `{ "token": "<JWT>" , ... }`.
2. Toda chamada leva `Authorization: Bearer <JWT>`.
3. O token expira (1 h). Ao receber `401`, faça login de novo.

O JWT carrega a matrícula, as permissões (`groups`) e a **unidade da sessão** (claim `unidade`).

| Rota | Uso |
|---|---|
| `GET /auth/minhas-unidades` | Unidades em que o profissional pode trabalhar |
| `POST /auth/trocar-unidade` corpo `{"unidade":"SIGLA"}` | Emite token novo para outra unidade |

### Sistemas integradores (sem login de pessoa)

Laboratório, RNDS, outro município etc. usam uma **credencial de sistema**, cadastrada pelo superusuário:

| Rota | Quem | Uso |
|---|---|---|
| `POST /plataforma/clientes-api` corpo `{"nome","unidade","permissoes":"kenfermagem,prealizarexames"}` | superusuário | Cria o cliente. Devolve `clientId` e `clientSecret` **uma única vez** |
| `GET /plataforma/clientes-api` | superusuário | Lista (sem segredo), com último uso |
| `DELETE /plataforma/clientes-api/{clientId}` | superusuário | Desativa (vale em até 1 minuto) |
| `POST /auth/cliente/token` corpo `{"client_id","client_secret"}` | o sistema | Devolve `access_token` (JWT de 1 h) |

O token do sistema tem a **unidade e as permissões do cadastro** e passa pelas mesmas regras de RBAC e de unidade de um profissional. Permissões aceitas: só `k*` e `p*`; superusuário nunca. O segredo é guardado só como hash; tentativas erradas caem no mesmo limitador do login (429).

## 3. Escopo por unidade

Um único banco atende várias unidades. A regra é:

- **Consultar** dados de outra unidade (BAM, exames, histórico do paciente) é permitido e fica registrado no log.
- **Alterar** exige que o registro seja da unidade da sessão.
- O **cadastro do paciente** é um só para todas as unidades: qualquer unidade edita, e a alteração é auditada com a unidade e os campos alterados.

O backend confere, para perfis de uma unidade:

| Situação | Resultado |
|---|---|
| Parâmetro `unidade`/`sigla`/`unid`/`yunidade` de outra unidade | 403 `UNIDADE_FORA_DA_SESSAO` |
| `TODAS` fora do histórico do paciente | 403 `UNIDADE_FORA_DA_SESSAO` |
| Parâmetro de unidade vazio ou ausente* | 403 `UNIDADE_FORA_DA_SESSAO` |
| Gravar em BAM (`{bam}`, `{idbam}` ou id de registro clínico) de outra unidade | 403 `UNIDADE_FORA_DA_SESSAO` |
| Corpo de POST/PUT com `unidade` de outra unidade | 403 `UNIDADE_FORA_DA_SESSAO` |
| Sessão sem unidade | 403 `SESSAO_SEM_UNIDADE` (exceto `/auth/*` e `/seguranca/*`) |

\* Rotas marcadas com `@UnidadePadraoSessao` (APS, resumo de alta, documentos assistenciais) aceitam a unidade ausente e usam a da sessão.

**Recomendação:** sempre envie a unidade da sessão explicitamente.

Perfis que trabalham entre unidades não passam por essas checagens: superusuário, administração (`kadm`), NIR, faturamento e BI executivo.

### Vários municípios no mesmo banco

O município de cada unidade é o `municipiopadrao` de `configurarunidade` (preencha ao cadastrar uma unidade nova).

- Administração, NIR, faturamento e BI cruzam unidades **só dentro do próprio município**. Unidade de outro município → 403 `MUNICIPIO_FORA_DA_SESSAO`.
- Com mais de um município cadastrado, `TODAS`, unidade vazia ou ausente também dão 403 para esses perfis (exceto no histórico do paciente e nas rotas `@UnidadePadraoSessao`). Com um município só, nada muda.
- Gravar em BAM de outro município → 403. **Consultar** histórico e BAM de outro município é permitido e registrado (continuidade do cuidado).
- Unidade sem município, com vários municípios cadastrados, conta como "outro município".
- Só o **superusuário** enxerga todos os municípios.

## 4. Erros

| HTTP | Corpo | Significado |
|---|---|---|
| 401 | vazio | Sem token, token inválido ou expirado |
| 403 | `{"erro":"Permissao insuficiente"}` | O perfil não tem a permissão da rota (RBAC) |
| 403 | `{"erro":"UNIDADE_FORA_DA_SESSAO"}` | A operação é de outra unidade |
| 403 | `{"erro":"SESSAO_SEM_UNIDADE"}` | O token não tem unidade: troque de unidade |
| 403 | `{"erro":"MUNICIPIO_FORA_DA_SESSAO"}` | A operação é de outro município |

## 5. Chaves de segurança (operação)

Ficam em `config/ghbam-secrets.properties`. Com `false`, a regra só registra no log `UNIDADE-SHADOW`.

| Chave | Regra |
|---|---|
| `ghbam.security.unidade.bloquear` | Unidade de outra em parâmetro |
| `ghbam.security.unidade.bam.bloquear` | Gravar em BAM de outra unidade |
| `ghbam.security.unidade.vazia.bloquear` | Unidade vazia ou ausente |
| `ghbam.security.unidade.corpo.bloquear` | Unidade de outra no corpo |
| `ghbam.security.unidade.sem-sessao.bloquear` | Sessão sem unidade |
| `ghbam.security.municipio.bloquear` | Perfil multiunidade fora do próprio município (padrão `true`) |

Antes de ligar uma chave em produção, leia no log as linhas `UNIDADE-SHADOW decision=...` com `multiunidade=false`: cada uma é uma tela que seria bloqueada.
