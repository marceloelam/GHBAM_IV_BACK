-- Catálogo autoral GHBAM: somente modelos de trabalho em RASCUNHO.
-- Nenhum item desta carga é liberado automaticamente para uso assistencial.
-- A publicação depende de revisão e aprovação no módulo Classificação própria GHBAM.

INSERT INTO classificacao_ghbam_item
    (uuid, codigo, tipo, titulo, definicao, status, versao, indicadores_json, relacionamentos_json, criado_por)
VALUES
    ('8d4a3f0a-5c35-4fb0-8e9f-000000000001', 'NC-001', 'NECESSIDADE_CUIDADO', 'Acompanhamento da segurança assistencial',
     'Foco de cuidado destinado a organizar observações, riscos percebidos e medidas de proteção relacionadas à assistência.',
     'RASCUNHO', '0.1.0', '["observacao_registrada","risco_reavaliado"]', '["AC-001","RA-001","AL-001"]', 'GHBAM - catálogo inicial'),
    ('8d4a3f0a-5c35-4fb0-8e9f-000000000002', 'NC-002', 'NECESSIDADE_CUIDADO', 'Conforto e resposta humana',
     'Foco de cuidado destinado a reconhecer desconfortos relatados ou observados e acompanhar a resposta da pessoa às medidas adotadas.',
     'RASCUNHO', '0.1.0', '["conforto_reavaliado","resposta_registrada"]', '["AC-002","RA-002"]', 'GHBAM - catálogo inicial'),
    ('8d4a3f0a-5c35-4fb0-8e9f-000000000003', 'NC-003', 'NECESSIDADE_CUIDADO', 'Autonomia para o cuidado',
     'Foco de cuidado destinado a identificar barreiras, capacidades e necessidades de apoio para participação segura no próprio cuidado.',
     'RASCUNHO', '0.1.0', '["capacidade_identificada","orientacao_registrada"]', '["AC-003","RA-003"]', 'GHBAM - catálogo inicial'),
    ('8d4a3f0a-5c35-4fb0-8e9f-000000000004', 'AC-001', 'ACAO_CUIDADO', 'Registrar observação assistencial estruturada',
     'Registrar achados relevantes, contexto, horário e fonte da informação usando linguagem objetiva e rastreável.',
     'RASCUNHO', '0.1.0', '["registro_completo","fonte_identificada"]', '["NC-001","RA-001"]', 'GHBAM - catálogo inicial'),
    ('8d4a3f0a-5c35-4fb0-8e9f-000000000005', 'AC-002', 'ACAO_CUIDADO', 'Reavaliar resposta ao cuidado',
     'Reavaliar a resposta da pessoa após uma medida de cuidado e registrar manutenção, melhora, piora ou ausência de resposta.',
     'RASCUNHO', '0.1.0', '["reavaliacao_registrada","resposta_classificada"]', '["NC-002","RA-002","AL-001"]', 'GHBAM - catálogo inicial'),
    ('8d4a3f0a-5c35-4fb0-8e9f-000000000006', 'AC-003', 'ACAO_CUIDADO', 'Orientar participação segura no cuidado',
     'Oferecer orientação compatível com a situação registrada, confirmar compreensão e documentar dúvidas ou necessidade de apoio.',
     'RASCUNHO', '0.1.0', '["orientacao_registrada","compreensao_verificada"]', '["NC-003","RA-003"]', 'GHBAM - catálogo inicial'),
    ('8d4a3f0a-5c35-4fb0-8e9f-000000000007', 'RA-001', 'RESULTADO_AVALIACAO', 'Registro assistencial atualizado',
     'Resultado esperado quando as informações relevantes do cuidado estão registradas, datadas, atribuídas e disponíveis para continuidade.',
     'RASCUNHO', '0.1.0', '["registro_atualizado","autoria_identificada"]', '["NC-001","AC-001"]', 'GHBAM - catálogo inicial'),
    ('8d4a3f0a-5c35-4fb0-8e9f-000000000008', 'RA-002', 'RESULTADO_AVALIACAO', 'Resposta ao cuidado acompanhada',
     'Resultado esperado quando a resposta observada foi reavaliada em momento definido e registrada com justificativa objetiva.',
     'RASCUNHO', '0.1.0', '["momento_definido","resposta_documentada"]', '["NC-002","AC-002"]', 'GHBAM - catálogo inicial'),
    ('8d4a3f0a-5c35-4fb0-8e9f-000000000009', 'RA-003', 'RESULTADO_AVALIACAO', 'Participação orientada e registrada',
     'Resultado esperado quando a participação da pessoa ou rede de apoio foi orientada, compreendida e registrada sem presumir adesão.',
     'RASCUNHO', '0.1.0', '["orientacao_documentada","necessidade_de_apoio_registrada"]', '["NC-003","AC-003"]', 'GHBAM - catálogo inicial'),
    ('8d4a3f0a-5c35-4fb0-8e9f-000000000010', 'AL-001', 'ALERTA_FLUXO', 'Reavaliação assistencial pendente',
     'Alerta de fluxo para revisão humana quando uma necessidade possui ação registrada sem resultado ou reavaliação documentada no período definido.',
     'RASCUNHO', '0.1.0', '["prazo_configurado","pendencia_exibida","tratamento_registrado"]', '["NC-001","AC-002","RA-001","RA-002"]', 'GHBAM - catálogo inicial')
ON CONFLICT (codigo) DO NOTHING;
