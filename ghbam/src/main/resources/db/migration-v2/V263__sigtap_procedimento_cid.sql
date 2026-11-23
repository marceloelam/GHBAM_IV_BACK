-- CIDs aceitos por procedimento, por competencia (rl_procedimento_cid.txt da Tabela
-- Unificada do DATASUS). Usada pelo motor do faturamento (CID compativel em BPA-I) e
-- pelas telas que lancam procedimento (multiprofissional, odontologia) para oferecer so
-- CID que o SUS aceita. A tabela antiga s_pacid parou na competencia 202102.
-- Somente acrescenta: nao altera nenhuma tabela existente.
CREATE TABLE IF NOT EXISTS sigtap_procedimento_cid (
    competencia      VARCHAR(6)  NOT NULL,
    co_procedimento  VARCHAR(10) NOT NULL,
    co_cid           VARCHAR(4)  NOT NULL,
    principal        BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (competencia, co_procedimento, co_cid)
);

CREATE INDEX IF NOT EXISTS ix_sigtap_procedimento_cid_proc
    ON sigtap_procedimento_cid (co_procedimento, competencia);
