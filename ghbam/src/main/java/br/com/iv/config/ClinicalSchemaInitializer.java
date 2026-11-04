package br.com.iv.config;

import java.sql.Connection;
import java.sql.Statement;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class ClinicalSchemaInitializer {

    private static final Logger LOG = Logger.getLogger(ClinicalSchemaInitializer.class);

    @Inject
    AgroalDataSource dataSource;

    @ConfigProperty(name = "ghbam.clinical-schema-initializer.enabled", defaultValue = "true")
    boolean enabled;

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            LOG.info("ClinicalSchemaInitializer desativado por configuracao; esquema sera apenas validado.");
            return;
        }

        // Sessoes ativas primeiro: garante o indice unico (rede de seguranca contra
        // dois logins com a mesma matricula) sem depender dos ALTER TABLE pesados abaixo,
        // que exigem lock exclusivo e podem bloquear a cadeia de inicializacao.
        criarTabelaSessoesAtivas();
        criarTabelasPasskey();
        criarTabelaChatConversasLimpas();
        criarTabelaProntuarioClinico();
        criarTabelaPrescricaoEstruturada();
        criarTabelaCicloMedicacao();
        criarTabelaPrescricaoAprazamentoChecagem();
        criarTabelaAssinaturaClinica();
        criarTabelaConformidadePendenciaBaixa();
        criarTabelaProcedimentoSusValorUnidade();
        criarTabelaSaeEnfermagem();
        criarTabelaCdssAlertaOverride();
        criarTabelasOdontologia();
        ajustarColunasTextoEvolucao();
    }

    private void criarTabelasPasskey() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS passkey_credential (
                      id BIGSERIAL PRIMARY KEY,
                      matricula VARCHAR(15) NOT NULL,
                      nome_dispositivo VARCHAR(120),
                      credential_id TEXT NOT NULL UNIQUE,
                      user_handle TEXT NOT NULL,
                      public_key_cose TEXT NOT NULL,
                      signature_count BIGINT NOT NULL DEFAULT 0,
                      ativa BOOLEAN NOT NULL DEFAULT TRUE,
                      discoverable BOOLEAN NOT NULL DEFAULT FALSE,
                      backup_eligible BOOLEAN NOT NULL DEFAULT FALSE,
                      backed_up BOOLEAN NOT NULL DEFAULT FALSE,
                      criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      ultimo_uso_em TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_passkey_credential_matricula
                    ON passkey_credential (matricula)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_passkey_credential_user_handle
                    ON passkey_credential (user_handle)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS passkey_challenge (
                      id BIGSERIAL PRIMARY KEY,
                      request_id VARCHAR(80) NOT NULL UNIQUE,
                      tipo VARCHAR(20) NOT NULL,
                      matricula VARCHAR(15),
                      request_json TEXT NOT NULL,
                      expira_em TIMESTAMP NOT NULL,
                      criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_passkey_challenge_expira
                    ON passkey_challenge (expira_em)
                    """);
            statement.execute("""
                    ALTER TABLE servidor
                    ADD COLUMN IF NOT EXISTS celular_pareado_ativo BOOLEAN NOT NULL DEFAULT FALSE
                    """);
            statement.execute("""
                    ALTER TABLE servidor
                    ADD COLUMN IF NOT EXISTS celular_pareado_nome VARCHAR(120)
                    """);
            statement.execute("""
                    ALTER TABLE servidor
                    ADD COLUMN IF NOT EXISTS celular_pareado_em TIMESTAMP
                    """);
        } catch (Exception e) {
            LOG.warn("Erro ao inicializar tabelas de passkey/WebAuthn", e);
        }
    }

    private void criarTabelasOdontologia() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE servidor
                    ADD COLUMN IF NOT EXISTS dentista BOOLEAN NOT NULL DEFAULT FALSE
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS odontologia_prontuario (
                      idprontuario SERIAL PRIMARY KEY,
                      idpaciente INTEGER NOT NULL UNIQUE,
                      criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    ALTER TABLE odontologia_prontuario
                    ADD COLUMN IF NOT EXISTS numero_prontuario VARCHAR(20)
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uk_odontologia_prontuario_numero
                    ON odontologia_prontuario (numero_prontuario)
                    WHERE numero_prontuario IS NOT NULL
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS odontologia_atendimento (
                      idatendimento SERIAL PRIMARY KEY,
                      idpaciente INTEGER NOT NULL,
                      idbam INTEGER,
                      unidade VARCHAR(20),
                      status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
                      tipo_odontograma VARCHAR(20) NOT NULL DEFAULT 'INICIAL',
                      denticao VARCHAR(20) NOT NULL DEFAULT 'PERMANENTE',
                      queixa_principal TEXT,
                      historia_doenca_atual TEXT,
                      anamnese_json TEXT,
                      exame_geral TEXT,
                      exame_extraoral TEXT,
                      exame_intraoral TEXT,
                      observacoes TEXT,
                      criado_por VARCHAR(120),
                      criado_por_matricula VARCHAR(40),
                      criado_em TIMESTAMP,
                      atualizado_em TIMESTAMP
                    )
                    """);
            statement.execute("""
                    ALTER TABLE odontologia_atendimento
                    ADD COLUMN IF NOT EXISTS idprontuario INTEGER
                    """);
            statement.execute("""
                    ALTER TABLE odontologia_atendimento
                    ADD COLUMN IF NOT EXISTS evolucoes_json TEXT
                    """);
            statement.execute("""
                    ALTER TABLE odontologia_atendimento
                    ADD COLUMN IF NOT EXISTS periodontais_json TEXT
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS odontograma_registro (
                      idregistro SERIAL PRIMARY KEY,
                      idatendimento INTEGER NOT NULL,
                      numero_dente INTEGER NOT NULL,
                      face VARCHAR(24) NOT NULL,
                      condicao VARCHAR(30) NOT NULL,
                      descricao TEXT,
                      CONSTRAINT fk_odontograma_atendimento
                        FOREIGN KEY (idatendimento) REFERENCES odontologia_atendimento(idatendimento)
                    )
                    """);
            statement.execute("""
                    DO $$
                    BEGIN
                      IF EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'odontograma_registro'
                          AND column_name = 'numerodente'
                      ) AND NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'odontograma_registro'
                          AND column_name = 'numero_dente'
                      ) THEN
                        ALTER TABLE odontograma_registro
                          RENAME COLUMN numerodente TO numero_dente;
                      END IF;
                    END
                    $$;
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_odontologia_atendimento_paciente
                    ON odontologia_atendimento (idpaciente, criado_em DESC)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_odontologia_atendimento_bam
                    ON odontologia_atendimento (idbam, status)
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uk_odontograma_registro_face
                    ON odontograma_registro (idatendimento, numero_dente, face)
                    """);
            LOG.info("Schema de Odontologia verificado.");
        } catch (Exception e) {
            LOG.error("Falha ao verificar schema de Odontologia.", e);
        }
    }

    private void criarTabelaProntuarioClinico() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS prontuario_clinico_item (
                      iditem SERIAL PRIMARY KEY,
                      idpaciente INTEGER NOT NULL,
                      idbam INTEGER,
                      unidade VARCHAR(20),
                      tipo VARCHAR(30) NOT NULL,
                      status VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
                      codigo VARCHAR(40),
                      titulo VARCHAR(180),
                      descricao TEXT,
                      observacao TEXT,
                      criado_por VARCHAR(120),
                      criado_por_matricula VARCHAR(40),
                      atualizado_por VARCHAR(120),
                      atualizado_por_matricula VARCHAR(40),
                      criado_em TIMESTAMP,
                      atualizado_em TIMESTAMP
                    )
                    """);
            statement.execute("ALTER TABLE prontuario_clinico_item ADD COLUMN IF NOT EXISTS idbam INTEGER");
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_prontuario_clinico_item_paciente
                    ON prontuario_clinico_item (idpaciente)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_prontuario_clinico_item_bam
                    ON prontuario_clinico_item (idbam)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_prontuario_clinico_item_tipo
                    ON prontuario_clinico_item (idpaciente, tipo, status)
                    """);
            LOG.info("Schema de prontuario clinico verificado.");
        } catch (Exception e) {
            LOG.error("Falha ao verificar schema de prontuario clinico.", e);
        }
    }

    private void criarTabelaPrescricaoEstruturada() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS prescricao_item_estruturado (
                      iditem SERIAL PRIMARY KEY,
                      idprescricao INTEGER,
                      idpaciente INTEGER,
                      idbam INTEGER NOT NULL,
                      unidade VARCHAR(20),
                      status VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
                      tipo_item VARCHAR(40) NOT NULL DEFAULT 'MEDICAMENTO',
                      medicamento VARCHAR(180) NOT NULL,
                      dose VARCHAR(80),
                      via VARCHAR(60),
                      frequencia VARCHAR(120),
                      duracao VARCHAR(80),
                      observacao TEXT,
                      prescrito_por VARCHAR(120),
                      prescrito_por_matricula VARCHAR(40),
                      atualizado_por VARCHAR(120),
                      atualizado_por_matricula VARCHAR(40),
                      suspenso_por VARCHAR(120),
                      suspenso_por_matricula VARCHAR(40),
                      motivo_suspensao TEXT,
                      criado_em TIMESTAMP,
                      atualizado_em TIMESTAMP,
                      suspenso_em TIMESTAMP
                    )
                    """);
            statement.execute("ALTER TABLE prescricao_item_estruturado ADD COLUMN IF NOT EXISTS tipo_item VARCHAR(40) NOT NULL DEFAULT 'MEDICAMENTO'");
            statement.execute("ALTER TABLE prescricao_item_estruturado ADD COLUMN IF NOT EXISTS setor_execucao VARCHAR(20)");
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_prescricao_item_estruturado_bam
                    ON prescricao_item_estruturado (idbam, status)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_prescricao_item_estruturado_prescricao
                    ON prescricao_item_estruturado (idprescricao)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_prescricao_item_estruturado_paciente
                    ON prescricao_item_estruturado (idpaciente)
                    """);
            LOG.info("Schema de prescricao estruturada verificado.");
        } catch (Exception e) {
            LOG.error("Falha ao verificar schema de prescricao estruturada.", e);
        }
    }

    private void criarTabelaCicloMedicacao() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS prescricao_item_ciclo_evento (
                      idevento SERIAL PRIMARY KEY,
                      iditem INTEGER NOT NULL,
                      idbam INTEGER NOT NULL,
                      idpaciente INTEGER,
                      idprescricao INTEGER,
                      unidade VARCHAR(20),
                      tipo_evento VARCHAR(30) NOT NULL,
                      status_evento VARCHAR(30) NOT NULL DEFAULT 'REGISTRADO',
                      data_hora_prevista TIMESTAMP,
                      data_hora_realizada TIMESTAMP,
                      quantidade NUMERIC(12,3),
                      dose_administrada VARCHAR(80),
                      responsavel VARCHAR(120),
                      responsavel_matricula VARCHAR(40),
                      observacao TEXT,
                      criado_em TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_prescricao_item_ciclo_item
                    ON prescricao_item_ciclo_evento (iditem, criado_em DESC)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_prescricao_item_ciclo_bam
                    ON prescricao_item_ciclo_evento (idbam, criado_em DESC)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_prescricao_item_ciclo_tipo
                    ON prescricao_item_ciclo_evento (idbam, tipo_evento)
                    """);
            LOG.info("Schema de ciclo de medicacao verificado.");
        } catch (Exception e) {
            LOG.error("Falha ao verificar schema de ciclo de medicacao.", e);
        }
    }

    private void criarTabelaPrescricaoAprazamentoChecagem() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS prescricao_aprazamento_checagem (
                      idaprazamentochecagem SERIAL PRIMARY KEY,
                      idprescricao INTEGER NOT NULL,
                      idbam INTEGER,
                      idpaciente INTEGER,
                      indice_horario INTEGER NOT NULL,
                      horario_aprazado VARCHAR(5) NOT NULL,
                      observacao_aprazamento TEXT,
                      aprazado_por VARCHAR(120),
                      aprazado_por_matricula VARCHAR(40),
                      aprazado_em TIMESTAMP,
                      checado BOOLEAN NOT NULL DEFAULT FALSE,
                      checado_em TIMESTAMP,
                      checado_por VARCHAR(120),
                      checado_por_matricula VARCHAR(40),
                      observacao_checagem TEXT,
                      criado_em TIMESTAMP,
                      atualizado_em TIMESTAMP,
                      CONSTRAINT uk_prescricao_aprazamento_horario UNIQUE (idprescricao, indice_horario)
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_prescricao_aprazamento_prescricao
                    ON prescricao_aprazamento_checagem (idprescricao, indice_horario)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_prescricao_aprazamento_bam
                    ON prescricao_aprazamento_checagem (idbam)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_prescricao_aprazamento_checado
                    ON prescricao_aprazamento_checagem (idprescricao, checado)
                    """);
            LOG.info("Schema de aprazamento e checagem da prescricao verificado.");
        } catch (Exception e) {
            LOG.error("Falha ao verificar schema de aprazamento e checagem da prescricao.", e);
        }
    }

    private void criarTabelaAssinaturaClinica() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS assinatura_clinica (
                      idassinatura INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                      tipo_documento VARCHAR(60) NOT NULL,
                      iddocumento INTEGER,
                      idbam INTEGER,
                      idpaciente INTEGER,
                      unidade VARCHAR(20),
                      status VARCHAR(20) NOT NULL DEFAULT 'ASSINADO',
                      conteudo_hash VARCHAR(64) NOT NULL,
                      conteudo_resumo TEXT,
                      assinado_por VARCHAR(120),
                      assinado_por_matricula VARCHAR(40),
                      assinado_em TIMESTAMP,
                      adendo_texto TEXT,
                      adendo_por VARCHAR(120),
                      adendo_por_matricula VARCHAR(40),
                      adendo_em TIMESTAMP,
                      assinatura_origem_id INTEGER
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_assinatura_clinica_documento
                    ON assinatura_clinica (tipo_documento, iddocumento, status)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_assinatura_clinica_bam
                    ON assinatura_clinica (tipo_documento, idbam, status)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_assinatura_clinica_paciente
                    ON assinatura_clinica (idpaciente, assinado_em DESC)
                    """);
            LOG.info("Schema de assinatura clinica verificado.");
        } catch (Exception e) {
            LOG.error("Falha ao verificar schema de assinatura clinica.", e);
        }
    }

    private void criarTabelaConformidadePendenciaBaixa() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS conformidade_pendencia_baixa (
                      idbaixa SERIAL PRIMARY KEY,
                      idbam INTEGER NOT NULL,
                      unidade VARCHAR(30),
                      chave_pendencia VARCHAR(220) NOT NULL,
                      titulo TEXT NOT NULL,
                      tipo VARCHAR(40) NOT NULL,
                      status VARCHAR(20) NOT NULL,
                      observacao TEXT,
                      responsavel VARCHAR(120),
                      responsavel_matricula VARCHAR(40),
                      criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    ALTER TABLE conformidade_pendencia_baixa
                    ADD COLUMN IF NOT EXISTS unidade VARCHAR(30)
                    """);
            statement.execute("""
                    UPDATE conformidade_pendencia_baixa baixa
                    SET unidade = bam.unidade
                    FROM bam
                    WHERE bam.idbam = baixa.idbam
                      AND (baixa.unidade IS NULL OR trim(baixa.unidade) = '')
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_conformidade_baixa_bam
                    ON conformidade_pendencia_baixa (idbam, criado_em DESC)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_conformidade_baixa_unidade
                    ON conformidade_pendencia_baixa (unidade, criado_em DESC)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_conformidade_baixa_chave
                    ON conformidade_pendencia_baixa (idbam, chave_pendencia, criado_em DESC)
                    """);
            LOG.info("Schema de baixas de conformidade verificado.");
        } catch (Exception e) {
            LOG.error("Falha ao verificar schema de baixas de conformidade.", e);
        }
    }

    private void criarTabelaProcedimentoSusValorUnidade() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS s_proced_unidade_valor (
                      idprocedimentovalorunidade SERIAL PRIMARY KEY,
                      unidade VARCHAR(20) NOT NULL,
                      pa_id VARCHAR(9) NOT NULL,
                      pa_dv VARCHAR(1) NOT NULL,
                      pa_sa DOUBLE PRECISION NOT NULL DEFAULT 0,
                      pa_sp DOUBLE PRECISION NOT NULL DEFAULT 0,
                      pa_sh DOUBLE PRECISION NOT NULL DEFAULT 0,
                      usarvalorpersonalizado BOOLEAN NOT NULL DEFAULT FALSE,
                      atualizado_por VARCHAR(120),
                      atualizado_em TIMESTAMP,
                      CONSTRAINT uk_s_proced_unidade_valor UNIQUE (unidade, pa_id, pa_dv)
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_s_proced_unidade_valor_unidade
                    ON s_proced_unidade_valor (unidade)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_s_proced_unidade_valor_proced
                    ON s_proced_unidade_valor (pa_id, pa_dv)
                    """);
            LOG.info("Schema de valores de procedimentos por unidade verificado.");
        } catch (Exception e) {
            LOG.error("Falha ao verificar schema de valores de procedimentos por unidade.", e);
        }
    }

    private void criarTabelaSaeEnfermagem() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sae_enfermagem (
                      id SERIAL PRIMARY KEY,
                      uuid VARCHAR(80) NOT NULL UNIQUE,
                      status VARCHAR(30) NOT NULL DEFAULT 'EM_ANDAMENTO',
                      tipo_sae VARCHAR(20) NOT NULL DEFAULT 'GERAL',
                      versao INTEGER NOT NULL DEFAULT 1,
                      uuid_origem VARCHAR(80),
                      motivo_retificacao TEXT,
                      data_sae DATE NOT NULL,
                      idpaciente INTEGER,
                      idbam INTEGER,
                      prontuario VARCHAR(40),
                      nomepaciente VARCHAR(180),
                      unidade VARCHAR(30),
                      paciente_json TEXT,
                      historico_json TEXT,
                      diagnosticos_json TEXT,
                      cuidados_json TEXT,
                      prescricoes_json TEXT,
                      evolucoes_json TEXT,
                      criado_por VARCHAR(120),
                      criado_por_matricula VARCHAR(40),
                      atualizado_por VARCHAR(120),
                      atualizado_por_matricula VARCHAR(40),
                      criado_em TIMESTAMP,
                      atualizado_em TIMESTAMP
                    )
                    """);
            statement.execute("ALTER TABLE sae_enfermagem ADD COLUMN IF NOT EXISTS versao INTEGER NOT NULL DEFAULT 1");
            statement.execute("ALTER TABLE sae_enfermagem ADD COLUMN IF NOT EXISTS tipo_sae VARCHAR(20) NOT NULL DEFAULT 'GERAL'");
            statement.execute("ALTER TABLE sae_enfermagem ADD COLUMN IF NOT EXISTS uuid_origem VARCHAR(80)");
            statement.execute("ALTER TABLE sae_enfermagem ADD COLUMN IF NOT EXISTS motivo_retificacao TEXT");
            statement.execute("DROP INDEX IF EXISTS uq_sae_enfermagem_bam_data");
            statement.execute("DROP INDEX IF EXISTS uq_sae_enfermagem_paciente_data_sem_bam");
            statement.execute("DROP INDEX IF EXISTS uq_sae_enfermagem_bam_data_versao");
            statement.execute("DROP INDEX IF EXISTS uq_sae_enfermagem_paciente_data_versao_sem_bam");
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_sae_enfermagem_bam_data_tipo_versao
                    ON sae_enfermagem (idbam, data_sae, tipo_sae, versao)
                    WHERE idbam IS NOT NULL
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_sae_enfermagem_paciente_data_tipo_versao_sem_bam
                    ON sae_enfermagem (idpaciente, data_sae, tipo_sae, versao)
                    WHERE idbam IS NULL AND idpaciente IS NOT NULL
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sae_enfermagem_bam_data ON sae_enfermagem (idbam, data_sae)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sae_enfermagem_paciente_data ON sae_enfermagem (idpaciente, data_sae)");
            LOG.info("Schema da SAE de Enfermagem verificado.");
        } catch (Exception e) {
            LOG.error("Falha ao verificar schema da SAE de Enfermagem.", e);
        }
    }

    private void criarTabelaCdssAlertaOverride() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS cdss_alerta_override (
                      idoverride SERIAL PRIMARY KEY,
                      idpaciente INTEGER,
                      idbam INTEGER,
                      unidade VARCHAR(30),
                      alerta_codigo VARCHAR(80) NOT NULL,
                      alerta_severidade VARCHAR(20) NOT NULL,
                      justificativa TEXT NOT NULL,
                      modulo_origem VARCHAR(80),
                      usuario_nome VARCHAR(120),
                      usuario_matricula VARCHAR(40),
                      criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_cdss_override_bam
                    ON cdss_alerta_override (idbam, criado_em DESC)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_cdss_override_paciente
                    ON cdss_alerta_override (idpaciente, criado_em DESC)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_cdss_override_alerta
                    ON cdss_alerta_override (alerta_codigo, alerta_severidade)
                    """);
            LOG.info("Schema de CDSS verificado.");
        } catch (Exception e) {
            LOG.error("Falha ao verificar schema de CDSS.", e);
        }
    }
    private void ajustarColunasTextoEvolucao() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE IF EXISTS evolucaopacienteenf
                    ALTER COLUMN relato TYPE TEXT
                    """);
            statement.execute("""
                    ALTER TABLE IF EXISTS evolucaopaciente
                    ALTER COLUMN relato TYPE TEXT
                    """);
            LOG.info("Schema das colunas de texto das evolucoes verificado.");
        } catch (Exception e) {
            LOG.error("Falha ao verificar schema das colunas de texto das evolucoes.", e);
        }
    }
    private void criarTabelaSessoesAtivas() {
        // Passo 1: criar tabela
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sessoes_ativas (
                      idsessao SERIAL PRIMARY KEY,
                      matricula VARCHAR(15) NOT NULL,
                      nome VARCHAR(55) NOT NULL,
                      token_hash VARCHAR(64) NOT NULL,
                      ip_address VARCHAR(45),
                      computador VARCHAR(100),
                      unidade VARCHAR(30),
                      data_login TIMESTAMP DEFAULT NOW(),
                      ultima_atividade TIMESTAMP DEFAULT NOW(),
                      ativa BOOLEAN DEFAULT TRUE
                    )
                    """);
        } catch (Exception e) {
            LOG.error("Falha ao criar tabela sessoes_ativas.", e);
        }

        // Passo 2: indices de apoio (falha tolerada)
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sessoes_ativas_matricula ON sessoes_ativas (matricula)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sessoes_ativas_ativa ON sessoes_ativas (ativa)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sessoes_ativas_token_hash ON sessoes_ativas (token_hash)");
        } catch (Exception e) {
            LOG.warn("Falha ao criar indices de apoio de sessoes_ativas (nao critico).", e);
        }

        // Passo 3: limpar duplicatas e criar indice unico (critico — sem ele dois logins com a mesma matricula sao permitidos)
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            // Desativa todas as sessoes ativas duplicadas, mantendo apenas a mais recente por matricula
            int duplicatasDesativadas = statement.executeUpdate("""
                    UPDATE sessoes_ativas
                    SET ativa = false
                    WHERE ativa = true
                      AND idsessao NOT IN (
                        SELECT DISTINCT ON (TRIM(matricula)) idsessao
                        FROM sessoes_ativas
                        WHERE ativa = true
                        ORDER BY TRIM(matricula), ultima_atividade DESC NULLS LAST, idsessao DESC
                      )
                    """);
            if (duplicatasDesativadas > 0) {
                LOG.warnf("[SESSAO] %d sessoes duplicadas desativadas para viabilizar indice unico.", duplicatasDesativadas);
            }
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_session
                    ON sessoes_ativas (matricula)
                    WHERE ativa = true
                    """);
            LOG.info("[SESSAO] Indice unico idx_unique_active_session verificado — duplo login por matricula bloqueado no banco.");
        } catch (Exception e) {
            LOG.errorf(e, "[SESSAO] CRITICO: falha ao criar indice unico idx_unique_active_session. Duplo login com a mesma matricula NAO esta protegido no banco de dados!");
        }

        // Passo 4: verificar que o indice realmente existe
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                java.sql.ResultSet rs = statement.executeQuery(
                    "SELECT 1 FROM pg_indexes WHERE tablename='sessoes_ativas' AND indexname='idx_unique_active_session'")) {
            if (!rs.next()) {
                LOG.error("[SESSAO] CRITICO: indice idx_unique_active_session NAO existe no banco! Duplo login NAO esta protegido.");
            } else {
                LOG.info("[SESSAO] Schema de sessoes ativas verificado com sucesso.");
            }
        } catch (Exception e) {
            LOG.warn("[SESSAO] Nao foi possivel verificar existencia do indice unico.", e);
        }
    }

    private void criarTabelaChatConversasLimpas() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_conversas_limpas (
                      idchatconversalimpa SERIAL PRIMARY KEY,
                      tipo VARCHAR(20) NOT NULL,
                      sala VARCHAR(40),
                      unidade VARCHAR(50) NOT NULL,
                      usuario_matricula VARCHAR(20) NOT NULL,
                      outro_matricula VARCHAR(20),
                      ultimo_id_limpo INTEGER,
                      criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_chat_conversas_limpas_sala
                    ON chat_conversas_limpas (unidade, tipo, usuario_matricula, sala)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_chat_conversas_limpas_privado
                    ON chat_conversas_limpas (unidade, tipo, usuario_matricula, outro_matricula)
                    """);
            LOG.info("Schema de conversas limpas do chat verificado.");
        } catch (Exception e) {
            LOG.error("Falha ao verificar schema de conversas limpas do chat.", e);
        }
    }
}
