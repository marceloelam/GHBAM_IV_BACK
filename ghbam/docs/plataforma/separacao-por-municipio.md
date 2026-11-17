# Separação de dados por município

Situação em 16/11/2026 e passos para quando entrar o segundo município.

## O que já existe

- Cada unidade (`configurarunidade`) tem `codibge` do município. Hoje: CHAPERO, HMSFX e VILA_GENY, todas 330200 (Itaguaí).
- `ConfiguracaoUnidadeService.validarMunicipioECnes` impede salvar unidade sem IBGE (6–7 dígitos) e CNES (7 dígitos).
- O JWT carrega a unidade do profissional; `UnidadeEscopoFilter` / `UnidadeCorpoInterceptor` já amarram as requisições à unidade da sessão.
- Unificação de cadastros duplicados: Correção de BAM → opção 5 (`PacienteUnificacaoService`, V232). Cadastro unificado some das buscas e não recebe BAM novo (commit 5cf1ce9f).

## Por que ainda não foi separado

Com um município, a separação gera o mesmo banco de hoje e não há como testar a troca de banco. Ativar a troca mexe no acesso a dados de todas as telas e de todos os jobs.

## Passos quando houver o segundo município

1. **Banco do novo município** criado vazio na homologação; rodar todas as migrations; copiar tabelas de apoio (CID, SIGTAP, medicamentos, permissões-modelo).
2. **Troca de banco no backend**: `quarkus.hibernate-orm.multitenant=DATABASE` + `TenantResolver` que lê a unidade do JWT → `codibge` → datasource. Jobs agendados e rotas públicas precisam de município explícito (sem requisição não há JWT).
3. **Cadastro central de pacientes** (só CPF, CNS, nome, nascimento, mãe e municípios onde há registro). Nenhum dado clínico.
4. **Histórico em outro município**: consulta somente leitura ao banco de origem via API de integrador, com registro de quem viu, quando, de qual unidade e motivo.
5. **Backup por banco** e inclusão do banco novo na rotina.
6. **Ensaio completo na homologação** com dois municípios antes de qualquer passo na produção.

## Pendências que ajudam desde já

- 7.937 CPFs com mais de um cadastro ativo (homologação, 16/11): unificar pela opção 5, com conferência humana.
- 68 entidades sem campo de unidade (levantamento de 30/10): decidir a que município pertencem antes de dividir.
- Unificação de cadastro nunca vai em migration; dado se corrige pela tela, em cada banco.

## Troca de banco implementada (16/11/2026, desligada)

- Pacote `br.com.iv.plataforma.municipio`. Município vem do endereço da página (X-Forwarded-Host, Host, Origin).
- Ligar: no config/ghbam-secrets.properties `ghbam.municipios.separacao.ativa=true`, `ghbam.municipios.bancos=330350=municipio_330350`, `ghbam.municipios.faixas=330350=10000000` (runtime). A troca do Hibernate (`quarkus.hibernate-orm.multitenant=DATABASE`) e' de BUILD: ligada so em %dev no application.properties; em producao exige build com GHBAM_MUNICIPIOS_MULTITENANT=DATABASE.
  `GHBAM_MUNICIPIOS_BANCOS=330350=municipio_330350`, `GHBAM_MUNICIPIOS_HOSTS=<endereço>=330350`.
- Token leva o claim `municipio`; token usado no endereço de outro município → 401.
- Jobs agendados rodam só no município padrão. Migrations dos bancos municipais: à mão enquanto `ghbam.municipios.flyway.migrar=false`.
- Primeira entrada no banco novo: o superusuário, pelo endereço principal, chama
  `POST /plataforma/municipios/{codibge}/ativar`. Copia as unidades daquele IBGE e o cadastro dele (servidor + servidor2, id novo).
  Depois entra pelo endereço do município e cadastra servidores, setores e salas.
- Cadastro de unidades (`/base/unidades`, POST/PUT/DELETE configuracaoUnidade): só superusuário.

## Checklist obrigatório antes de ligar na produção (auditoria de 17/11/2026)

Com `multitenant=DATABASE`, código que roda **fora de uma requisição** usa o banco padrão.

- [x] Tarefas assíncronas disparadas por requisição guardam o município antes: `ImagemIaService`,
      `IdentificacaoFacialService`, `ChatPresenceEndpoint`, `ResumoClinicoProcessoService` (`executarEm`).
- [x] Geocodificação do paciente: fica no padrão de propósito (cadastro único de pacientes mora no banco principal).
- [ ] **Jobs `@Scheduled` rodam só no banco padrão.** Antes do 2º município, cada job de dados precisa rodar em
      cada município, sem perder a transação (corpo `@Transactional` chamado pelo proxy do bean dentro de
      `executarEmNovaThread`): `ApsTransmissaoService.envioAutomatico`, `FaturamentoAutomacaoJob` (2 jobs),
      `FhirEnvioRetryJob`, `FhirDispatchService.reprocessarPendencias`, `SessaoAtivaCleanupJob`,
      `VigilanciaAlertaAutomaticoJob`, `GedReenvioArmazenamentoService`. Não precisam: `DbConnectionReaper` (infra),
      `UsuariosOnlineCleanupJob` (memória), `LimpezaUsuariosInativosScheduler` (vazio).
      Não foi feito em 17/11 de propósito: são jobs ativos na produção e o ganho só existe com o 2º contrato.
- [ ] Produção: `quarkus.hibernate-orm.multitenant=DATABASE` no BUILD + banco `ghbam_modelo_municipio` preparado lá.
