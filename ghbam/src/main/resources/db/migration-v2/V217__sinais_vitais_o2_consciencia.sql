-- NEWS2 completo: O2 suplementar e nivel de consciencia (AVPU/ACVPU) nos sinais vitais.
-- NULL = nao registrado (registros antigos); o NEWS2 avisa que o escore pode estar subestimado.
ALTER TABLE prescricaocont ADD COLUMN IF NOT EXISTS o2suplementar BOOLEAN;
ALTER TABLE prescricaocont ADD COLUMN IF NOT EXISTS consciencia VARCHAR(1);
