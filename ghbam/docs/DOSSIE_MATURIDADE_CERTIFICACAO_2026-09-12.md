# Dossiê de Maturidade e Certificação — GHBAM IV

**Data:** 2026-09-12
**Escopo:** consolida achados verificados diretamente no código (frontend + backend, ambos os repositórios), corrige afirmações desatualizadas de auditorias anteriores, e organiza os gaps reais num roadmap por esforço. Não é uma peça de marketing — cada linha "forte" abaixo tem arquivo/commit citável; cada "gap" tem o motivo técnico.

Documentos-fonte anteriores (não substituídos, complementados): `overview.md`, `modules.md`, `security-compliance.md`, `backend-security-rbac.md`, `endpoint-permission-inventory.md`, `comparativo-maturidade-mercado.md`, `fhir-rnds-map.md`, `roadmap.md` (todos no repo do frontend, `docs/`) e `AUDITORIA_MATURIDADE_BACKEND_2026-07-29.md`, `SEGURANCA_OPERACIONAL.md` (neste repo).

---

## 1. Comparativo competitivo — corrigido com evidência de código (12/09/2026)

| Área | Avaliação de mercado recebida | Estado real verificado hoje | Evidência |
|---|---|---|---|
| PA/urgência municipal | Forte | Confirmado forte | Fluxo BAM/triagem/NIR em produção há anos |
| SUS/faturamento público | Forte, menos homologado | Confirmado forte | V91–V99: competência, crítica, SIGTAP, AIH/APAC/RAAS/BPA, exporters dedicados |
| PEP clínico | Bom | Bom, com CDSS ativo | `CdssRulesEngine` (interação, dose máx., NEWS2), `PrescricaoEstruturada` |
| Farmácia/estoque | Bom | Bom | `PharmaciaDispensacaoService`, permutas, lotes |
| **Ciclo fechado de medicação** | **Parcial** | **Muito mais completo do que reportado**: prescrição→CDSS→dispensação→aprazamento/checagem→evento adverso estruturado (NSP, hoje) | Ver seção 2 |
| LIS/RIS/PACS | Parcial/bom | **Parcial confirmado** — laboratório e RX têm fluxo próprio; não há integração DICOM/PACS formal nem HL7 v2 para equipamento de terceiro | Gap real, ver seção 4 |
| **Interoperabilidade FHIR/HL7/RNDS** | "Em evolução" | **Produção-pronta, aguardando só credencial administrativa** — não é mais andaime | Ver seção 3 |
| Segurança/RBAC | Bom/forte | Confirmado forte, com histórico de regressão conhecido | `RoutePermissionCatalog` (~90 prefixos), `AuditRequestFilter` audita toda mutação + leitura clínica sensível |
| BI/gestão | Bom | Sem verificação nova nesta sessão | — |
| UX/mobile | Irregular pelo tamanho | Sem verificação nova nesta sessão | — |
| Certificações/compliance | Não evidenciado | **Confirmado: não evidenciado** — nenhum processo SBIS/CFM em andamento identificável no repositório | Gap real, decisão de negócio, não técnica |
| **IA clínica (RAG, resumo, CDSS)** | "Promissor" | Módulo real e em uso, com **governança melhor do que o esperado** (prompt anti-alucinação, auditoria por chamada, disclaimers, agora travado por teste) — falta só tabela de auditoria estruturada e política formal por escrito | Ver seção 5 |
| **Observabilidade técnica** | "Parece interna" | **Confirmado: gap real** — zero Micrometer/Prometheus/OpenTelemetry no `pom.xml` | Ver seção 4 |

---

## 2. Ciclo de medicação — estado real (corrige a leitura de "parcial")

Verificado em 12/09/2026, ponta a ponta:

1. **Prescrição estruturada** → `PrescricaoEstruturada.jsx` + `CdssRulesEngine` (interação medicamentosa crítica, dose máxima diária, exigência de peso pediátrico/nefrotóxico, checagem cruzada com bulário, NEWS2).
2. **Farmácia/dispensação** → `PharmaciaDispensacaoService`/`PharmaciaDispensacaoResource`.
3. **Administração/checagem de enfermagem** → `PrescricaoAprazamentoChecagemService`/`Controller` (aprazamento por horário, checagem).
4. **Evento adverso** → módulo `NspEvento` (Núcleo de Segurança do Paciente, ativado em 02/09/2026), agora com campos estruturados de medicamento/dose/via/prescrição vinculada e causalidade (escala Naranjo/OMS-UMC adaptada), migration V154 (12/09/2026). Integração com NOTIVISA (`notivisaNotificado`/`notivisaProtocolo`) já existente.

**O que ainda falta para "ciclo fechado" no sentido HIMSS**: rastreabilidade automática de lote/validade (SNCM/ANVISA) na dispensação, e cálculo de dose automática por peso/função renal na prescrição (hoje o CDSS *exige* peso, mas não calcula a dose sozinho). Esforço estimado: semanas, não meses — a espinha dorsal já existe.

---

## 3. FHIR/RNDS — estado real (corrige "andaime preparatório")

Commits de 02/09/2026 (`8ebf119`, `1cec76e`) implementaram motor transacional real:

- `FhirDispatchService` — HTTP client, fila (`FhirEnvioFila`, migration V145), processamento assíncrono via `@Observes(TransactionPhase.AFTER_SUCCESS)`, retry exponencial (`FhirEnvioRetryJob`).
- `FhirSumarioAltaMapperService` — monta `Bundle` FHIR R4 real (`Composition`+`Patient`+`Encounter`+`Condition`+`Provenance`), não apenas administrativo.
- Disparo **automático** na assinatura do sumário de alta (`AssinaturaClinicaService.assinar()`), atendendo à exigência transacional do RISAH (Portaria SMS-SP 118/2026) e ao Decreto 12.560/2025.
- Testes: `FhirDispatchServiceTest`, `FhirSumarioAltaMapperServiceTest`.

**Gap real remanescente**: `FhirEnvioDestino` trava em `NAO_CONFIGURADO` até existir uma linha real com endpoint + credencial de produção. Suporta `BEARER_TOKEN`/`BASIC`/`API_KEY_HEADER` genéricos — **não é específico de certificado ICP-Brasil/mTLS da RNDS**. Isso é o item que falta para "certificável": obter credencial de homologação com o DATASUS e, possivelmente, adaptar o cliente HTTP para mTLS. Esforço: dias de código + tempo de homologação externa (fora do controle do time).

---

## 4. Gaps reais confirmados (sem code fix disponível hoje)

| Gap | Confirmado como | Esforço estimado |
|---|---|---|
| Observabilidade (Prometheus/Micrometer/tracing/SLO) | Zero dependência de métricas no `pom.xml` — hoje só há logs (`java.util.logging`) e uma auditoria de acesso (`AuditRequestFilter`), sem série temporal nem alerta automático | 1–2 semanas: `quarkus-micrometer-registry-prometheus` + dashboards Grafana básicos por módulo crítico (login, faturamento, CDSS) |
| LIS/RIS/PACS (DICOM/HL7 v2) | Laboratório/RX têm tela e fluxo próprios, sem protocolo padrão de integração com equipamento/LIS de terceiro | Meses — depende de qual analisador/PACS o cliente usa; é integração ponto-a-ponto, não algo genérico de codar uma vez |
| Certificação SBIS/CFM | Nenhum processo identificável no repositório | Decisão de negócio + meses de processo com a certificadora, não é tarefa de código |
| Credencial real RNDS/DATASUS | Motor pronto, falta só a credencial (seção 3) | Depende de homologação externa |

---

## 5. Governança de IA clínica — melhor do que a leitura inicial (corrigido em 12/09/2026)

O GHBAM tem IA clínica funcional (`rag2`, proxy HuggingFace/Groq/OpenRouter para resumo clínico, "Como está o paciente?", `CdssRulesEngine`). Uma leitura mais profunda de `Rag2ProntuarioService.java` mostrou que a governança já é mais madura do que a primeira passada deste dossiê registrou:

- **Já existe e confirmado**: prompt de sistema (`SYSTEM_PROMPT`) com regras explícitas — proíbe inventar informação, proíbe diagnóstico novo, exige resposta literal de recusa ("Não encontrei essa informação no prontuário do paciente") quando não há evidência no contexto, declara explicitamente que não substitui a avaliação do profissional. Toda resposta carrega aviso de segurança (`AVISO_SEGURANCA`). Toda chamada é auditada (`registrarAuditoria`, com status por caminho: negado, sem-dados, erro-ia, ok, etc.) e o relatório consolidado carrega hash SHA-256 de verificação. Contexto é restrito por perfil (clínico vs. administrativo). Override do CDSS exige justificativa ≥15 caracteres + matrícula.
- **Travado com teste nesta sessão**: `Rag2ProntuarioServiceTest` (3 testes) fixa essas cláusulas do prompt como contrato — se alguém editar o `SYSTEM_PROMPT` e remover a proteção anti-alucinação sem perceber, o teste quebra antes de ir para produção.
- **Falta de verdade**: auditoria de IA usa a tabela de log genérica (`LogDto`/`LogService`, texto livre), não uma tabela dedicada com colunas estruturadas (pergunta/resposta/modelo/hash-contexto/status) fácil de consultar para relatório de compliance; e não há como testar automaticamente se o modelo *de fato* obedece o prompt em produção (isso depende do comportamento real do LLM, só o caminho determinístico "contexto vazio → recusa sem chamar IA" é testável, e já está coberto). Política formal por escrito (documento institucional, não código) ainda não existe como artefato separado do prompt.
- Esforço remanescente: 3-5 dias para a tabela de auditoria estruturada; a política formal é documento, não código — pode sair já.

---

## 6. Como usar este dossiê

Este documento é para consolidar o discurso de maturidade em auditoria/venda/certificação, não para prometer prazo. Onde este dossiê diz "esforço estimado", é uma ordem de grandeza para priorização interna — não um compromisso de entrega. Antes de qualquer apresentação externa (investidor, prefeitura, certificadora), reconferir contra o código na data da apresentação: este repositório tem histórico de trabalho grande acumulado sem commit por dias (ver `git log -1 --format=%ci` antes de citar qualquer número deste documento como atual).
