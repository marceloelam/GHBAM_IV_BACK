ALTER TABLE unidade_video_canal
    DROP CONSTRAINT IF EXISTS uk_unidade_video_canal_sigla_url;

CREATE UNIQUE INDEX IF NOT EXISTS uk_unidade_video_canal_sigla_url_hash
    ON unidade_video_canal (sigla, md5(url));
