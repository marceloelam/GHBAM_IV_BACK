-- Tabela para Protocolos Médicos
CREATE TABLE IF NOT EXISTS protocolo (
    id SERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    especialidade VARCHAR(50),
    descricao VARCHAR(200),
    versao VARCHAR(20) DEFAULT '1.0',
    perguntas_json TEXT,
    campos_json TEXT,
    regras_json TEXT,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP,
    criado_por VARCHAR(50),
    atualizado_por VARCHAR(50),
    ativo BOOLEAN DEFAULT TRUE
);

-- Inserir alguns protocolos de exemplo
INSERT INTO protocolo (nome, especialidade, descricao, versao, perguntas_json, campos_json, regras_json) VALUES
('dor_toracica', 'cardiologia', 'Avaliação sistematizada de dor torácica', '1.0',
'[{"id":"inicio_dor","tipo":"datetime","label":"Início da dor","obrigatorio":true},{"id":"localizacao","tipo":"select","label":"Localização","obrigatorio":true,"opcoes":[{"value":"precordial","label":"Precordial"},{"value":"peito","label":"Peito"}]},{"id":"intensidade","tipo":"slider","label":"Intensidade (1-10)","min":1,"max":10,"obrigatorio":true}]',
'[{"id":"inicio_dor","tipo":"datetime","label":"Início da dor","obrigatorio":true},{"id":"localizacao","tipo":"select","label":"Localização","obrigatorio":true,"opcoes":[{"value":"precordial","label":"Precordial"},{"value":"peito","label":"Peito"}]},{"id":"intensidade","tipo":"slider","label":"Intensidade (1-10)","min":1,"max":10,"obrigatorio":true}]',
'[{"condicoes":[{"campo":"intensidade","operador":">=","valores":["7"]}],"acao":{"tipo":"mostrar_alerta","mensagem":"Dor intensa! Considerar protocolo de SCA"}}]')
ON CONFLICT (nome) DO NOTHING;
