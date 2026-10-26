-- Tabela de override de alertas do CDSS (br.com.iv.cdss): registra quando um
-- profissional decide prosseguir com a prescricao mesmo diante de um alerta
-- critico/major (alergia, interacao medicamentosa, dose maxima, bulario),
-- exigindo justificativa clinica documentada para auditoria.
-- CdssAlertaOverride.java ja espera esta tabela; sem ela o override falha em
-- runtime (tabela inexistente) na primeira tentativa real.

CREATE TABLE IF NOT EXISTS cdss_alerta_override (
    idoverride SERIAL PRIMARY KEY,
    idpaciente INTEGER,
    idbam INTEGER,
    unidade VARCHAR(30),
    alerta_codigo VARCHAR(80),
    alerta_severidade VARCHAR(20),
    justificativa TEXT,
    modulo_origem VARCHAR(80),
    usuario_nome VARCHAR(120),
    usuario_matricula VARCHAR(40),
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cdss_alerta_override_bam
    ON cdss_alerta_override (idbam, criado_em DESC);
CREATE INDEX IF NOT EXISTS idx_cdss_alerta_override_paciente
    ON cdss_alerta_override (idpaciente, criado_em DESC);
