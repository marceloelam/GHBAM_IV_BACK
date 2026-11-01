-- Rastreio do pedido medico (medicacao/exames da PA): quem executou e quando, por item.
-- Preenchido pelo servidor a partir da sessao (finalizacao em lote ou item a item).
-- Pedidos anteriores a esta migration ficam sem valor (so o texto livre em obs2).
ALTER TABLE pedidomedico2 ADD COLUMN IF NOT EXISTS executado_em TIMESTAMP;
ALTER TABLE pedidomedico2 ADD COLUMN IF NOT EXISTS executado_por VARCHAR(120);
ALTER TABLE pedidomedico2 ADD COLUMN IF NOT EXISTS executado_por_matricula VARCHAR(40);
