-- Todos os grupos do menu passam a obedecer aos acessos da tela de servidores, item por item
-- (mapa src/layout/acessoPorRota.js no frontend). Consultório (modo ambulatorial) fica para depois.
-- Acessos de ação (autorizar APAC, exportar BPA, dispensar...) NÃO são ligados aqui: as telas ganharam acessos só de exibição.
-- 1) Itens que não tinham acesso próprio ganham um (padrão desligado).
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS padmissaorn BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS papac BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pbpa BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pccihresumo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pcdsspreventivo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pclassificarpaciente BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pcoleta BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pcorrigirresultados BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pfarmaciaatendimento BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pfaturamentosus BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pfaturas BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pfichaanestesica BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pimprimiridentificacao BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pindicadorescirurgicos BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pinternados BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pjornadapa BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS plimitesdose BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pmapacirurgico BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pmapaepidemiologico BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pmedicacaomaternidade BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pmedicacaopediatria BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pmedicacaospa BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pmodelosprescricao BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS ppainelmunicipal BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS ppendenciaspa BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS ppepatendimento BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pplantaoenfermagem BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pprocessosinternacao BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pproducaoassistencial BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pprotocolosfluxograma BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS praas BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS prealizarexames BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS preclassificarpaciente BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS presumodealta BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS psumarioobito BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS ptiss BOOLEAN NOT NULL DEFAULT FALSE;

-- 2) Ninguém perde tela: quem já abria o grupo (perfis/acessos que abrem o grupo) fica com o acesso ligado.
-- atendimentoperiodo: Faturamento, Laboratório
UPDATE servidor2 s2 SET atendimentoperiodo = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.atendimentoperiodo IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.laboratorio = TRUE OR s.remedios = TRUE);
-- avaliacaopacienteia: Faturamento, Médico
UPDATE servidor2 s2 SET avaliacaopacienteia = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.avaliacaopacienteia IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.medico = TRUE OR s.remedios = TRUE);
-- balancohidrico: Enfermagem
UPDATE servidor2 s2 SET balancohidrico = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.balancohidrico IS DISTINCT FROM TRUE
   AND (s.enfermagem = TRUE OR s.tcenfermagem = TRUE);
-- biexecutivo: Faturamento
UPDATE servidor2 s2 SET biexecutivo = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.biexecutivo IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- bulariomedico: Médico
UPDATE servidor2 s2 SET bulariomedico = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.bulariomedico IS DISTINCT FROM TRUE
   AND (s.medico = TRUE);
-- enfermariaseleitos: Faturamento
UPDATE servidor2 s2 SET enfermariaseleitos = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.enfermariaseleitos IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- evolucaomultidisciplinar: Multidisciplinar
UPDATE servidor2 s2 SET evolucaomultidisciplinar = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.evolucaomultidisciplinar IS DISTINCT FROM TRUE
   AND (s.assistencia = TRUE OR s.fisioterapia = TRUE OR s.fonoaudiologia = TRUE OR s.nutricao = TRUE OR s.psicologia = TRUE);
-- historicocompleto: Faturamento, Multidisciplinar, Médico, Triagem
UPDATE servidor2 s2 SET historicocompleto = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.historicocompleto IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.assistencia = TRUE OR s.fisioterapia = TRUE OR s.fonoaudiologia = TRUE OR s.medico = TRUE OR s.nutricao = TRUE OR s.psicologia = TRUE OR s.remedios = TRUE OR s.triagem = TRUE);
-- imprimirbam: Faturamento
UPDATE servidor2 s2 SET imprimirbam = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.imprimirbam IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- minhasassinaturas: Médico
UPDATE servidor2 s2 SET minhasassinaturas = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.minhasassinaturas IS DISTINCT FROM TRUE
   AND (s.medico = TRUE);
-- nirrelatorio: Faturamento
UPDATE servidor2 s2 SET nirrelatorio = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.nirrelatorio IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- padmissaorn: Médico
UPDATE servidor2 s2 SET padmissaorn = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.padmissaorn IS DISTINCT FROM TRUE
   AND (s.medico = TRUE);
-- papac: Faturamento SUS
UPDATE servidor2 s2 SET papac = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.papac IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- pbpa: Faturamento
UPDATE servidor2 s2 SET pbpa = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pbpa IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- pccihresumo: Faturamento
UPDATE servidor2 s2 SET pccihresumo = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pccihresumo IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- pcdsspreventivo: Médico
UPDATE servidor2 s2 SET pcdsspreventivo = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pcdsspreventivo IS DISTINCT FROM TRUE
   AND (s.medico = TRUE);
-- pcirurgialistaespera: Centro Cirurgico
UPDATE servidor2 s2 SET pcirurgialistaespera = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pcirurgialistaespera IS DISTINCT FROM TRUE
   AND (s2.pcentrocirurgico = TRUE);
-- pclassificarpaciente: Triagem
UPDATE servidor2 s2 SET pclassificarpaciente = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pclassificarpaciente IS DISTINCT FROM TRUE
   AND (s.triagem = TRUE);
-- pcoleta: Laboratório
UPDATE servidor2 s2 SET pcoleta = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pcoleta IS DISTINCT FROM TRUE
   AND (s.laboratorio = TRUE);
-- pcorrigirresultados: Laboratório
UPDATE servidor2 s2 SET pcorrigirresultados = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pcorrigirresultados IS DISTINCT FROM TRUE
   AND (s.laboratorio = TRUE);
-- pdrives: Enfermagem, Farmácia, Faturamento, Imagem, Laboratório, Multidisciplinar, Médico, Nir, RH, SPA
UPDATE servidor2 s2 SET pdrives = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pdrives IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.assistencia = TRUE OR s.enfermagem = TRUE OR s.farmcentral = TRUE OR s.farmhmsfx = TRUE OR s.faturamento = TRUE OR s.fisioterapia = TRUE OR s.fonoaudiologia = TRUE OR s.laboratorio = TRUE OR s.medicacao = TRUE OR s.medico = TRUE OR s.nutricao = TRUE OR s.psicologia = TRUE OR s.remedios = TRUE OR s.rh = TRUE OR s.rx = TRUE OR s.tcenfermagem = TRUE OR s.tomografia = TRUE OR s.ultrassonografia = TRUE OR s2.painelmaternidade = TRUE OR s2.painelpediatria = TRUE OR s2.painelspa = TRUE);
-- pesquisanir: Faturamento
UPDATE servidor2 s2 SET pesquisanir = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pesquisanir IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- pfarmaciaatendimento: Farmácia
UPDATE servidor2 s2 SET pfarmaciaatendimento = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pfarmaciaatendimento IS DISTINCT FROM TRUE
   AND (s.farmcentral = TRUE OR s.farmhmsfx = TRUE);
-- pfaturamentosus: Faturamento SUS
UPDATE servidor2 s2 SET pfaturamentosus = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pfaturamentosus IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- pfaturas: Faturamento
UPDATE servidor2 s2 SET pfaturas = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pfaturas IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- pfichaanestesica: Médico
UPDATE servidor2 s2 SET pfichaanestesica = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pfichaanestesica IS DISTINCT FROM TRUE
   AND (s.medico = TRUE);
-- pfluxopacientes: Enfermagem, Médico, Nir, Triagem
UPDATE servidor2 s2 SET pfluxopacientes = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pfluxopacientes IS DISTINCT FROM TRUE
   AND (s.enfermagem = TRUE OR s.faturamento = TRUE OR s.medico = TRUE OR s.tcenfermagem = TRUE OR s.triagem = TRUE);
-- pimprimiridentificacao: Enfermagem
UPDATE servidor2 s2 SET pimprimiridentificacao = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pimprimiridentificacao IS DISTINCT FROM TRUE
   AND (s.enfermagem = TRUE OR s.tcenfermagem = TRUE);
-- pindicadorescirurgicos: Centro Cirurgico
UPDATE servidor2 s2 SET pindicadorescirurgicos = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pindicadorescirurgicos IS DISTINCT FROM TRUE
   AND (s2.pcentrocirurgico = TRUE);
-- pinternados: Médico
UPDATE servidor2 s2 SET pinternados = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pinternados IS DISTINCT FROM TRUE
   AND (s.medico = TRUE);
-- pjornadapa: Faturamento, Médico, Nir, Triagem
UPDATE servidor2 s2 SET pjornadapa = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pjornadapa IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.faturamento = TRUE OR s.medico = TRUE OR s.remedios = TRUE OR s.triagem = TRUE);
-- plimitesdose: Enfermagem
UPDATE servidor2 s2 SET plimitesdose = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.plimitesdose IS DISTINCT FROM TRUE
   AND (s.enfermagem = TRUE OR s.tcenfermagem = TRUE);
-- plocalizaratendimento: Administrativo, Nir
UPDATE servidor2 s2 SET plocalizaratendimento = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.plocalizaratendimento IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.faturamento = TRUE);
-- pmapacirurgico: Centro Cirurgico
UPDATE servidor2 s2 SET pmapacirurgico = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pmapacirurgico IS DISTINCT FROM TRUE
   AND (s2.pcentrocirurgico = TRUE);
-- pmapaepidemiologico: Enfermagem, Médico
UPDATE servidor2 s2 SET pmapaepidemiologico = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pmapaepidemiologico IS DISTINCT FROM TRUE
   AND (s.enfermagem = TRUE OR s.medico = TRUE OR s.tcenfermagem = TRUE);
-- pmedicacaomaternidade: SPA
UPDATE servidor2 s2 SET pmedicacaomaternidade = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pmedicacaomaternidade IS DISTINCT FROM TRUE
   AND (s.medicacao = TRUE OR s2.painelmaternidade = TRUE OR s2.painelpediatria = TRUE OR s2.painelspa = TRUE);
-- pmedicacaopediatria: SPA
UPDATE servidor2 s2 SET pmedicacaopediatria = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pmedicacaopediatria IS DISTINCT FROM TRUE
   AND (s.medicacao = TRUE OR s2.painelmaternidade = TRUE OR s2.painelpediatria = TRUE OR s2.painelspa = TRUE);
-- pmedicacaospa: SPA
UPDATE servidor2 s2 SET pmedicacaospa = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pmedicacaospa IS DISTINCT FROM TRUE
   AND (s.medicacao = TRUE OR s2.painelmaternidade = TRUE OR s2.painelpediatria = TRUE OR s2.painelspa = TRUE);
-- pmodelosprescricao: Médico
UPDATE servidor2 s2 SET pmodelosprescricao = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pmodelosprescricao IS DISTINCT FROM TRUE
   AND (s.medico = TRUE);
-- ppainelmunicipal: Faturamento
UPDATE servidor2 s2 SET ppainelmunicipal = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.ppainelmunicipal IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- ppendenciaspa: Faturamento
UPDATE servidor2 s2 SET ppendenciaspa = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.ppendenciaspa IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- ppepatendimento: Médico
UPDATE servidor2 s2 SET ppepatendimento = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.ppepatendimento IS DISTINCT FROM TRUE
   AND (s.medico = TRUE);
-- pplantaoenfermagem: Enfermagem
UPDATE servidor2 s2 SET pplantaoenfermagem = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pplantaoenfermagem IS DISTINCT FROM TRUE
   AND (s.enfermagem = TRUE OR s.tcenfermagem = TRUE);
-- pprocessosinternacao: Médico
UPDATE servidor2 s2 SET pprocessosinternacao = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pprocessosinternacao IS DISTINCT FROM TRUE
   AND (s.medico = TRUE);
-- pproducaoassistencial: Administrativo
UPDATE servidor2 s2 SET pproducaoassistencial = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pproducaoassistencial IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE);
-- pprotocolosfluxograma: Médico
UPDATE servidor2 s2 SET pprotocolosfluxograma = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.pprotocolosfluxograma IS DISTINCT FROM TRUE
   AND (s.medico = TRUE);
-- praas: Faturamento SUS
UPDATE servidor2 s2 SET praas = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.praas IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- prealizarexames: Laboratório
UPDATE servidor2 s2 SET prealizarexames = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.prealizarexames IS DISTINCT FROM TRUE
   AND (s.laboratorio = TRUE);
-- preclassificarpaciente: Triagem
UPDATE servidor2 s2 SET preclassificarpaciente = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.preclassificarpaciente IS DISTINCT FROM TRUE
   AND (s.triagem = TRUE);
-- presumodealta: Médico
UPDATE servidor2 s2 SET presumodealta = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.presumodealta IS DISTINCT FROM TRUE
   AND (s.medico = TRUE);
-- psumarioobito: Enfermagem
UPDATE servidor2 s2 SET psumarioobito = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.psumarioobito IS DISTINCT FROM TRUE
   AND (s.enfermagem = TRUE OR s.tcenfermagem = TRUE);
-- ptiss: Faturamento
UPDATE servidor2 s2 SET ptiss = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.ptiss IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
-- resumopacientes: Faturamento
UPDATE servidor2 s2 SET resumopacientes = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s2.resumopacientes IS DISTINCT FROM TRUE
   AND (s.administracao = TRUE OR s.remedios = TRUE);
