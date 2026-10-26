CREATE TABLE IF NOT EXISTS tema_visual (
    idtema_visual BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(30) NOT NULL,
    primaria VARCHAR(7) NOT NULL,
    secundaria VARCHAR(7) NOT NULL,
    gradiente BOOLEAN NOT NULL DEFAULT TRUE,
    papeis_json TEXT,
    usuario VARCHAR(120),
    criado_em TIMESTAMP,
    atualizado_em TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tema_visual_unidade
    ON tema_visual (UPPER(unidade));
