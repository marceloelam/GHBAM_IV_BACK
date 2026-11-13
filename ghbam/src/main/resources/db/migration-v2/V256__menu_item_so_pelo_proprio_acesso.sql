-- Cada item do menu passa a depender só do próprio acesso: antes ele também aparecia por outra condição
-- (ser administrador, ter o perfil do grupo ou um acesso parecido), e desligar o acesso não o escondia.
-- Para ninguém perder tela no dia, quem via o item por essas outras condições fica com o acesso ligado.
-- Depois disso, desligar o acesso na tela de servidores (ou na coordenação) esconde o item de verdade.

-- Itens que o administrador via sem o acesso.
UPDATE servidor2 s2 SET caddrives = TRUE, gerenciarcertificados = TRUE, logs = TRUE, controleassistencial = TRUE,
       documentosassistenciais = TRUE, saeenfermagem = TRUE, palmoxarifado = TRUE
  FROM servidor s WHERE s.matricula = s2.matricula AND s.administracao = TRUE;

-- Pedir material: aparecia também para a gestão do almoxarifado e para o administrador.
UPDATE servidor2 s2 SET palmoxpedir = TRUE FROM servidor s
 WHERE s.matricula = s2.matricula AND s2.palmoxpedir IS DISTINCT FROM TRUE AND (s2.palmoxarifado = TRUE OR s.administracao = TRUE);

-- Imprimir BAM: a enfermagem via pelo perfil.
UPDATE servidor2 s2 SET imprimirbam = TRUE FROM servidor s
 WHERE s.matricula = s2.matricula AND s2.imprimirbam IS DISTINCT FROM TRUE AND (s.enfermagem = TRUE OR s.tcenfermagem = TRUE);

-- Limites de dose (tela): aparecia para quem edita regras de dose e para o perfil médico.
UPDATE servidor2 s2 SET plimitesdose = TRUE FROM servidor s
 WHERE s.matricula = s2.matricula AND s2.plimitesdose IS DISTINCT FROM TRUE AND (s2.regrasdose = TRUE OR s.medico = TRUE);

-- Mapa epidemiológico: aparecia para o administrador e para vigilância/CCIH.
UPDATE servidor2 s2 SET pmapaepidemiologico = TRUE FROM servidor s
 WHERE s.matricula = s2.matricula AND s2.pmapaepidemiologico IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s2.vigilanciaepidemiologica = TRUE OR s2.ccih = TRUE);

-- Acessos parecidos que também mostravam o item.
UPDATE servidor2 SET pfhirsumarioalta = TRUE WHERE pfhirsumarioalta IS DISTINCT FROM TRUE AND (integracaosus = TRUE OR cadunidades = TRUE);
UPDATE servidor2 SET ppepatendimento = TRUE WHERE ppepatendimento IS DISTINCT FROM TRUE
   AND (historicocompleto = TRUE OR historicocompleto_adm = TRUE OR prontuariocompleto = TRUE
        OR nirhistoricocompleto = TRUE OR nirenfermariaseleitos = TRUE OR nir = TRUE OR ccih = TRUE);
UPDATE servidor2 SET pjornadapa = TRUE WHERE pjornadapa IS DISTINCT FROM TRUE
   AND (historicocompleto = TRUE OR prontuariocompleto = TRUE OR resumopaciente = TRUE OR enfermariaseleitos = TRUE OR ccih = TRUE);
UPDATE servidor2 SET historicocompleto = TRUE WHERE historicocompleto IS DISTINCT FROM TRUE AND historicocompleto_adm = TRUE;
UPDATE servidor2 SET gedconsulta = TRUE WHERE gedconsulta IS DISTINCT FROM TRUE AND ged = TRUE;
UPDATE servidor2 SET pcme = TRUE WHERE pcme IS DISTINCT FROM TRUE AND pcentrocirurgico = TRUE;
UPDATE servidor2 SET prnds = TRUE WHERE prnds IS DISTINCT FROM TRUE AND integracaosus = TRUE;
UPDATE servidor2 SET vigilanciaepidemiologica = TRUE WHERE vigilanciaepidemiologica IS DISTINCT FROM TRUE AND ccih = TRUE;
UPDATE servidor2 SET configuracaovigilanciaepidemiologica = TRUE
 WHERE configuracaovigilanciaepidemiologica IS DISTINCT FROM TRUE AND ccih = TRUE;
