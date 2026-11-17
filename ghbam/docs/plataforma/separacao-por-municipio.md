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
