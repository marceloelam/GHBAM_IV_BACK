CREATE TABLE IF NOT EXISTS aps_equipe_microarea (
    id BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(30) NOT NULL,
    microarea VARCHAR(30) NOT NULL,
    equipe_referencia VARCHAR(120) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_aps_equipe_microarea UNIQUE (unidade, microarea)
);
