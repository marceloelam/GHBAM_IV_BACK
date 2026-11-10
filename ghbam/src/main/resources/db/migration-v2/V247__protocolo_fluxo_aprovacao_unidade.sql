-- Aprovacao de protocolos: registra tambem a unidade (da sessao) de quem decidiu.
-- A justificativa (coluna comentario) passa a ser exigida pela API em toda decisao.
ALTER TABLE protocolo_fluxo_aprovacao ADD COLUMN IF NOT EXISTS unidade VARCHAR(80);
