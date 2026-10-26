-- Rastreabilidade estruturada de erro de medicacao / reacao adversa no
-- Nucleo de Seguranca do Paciente (nsp_evento). Sem estes campos, o incidente
-- ficava so em texto livre e nao dava para cruzar com o medicamento/prescricao
-- nem gerar indicador de farmacovigilancia por principio ativo.
ALTER TABLE nsp_evento
    ADD COLUMN IF NOT EXISTS medicamento VARCHAR(160),
    ADD COLUMN IF NOT EXISTS dose_administrada VARCHAR(80),
    ADD COLUMN IF NOT EXISTS via_administracao VARCHAR(40),
    ADD COLUMN IF NOT EXISTS idprescricao INTEGER,
    ADD COLUMN IF NOT EXISTS causalidade VARCHAR(20);
