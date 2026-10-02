# Segurança operacional

## Configuração

- Segredos são fornecidos pelo arquivo externo
  `/root/ghbam4/config/ghbam-secrets.properties`.
- Em produção, esse arquivo deve pertencer a `root:root` e ter modo `0600`.
- `deploy/inicio.sh` inicia o Quarkus no diretório que contém `config/`;
  o Java lê o arquivo diretamente, sem depender de `.env` ou de `source`.
- `GHBAM_SECURITY_RBAC_ENFORCE=true` mantém a autorização por perfil ativa.
- Recuperação administrativa permanece desabilitada quando
  `ADMIN_RECOVERY_MATRICULA` ou `ADMIN_RECOVERY_PASSWORD` estiver vazio.

## Implantação e rollback

- Para trocar o backend sem quebrar os celulares já pareados, atualize somente
  o JAR: `bash ./deploy/atualizar-jar-seguro.sh /caminho/ghbam-4.0.0-runner.jar`.
  O script nunca altera `config/ghbam-secrets.properties`, valida as três
  chaves `VAPID_*` e deixa o JAR anterior em
  `ghbam-4.0.0-runner.jar.anterior` para rollback.
- Nunca copie uma pasta `config/` nova sobre a instalação de produção e nunca
  gere novas chaves VAPID em uma atualização. Elas são a identidade usada pelas
  subscriptions dos celulares já pareados.

- Validar o novo JAR em portas internas alternativas antes da troca.
- Manter uma cópia do JAR e do script de inicialização anteriores.
- Após a troca, validar:
  - endpoint público do painel de chamada retorna `200`;
  - endpoint clínico sem token retorna `401`;
  - logs mostram validação dos segredos e inicialização completa;
  - RBAC permanece com `GHBAM_SECURITY_RBAC_ENFORCE=true`.

## Rotação pendente

Remover os valores do código impede nova exposição no artefato, mas não revoga
credenciais que já existiam. A rotação deve ser feita nos respectivos sistemas:

- senha do usuário PostgreSQL;
- contas de serviço e administração LDAP;
- tokens Hugging Face, OpenRouter, Groq e Claude;
- chaves JWT, com janela controlada para expiração das sessões existentes.

Depois de cada rotação, atualizar somente o cofre/arquivo externo protegido,
reiniciar o serviço e repetir as verificações pós-implantação.
