# Auditoria de maturidade do backend — 29-07-2026

## Conclusão

O backend está mais estável e possui uma base de produção melhor definida, mas
ainda não deve ser classificado como plenamente maduro. O estado atual é
**operacional com ressalvas controladas**.

## Concluído

- Quarkus 3.31.2, Flyway 11.20.3 e PostgreSQL JDBC 42.7.9.
- Migrações V69/V70 validadas em cópia local do banco.
- Compatibilidade de banco verificada para PostgreSQL 16.1 e 18.1.
- 53 testes aprovados e build limpo concluído.
- Endpoints GET não recebem mais corpo de requisição.
- Dependências redundantes de Swagger Parser, OpenAPI Generator, SLF4J e
  Commons Logging removidas.
- Arquivo `NBioBSPJNI.jar` inválido removido: o conteúdo era HTML, não bytecode.
- Bean Validation declarada explicitamente pela extensão oficial do Quarkus.
- `AppSecrets` preservado, lendo segredos de arquivo externo, propriedades do
  processo ou ambiente; `config/ghbam-secrets.properties` permanece ignorado.
- Token Zamed retirado do arquivo versionado.
- Cabeçalhos defensivos aplicados às respostas JAX-RS.
- Runtime final validado em `127.0.0.1`, PostgreSQL local e `/q/health` = UP.
- CORS local validado para `http://localhost:5173`.
- Cobertura JaCoCo medida e protegida por baseline de 4% no lifecycle `verify`.
- Pipeline automatizado gera build, executa testes, verifica cobertura, procura
  tokens de IA versionados e preserva relatório/artefato por 14 dias.

## Ações obrigatórias antes do próximo deploy

1. Rotacionar a credencial Zamed que já esteve versionada.
2. Definir `ZAMED_AUTENTICACAO` em `config/ghbam-secrets.properties` ou no
   ambiente de cada servidor.
3. Manter um arquivo de segredos específico por servidor; não copiar o arquivo
   de produção para desenvolvimento.
4. Criar backup e ensaiar restauração antes de executar migrações no banco
   original.
5. Executar smoke test autenticado após cada implantação.

## Pendências de maturidade

- Elevar a cobertura atual de 4,39% e ampliar testes de integração/E2E
  autenticados. Segurança está em 46,98% e AppSecrets em 66,41%.
- Concluir auditoria OWASP com chave NVD. Sem chave, a ferramenta iniciou a
  atualização de 371.198 registros e foi interrompida por duração impraticável.
- Tratar gradualmente os avisos Lombok de `equals/hashCode` nas entidades.
- Migrar usos de `@Temporal` e a propriedade Hibernate de geração de schema
  marcada como obsoleta.
- Decidir a estratégia oficial do SDK biométrico. O adaptador reflexivo continua
  seguro sem SDK; para ativar hardware será necessário obter o JAR legítimo do
  fornecedor e distribuí-lo por repositório privado/verificado.
- Automatizar pipeline CI com build, testes, análise de dependências e artefato
  reproduzível.

## Implantação nos dois servidores

O mesmo JAR pode ser usado nos servidores PostgreSQL 14–18 e 18, desde que:

- cada servidor tenha seu próprio `config/ghbam-secrets.properties`;
- certificado TLS e caminhos de relatórios existam no host;
- o usuário do banco tenha permissão para as migrações previstas;
- backup, health check e rollback sejam executados por servidor.

Nenhuma publicação, push ou alteração em produção foi realizada nesta auditoria.
