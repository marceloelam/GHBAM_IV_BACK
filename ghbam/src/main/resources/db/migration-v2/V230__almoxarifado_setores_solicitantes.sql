-- Setores que pedem material ao almoxarifado alem dos setores assistenciais
-- (setoresunidade): administracao, NIR, recepcao etc. Fica separado do
-- cadastro de setores para nao aparecer como destino de paciente.

CREATE TABLE IF NOT EXISTS almox_setor (
    idsetor BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    nome VARCHAR(80) NOT NULL,
    dataregistro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuarioregistro VARCHAR(80)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_almox_setor_unidade_nome
    ON almox_setor (unidade, nome);
