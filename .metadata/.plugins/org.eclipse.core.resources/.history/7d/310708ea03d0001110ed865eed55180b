SELECT setval(
    pg_get_serial_sequence('rxprocedimentos', 'idrx'),
    GREATEST(COALESCE(MAX(idrx), 0) + 1, 1),
    false
)
FROM rxprocedimentos;
