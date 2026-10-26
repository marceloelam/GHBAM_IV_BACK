-- Modelo(s) de digital SourceAFIS do servidor, gerados a partir da imagem capturada
-- pelo agente local. Permite comparar a digital no proprio servidor (Linux), sem
-- depender do SDK Nitgen na estacao do cliente - necessario para login biometrico
-- fora da rede interna. Formato: "SOURCEAFIS_V1:<base64>" separados por ';'.
-- O FIR Nitgen (colunas fir/biometria) permanece intacto.
ALTER TABLE servidor ADD COLUMN IF NOT EXISTS biometria_sourceafis TEXT;
