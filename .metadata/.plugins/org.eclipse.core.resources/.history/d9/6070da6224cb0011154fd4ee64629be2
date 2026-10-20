-- Em dev, com quarkus.hibernate-orm.database.generation=update, o Hibernate pode
-- ja ter criado esta coluna a partir da entidade ResetSenhaSolicitacao antes do
-- Flyway rodar (mesmo cenario documentado na V135). IF NOT EXISTS torna a
-- migration idempotente nesse caso.
ALTER TABLE reset_senha_solicitacao
    ADD COLUMN IF NOT EXISTS consulta_token VARCHAR(100) UNIQUE;
