-- Quadro de fluxo de pacientes (/base2/fluxo-pacientes): permissao propria,
-- alem dos perfis assistenciais (recepcao, triagem, medico, enfermagem, NIR, adm).
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pfluxopacientes BOOLEAN NOT NULL DEFAULT FALSE;
