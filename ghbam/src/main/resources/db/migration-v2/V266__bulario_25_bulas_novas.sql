-- Bulário (22/11/2026): inclui 25 bulas que o CDSS não achava (enoxaparina, nifedipino, empagliflozina,
-- oseltamivir...). Fonte: bulas.med.br, pelo nome comercial. Não duplica (pula a fonte já existente).
-- Acerta antes a sequência do id: em cópias da produção ela estava em 1 e o próximo cadastro colidiria.
-- Se a tabela bulario não existir (servidor novo), não faz nada: aí vale o script de banco inicial.
DO $migracao$
BEGIN
  IF to_regclass('public.bulario') IS NULL OR to_regclass('public.bulario_id_seq') IS NULL THEN
    RAISE NOTICE 'Tabela bulario ausente: V266 ignorada.';
    RETURN;
  END IF;
  PERFORM setval('public.bulario_id_seq', GREATEST((SELECT coalesce(max(id),1) FROM public.bulario), (SELECT last_value FROM public.bulario_id_seq)));
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Dipirona + Cafeína + Mucato de Isometepteno', 'Dipirona + Cafeína + Mucato de Isometepteno', 'NEOSALDINA® contém como princípios ativos dipirona, mucato de isometepteno e cafeína. A dipirona, um eficaz analgésico não-opioide, é uma pró-droga que possui, ainda, atividade antipirética e antitérmica intensamente investigada. É amplamente utilizado na clínica, tanto em uso isolado como combinado a outros medicamentos. O isometepteno é um simpaticomimético de ação indireta com atividade vasoconstritora, que apresenta ação analgésica própria e potencializadora de analgésicos. A cafeína é um derivado xantínico que possui discreta ação sobre o sistema nervoso central e apresenta efeito vasocon', 'Como analgésico e antiespasmódico, indicado para o tratamento de diversos tipos de cefaléias ou de dor abdominal tipo cólica.', 'POSOLOGIA

Crianças maiores de 12 anos e adultos: 1 a 2 drágeas (em dose única) a cada 6 horas ou 4 vezes ao dia. A dose diária máxima de 8 drágeas (4 x 2 drágeas) não deve ser excedida.

Este medicamento não deve ser partido ou mastigado.', 'Estados de hipersensibilidade e intolerância à dipirona (ou a derivados pirazolônicos) ou aos demais componentes da fórmula do produto.

NEOSALDINA® está contraindicada nas crises de hipertensão arterial, na presença de discrasias sanguíneas, de transtornos do sistema hematopoiético, de insuficiente função da medula óssea ou de determinadas doenças metabólicas, como porfiria hepática ou deficiência congênita da glicose-6-fosfato-desidrogenase.

O uso de NEOSALDINA® é contraindicado em dosagem elevada por período prolongado sem supervisão médica.

Este medicamento é contraindicado para menores de 12 anos de idade.', 'As principais reações adversas a NEOSALDINA® são as reações cutâneas. As reações mais graves são choque anafilático e discrasias sanguíneas, que raramente ocorrem com o uso da medicação. As frequências de ocorrência das reações são apresentadas abaixo:

Reação comum (> 1/100 e < 1/10): reações cutâneas (exantema)

Reação rara (< 1/10.000): choque anafilático, discrasias sanguíneas (tais como: agranulocitose, leucopenia, trombocitopenia), aumento da frequência cardíaca e irritabilidade. Apesar de serem ocorrências raras, o choque anafilático e as discrasias sanguíneas podem envolver ameaça à vida e podem ocorrer mesmo se a dipirona tiver sido administrada previamente, sem qualquer efeito adverso.

Os sintomas de agranulocitose são: febre alta, sensação de frio, garganta inflamada, dificuldade em engolir, lesões inflamatórias na boca, nariz e garganta, assim como nas regiões genital e anal. Imediata interrupção da medicação é a indicação para a completa recuperação. Portanto, se uma inesperada piora for observada, se a febre não ceder ou se reincidir, ou se lesões dolorosas ocorrerem nas mucosas, principalmente da boca, nariz ou garganta, o tratamento deve ser imediatamente interrompido.

A trombocitopenia pode levar à tendência de sangramentos, podendo ocorrer pequenas hemorragias na pele e mucosas. Já o aumento da frequência cardíaca e a irritabilidade são sintomas breves e podem ser evitados com a redução da dose. Reações de frequência desconhecida: hipotermia e reações de hipersensibilidade afetando a pele (exantema), a conjuntiva e as mucosas da cavidade naso-faríngea. Também podem ocorrer efeitos colaterais simpaticotônicos como náusea, rubor, suor ou dor de cabeça. Esses sintomas desaparecem em geral com a redução de dose.

Reações em grupos especiais de pacientes (frequência desconhecida): em alguns pacientes, especialmente aqueles com história de doença renal, ou em casos de superdose, insuficiência renal transitória, como oligúria e anúria acompanhadas de proteinúria e inflamação do tecido renal (nefrite intersticial) podem ser observadas.

Crises de asma podem ser observadas em pacientes predispostos.

Em casos de eventos adversos, notifique ao Sistema de Notificações em Vigilância Sanitária – NOTIVISA ou à Vigilância Sanitária Estadual ou Municipal. Informe também à empresa através do seu serviço de atendimento.', 'NEOSALDINA® não deve ser tomada concomitante a bebidas alcoólicas, pois pode ocorrer potencialização dos efeitos do álcool pela presença da dipirona.

Em pacientes recebendo concomitante tratamento com ciclosporina, os níveis de ciclosporina no plasma podem ser reduzidos, sendo recomendadas dosagens da concentração plasmática de ciclosporina a intervalos regulares.

Pode ocorrer hipotermia grave quando NEOSALDINA® for associada à clorpromazina.

Podem ocorrer reações hipertensivas graves com o uso concomitante com inibidores da monoaminoxidase (IMAOs). A cafeína pode reduzir a ação sedativa dos ansiolíticos (benzodiazepínicos).', 'NEOSALDINA® deve ser administrada com cuidado a pacientes hipertensos e a pacientes cuja pressão arterial sistólica esteja abaixo de 100 mmHg ou cuja função circulatória seja inconstante (ex. circulação debilitada associada a infarto do miocárdio, lesões múltiplas ou choque circulatório recente).

Caso ocorra leve agitação e/ou aumento dos batimentos cardíacos, a dose diária de NEOSALDINA® deve ser reduzida, o que deverá determinar o desaparecimento imediato dos sintomas, não havendo necessidade de tratamento especial. Recomenda-se cautela em pacientes com asma brônquica ou infecções respiratórias crônicas e em pacientes sensíveis a analgésicos e antiinflamatórios (asma causada por analgésicos, intolerância a analgésicos) e que são, portanto, suscetíveis a crises de asma induzidas por medicamentos ou choque anafilático ocasionado por dipirona. Embora a intolerância a analgésicos seja extremamente rara, o risco de choque é maior após a administração parenteral do que por via oral ou retal.

Pacientes com o sistema hematopoiético debilitado (ex. pacientes citoestáticos) só deverão utilizar dipirona sob supervisão médica e atento monitoramento laboratorial.

Caso ocorram sinais sugestivos de agranulocitose ou trombocitopenia, recomenda-se a interrupção do tratamento com o produto. A administração de NEOSALDINA® a pacientes com amigdalite ou qualquer outra condição que afete a boca e garganta, deve ser exercida com especial cuidado, uma vez que, a condição existente poderá mascarar um sintoma prévio de agranulocitose (angina agranulocítica).

Indivíduos que reajam a bebidas alcoólicas (mesmo em pequenas quantidades), espirrando, lacrimejando, apresentando acentuado rubor, como também indivíduos com alergia a alimentos, pêlo animal, tinturas de cabelo e agentes preservativos devem utilizar o produto com muito cuidado.

Pacientes extremamente sensíveis à cafeína, não devem tomar NEOSALDINA® à noite para evitar dificuldades de conciliar o sono.

O aparecimento de coloração avermelhada espontânea na urina significa eliminação do ácido rubazônico, um metabólito inócuo da dipirona.

Atenção: Este medicamento contém açúcar, portanto deve ser usado com cautela em portadores de Diabetes. Portanto, NEOSALDINA® drágeas pode ser substituído por NEOSALDINA® gotas, que não contém açúcar.

Este medicamento pode causar doping.

Gravidez e lactação

Categoria C de risco na gravidez – Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.

NEOSALDINA® não deve ser administrada durante o período de lactação, pois metabólitos da dipirona podem passar para o leite materno. Se necessário o seu uso, a amamentação deve ser interrompida.

PACIENTES IDOSOS

Aconselha-se o uso de doses menores para pessoas idosas e/ou debilitadas. A fim de prevenir distúrbios do sono em pessoas idosas e sensíveis, NEOSALDINA® não deve ser administrada à noite.

INSUFICIÊNCIA RENAL

Não existe experiência com o uso de NEOSALDINA® por pacientes com transtornos renais, porém não é recomendado o uso de altas doses ou por longos períodos nestes pacientes.

INSUFICIÊNCIA HEPÁTICA

Não existe experiência com o uso de NEOSALDINA® por pacientes com disfunção hepática, porém não é recomendado o uso de altas doses ou por longos períodos nestes pacientes.', 'https://www.bulas.med.br/bula/neosaldina-drageas-profissional', 'Vermelha', '2026-11-22T23:23:55.220Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/neosaldina-drageas-profissional');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Cetorolaco Trometamol', 'Cetorolaco Trometamol', 'Trometamol cetorolaco é um medicamento da classe dos anti-inflamatórios não esteroides (AINEs), com potente capacidade de reduzir a dor, além de controlar a inflamação e a febre.', 'Trometamol cetorolaco está indicado para o controle, em curto prazo, da dor aguda, de intensidade moderada a intensa, que necessite de potência para combatê-la equivalente à de um opioide (por exemplo, morfina), como, por exemplo, ocorre nos pós-operatórios, cólica renal, dor lombar, cólica biliar, entre outros. Trometamol cetorolaco não está indicado para dor crônica (de longa duração).', 'Trometamol cetorolaco pode ser administrado por via intramuscular (IM) ou intravenosa (IV).

CUIDADOS DE ADMINISTRAÇÃO

Informe seu médico se estiver tomando outros medicamentos e quais são eles. Não use nem misture medicamentos por conta própria.

DURAÇÃO DO TRATAMENTO

Em adultos e crianças, a duração máxima de trometamol cetorolaco IM ou IV não deve exceder dois dias, por causa da possibilidade de aumento de eventos adversos com o uso prolongado.

Dose única:

• Pacientes com menos de 65 anos - uma dose de 10 a 60 mg IM ou 10 a 30 mg IV.

• Pacientes a partir de 65 anos ou com insuficiência renal - uma dose de 10 a 30 mg IM ou 10 a 15 mg IV.

• Crianças a partir de 2 anos - uma dose de 1,0 mg/kg IM ou 0,5 a 1,0 mg/kg IV.

Doses múltiplas:

• Pacientes com menos de 65 anos - dose máxima diária não deve exceder 90 mg, com 10 a 30 mg IM a cada 4 - 6 horas ou 10 a 30 mg IV em bolus.

• Pacientes a partir de 65 anos ou com insuficiência renal - 10 a 15 mg IM, a cada 4 - 6 horas, no máximo 60 mg/dia.

• Crianças a partir de 2 anos - 1mg/kg IM ou 0,5 a 1,0 mg/kg, a cada seis horas.

IMPORTANTE: ampola com ponto de corte. Veja instrução abaixo:

Segure a ampola com o ponto de corte marcado no gargalo voltado para sua direção e quebre no sentido oposto (ver ilustração abaixo).

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento. Não interrompa o tratamento sem o conhecimento do seu médico.

O tempo de tratamento varia com o tipo e evolução da doença. Seu médico saberá quando você deve parar o tratamento.', 'Você não deverá usar trometamol cetorolaco se for alérgico ao trometamol cetorolaco ou a qualquer substância contida na ampola. Informe a seu médico sobre qualquer medicamento que esteja usando, antes ou durante o tratamento.

Se você apresenta alergia a outros anti-inflamatórios não esteroides ou ao ácido acetilsalicílico (Aspirina) ou outros inibidores da síntese de prostaglandinas (ibuprofeno e piroxicam), não deve usar trometamol cetorolaco.

Trometamol cetorolaco é contraindicado durante o trabalho de parto e o parto.

Você não deverá aplicar trometamol cetorolaco se estiver com sangramento cerebrovascular suspeito ou confirmado ou for submetido à hemostasia incompleta (quando não se consegue estancar o sangue) ou em casos em que você esteja sob o risco de sangramento.

Trometamol cetorolaco está contraindicado para a prevenção da dor em grandes cirurgias e durante cirurgias, por causa do aumento do risco de sangramento.

Se você tem história de sangramento ou perfuração gastrintestinal, úlcera péptica ou hemorragia digestiva recorrente (dois ou mais episódios distintos e comprovados de ulceração ou sangramento), não deve usar trometamol cetorolaco, assim como os outros anti-inflamatórios não esteroides (AINEs).

Se você for portador de insuficiência cardíaca grave, não poderá tomar trometamol cetorolaco nem outros anti-inflamatórios não esteroides (AINEs).

Se você for portador de insuficiência renal moderada ou grave ou estiver sob risco de insuficiência renal devido à redução da volemia (volume total de sangue circulante) ou desidratação, você não poderá usar trometamol cetorolaco por causa do risco de toxicidade renal.

Trometamol cetorolaco não deve ser usado para administração neuroaxial (epidural ou espinhal), ou seja, na “espinha”, por causa de seu componente alcoólico.

Você não deve tomar trometamol cetorolaco se estiver usando ácido acetilsalicílico ou outros AINEs.

As associações entre trometamol cetorolaco e oxipenfilina e entre trometamol cetorolaco e probenecida são contraindicadas.

Este medicamento é contraindicado para menores de 2 anos de idade.', 'Você pode apresentar os seguintes sintomas com o uso de trometamol cetorolaco:

Distúrbios do trato gastrintestinal: os eventos adversos mais comumente observados são os gastrintestinais. Úlceras pépticas ou não, perfuração ou sangramento gastrintestinal, algumas vezes fatais, podem ocorrer principalmente em idosos. Dor / desconforto abdominal, anorexia (falta de apetite), obstipação (prisão de ventre), diarreia, dispepsia (dificuldade de digestão), eructação (arroto), flatulência (gases intestinais), plenitude (sensação falsa de estômago cheio), gastrite (inflamação no estômago), sangramento gastrintestinal, hematêmese (vomitar sangue), náusea, esofagite (inflamação do esôfago), pancreatite (inflamação no pâncreas), ulceração / perfuração gastrintestinal, estomatite (inflamação na boca ou gengiva), estomatite ulcerativa (inflamação da mucosa da boca com úlceras), vômito, boca seca, sangramento retal, melena (fezes com sangue digerido, semelhantes à borra de café), piora de colite ou doença de Crohn foram relatados durante a administração.

Infecção: meningite sem presença de bactérias.

Distúrbio do sistema linfático e sangue: trombocitopenia (redução do número de plaquetas no sangue).

Distúrbios do sistema imunológico: anafilaxia (reação alérgica grave com choque e parada respiratória, podendo levar a óbito), reações anafilactoides (parecidas com anafilaxia), que podem ser fatais, reações de hipersensibilidade (alergia) como broncoespasmo (contração dos brônquios provocando chiado no peito e falta de ar), rubor (vermelhidão), erupção cutânea, hipotensão (pressão arterial baixa demais) e edema laríngeo (inchaço da laringe).

Distúrbios metabólicos e nutricionais: anorexia (falta de apetite), hipercalemia (aumento do potássio no sangue) e hiponatremia (diminuição do sódio no sangue).

Distúrbios psiquiátricos: pensamentos anormais, depressão, insônia, ansiedade, nervosismo, reações psicóticas (com perda de sentido da realidade), sonhos anormais, alucinações, euforia, dificuldade de concentração e sonolência.

Distúrbios no sistema nervoso central: cefaleia (dor de cabeça), hipercinesia (movimentos desordenados acelerados), parestesia (sensações anormais, como dormência, formigamentos, fisgadas), tontura, convulsões (ataques tipo epiléptico, em que o paciente perde a consciência e apresenta movimentos involuntários) e paladar anormal.

Distúrbios visuais: visão anormal.

Distúrbios auditivos: zumbido, diminuição da audição e vertigem.

Distúrbios no trato urinário e rins: insuficiência renal aguda (quando os rins param de funcionar), aumento da frequência miccional (urinar várias vezes), nefrite intersticial (inflamação dos rins), síndrome nefrótica (doença renal que provoca perda de proteínas com inchaço), retenção urinária (a urina não é eliminada adequadamente), oligúria (baixo volume de urina), síndrome hemolítico urêmica (doença em que existe uma destruição dos glóbulos vermelhos do sangue e perda de função dos rins), dor em flanco (com ou sem sangue na urina). Como ocorre quando são usadas outras drogas que inibem a síntese de prostaglandina, podem aparecer sinais de insuficiência renal, hipercalemia (aumento do potássio no sangue), hiponatremia (diminuição de sódio no sangue), elevação da ureia e da creatinina séricas (substâncias que aumentam no sangue quando os rins não estão funcionando adequadamente e são dosadas no sangue para monitoramento dessa função).

Distúrbios no sistema cardiovascular: bradicardia (diminuição na frequência cardíaca), palpitação (sensação de irregularidade nos batimentos cardíacos) e insuficiência cardíaca.

Distúrbios no sistema vascular: hipertensão (pressão alta), hipotensão (pressão baixa demais), hematoma (extravasamento de sangue dos vasos sanguíneos, popularmente manchas roxas), rubor (vermelhidão), palidez e hemorragia pós-operatória.

Distúrbio no sistema reprodutor feminino: infertilidade.

Distúrbios no sistema respiratório: asma (crises de chiado no peito acompanhadas de falta de ar), dispneia (falta de ar), edema pulmonar (inchaço / acúmulo de líquidos nos pulmões).

Distúrbios hepatobiliares: hepatites (inflamação do fígado), icterícia colestática (a pele e as mucosas ficam amareladas por dificuldade de eliminação das bilirrubinas), falência hepática (o fígado deixa de funcionar).

Distúrbios da pele e tecido subcutâneo: dermatite esfoliativa (inflamação de pele acompanhada de descamação), erupção maculopapular (lesões avermelhadas e um pouco elevadas na pele que deixam pele íntegra entre elas, como no sarampo), prurido (coceira), urticária (placas avermelhadas na pele, com muita coceira, que mudam de lugar espontaneamente), púrpura (manchas vermelhas ou roxas na pele, fixas, por extravasamento de sangue), angioedema (inchaço geralmente na face, pegando pálpebras e boca, pode atingir a laringe e impedir a respiração), sudorese, reações bolhosas (bolhas na pele e às vezes também nas mucosas), incluindo síndrome de Stevens-Johnson e necrólise epidérmica tóxica (muito rara).

Distúrbios musculoesqueléticos e do tecido conectivo: mialgia (dor muscular).

Distúrbios gerais e no local de administração: sede excessiva, astenia (fraqueza), edema (inchaço), reações no local de aplicação da injeção, febre e dor torácica.

Exames: tempo de sangramento aumentado (um dos exames usados para avaliar a coagulação), aumento da ureia e da creatinina séricas (substâncias que aumentam no sangue quando os rins não estão funcionando adequadamente) e testes de função hepática anormais (exames usados para avaliar o funcionamento do fígado).

Na experiência pós-comercialização, foram relatados hematomas (manchas roxas por extravasamento de sangue sob a pele) e outros sinais de sangramento de feridas associados ao uso perioperatório (próximo de uma cirurgia) de trometamol cetorolaco. Há risco potencial de sangramento em casos como retirada de parte da próstata, retirada das amídalas, cirurgias cosméticas, dentre outros.

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também à empresa através do seu serviço de atendimento.', 'Você deve utilizar trometamol cetorolaco com cuidado quando combinado com os seguintes medicamentos: anticoagulantes (que diminuem a coagulação do sangue) (por exemplo: varfarina, heparina, dextran), lítio (medicamento usado em algumas doenças psiquiátricas), diuréticos (medicamentos destinados a aumentar a quantidade de urina eliminada (por exemplo: furosemida e hidroclorotiazida), medicamentos para pressão alta (por exemplo: captopril, candesartana), metotrexato (usado em algumas doenças reumatológicas), antiplaquetários (dificultam a atividade das plaquetas e assim diminuem a coagulação do sangue) (por exemplo: ácido acetilsalicílico) e medicamentos antidepressivos (por exemplo: fluoxetina).

Abuso / dependência: trometamol cetorolaco é isento de potencial de dependência. Não foram observados sintomas de abstinência após sua descontinuação abrupta.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'EFEITOS HEMATOLÓGICOS

Trometamol cetorolaco prolonga o tempo de sangramento.

Diferentemente dos efeitos prolongados do ácido acetilsalicílico, a função plaquetária volta ao normal dentro de 24 a 48 horas depois que o tratamento com trometamol cetorolaco é suspenso.

Se você tem distúrbios de coagulação, seu médico deverá acompanhar essa situação com exames regulares.

Se você estiver fazendo o uso de anticoagulantes (por exemplo: varfarina, heparina e dextran), converse com o seu médico, pois será necessário um monitoramento rigoroso da sua coagulação (ver item Principais interações medicamentosas).

PACIENTES IDOSOS

Você poderá apresentar uma maior frequência de efeitos indesejáveis em relação aos pacientes mais jovens.

RETENÇÃO HÍDRICA / SÓDIO EM CONDIÇÕES CARDIOVASCULARES E EDEMA

Se você apresenta descompensação cardíaca, pressão alta ou condições similares, converse com o seu médico, pois você deverá usar trometamol cetorolaco com cuidado, uma vez que poderá ocorrer retenção de líquidos, pressão alta e edema (inchaço).

REAÇÕES CUTÂNEAS

Reações cutâneas graves, algumas delas fatais, incluindo descamação da pele, síndrome de Stevens-Johnson (reação a medicamentos que provoca bolhas na pele e também nas mucosas) e necrólise epidérmica tóxica (também desencadeada em geral por medicamentos, provoca necrose de grandes áreas da pele com descamação extensa), foram relatadas muito raramente em associação com o uso de AINEs. Você deve ficar atento(a), principalmente durante o início do tratamento com trometamol cetorolaco, ao aparecimento de erupção cutânea, lesão nas mucosas ou qualquer outro sinal de hipersensibilidade ou alergia. Ao primeiro sinal de reação alérgica, você deve descontinuar o uso de trometamol cetorolaco.

ULCERAÇÃO, SANGRAMENTO E PERFURAÇÃO GASTRINTESTINAIS

Esses efeitos podem ser fatais se você estiver sendo tratado com qualquer dos AINEs, incluindo trometamol cetorolaco, a qualquer tempo do tratamento, com ou sem sintomas de alerta ou história pregressa de eventos gastrintestinais graves. Nesses casos, seu médico deverá considerar a associação com agentes protetores da mucosa gástrica (por exemplo: misoprostol ou omeprazol).

Caso você seja portador de doenças inflamatórias intestinais (colite ulcerativa e doença de Crohn), o seu médico deverá administrar com cautela os AINEs, incluindo trometamol cetorolaco, pois pode ocorrer agravamento dessas doenças.

Se você tem histórico de toxicidade gastrintestinal, particularmente se for idoso, você deve relatar qualquer sintoma abdominal incomum ao seu médico (especialmente sangramento gastrintestinal). Caso ocorram sangramentos ou perfurações gastrintestinais, o tratamento com trometamol cetorolaco deverá ser suspenso. A maioria dos eventos gastrintestinais fatais associados aos anti-inflamatórios não esteroides ocorreu em pacientes debilitados e/ou idosos. O risco de ocorrer sangramento gastrintestinal clinicamente importante depende da duração do tratamento e da dose, principalmente em pacientes idosos que recebam dose diária média superior a 60 mg/dia. A história pregressa de doença ulcerativa péptica aumenta a possibilidade do desenvolvimento de complicações gastrintestinais durante a terapia com trometamol cetorolaco.

Se você está sendo tratado com corticoides orais (prednisona), anticoagulantes (por exemplo, varfarina), inibidores seletivos da recaptação de serotonina (fluoxetina) ou agentes antiplaquetários (ácido acetilsalicílico), seu médico deverá ser informado (vide item Principais interações medicamentosas).

EFEITOS CARDIOVASCULARES E CEREBROVASCULARES

Estudos clínicos e dados epidemiológicos sugerem que o uso de coxibes e alguns AINEs (principalmente em altas doses) pode estar associado a pequeno aumento do risco de eventos trombóticos arteriais (por exemplo: infarto do miocárdio e acidente vascular cerebral), principalmente em altas doses.

Apesar do cetorolaco não ter aumentado os eventos trombóticos, como infarto do miocárdio, não há dados suficientes para excluir esse risco.

Se você estiver com pressão não controlada, insuficiência cardíaca congestiva leve a moderada, doença arterial coronariana, doença arterial periférica e/ou distúrbio cérebro vascular, você só deverá ser tratado com trometamol cetorolaco após avaliação cuidadosa pelo seu médico.

Seu médico deve avaliar criteriosamente o uso do medicamento caso você apresente fatores de risco para doenças cardiovasculares, como hipertensão, hiperlipidemia (concentração elevada de colesterol ou triglicerídeo no sangue), diabetes mellitus e tabagismo.

EFEITO RENAL

Como outros AINEs, trometamol cetorolaco deve ser usado com cautela caso você seja portador de insuficiência renal (ou história de doença renal), hipovolemia (baixa do volume de sangue circulante), insuficiência cardíaca, insuficiência hepática, faça uso de diuréticos e seja idoso, pois pode ocorrer toxicidade renal. A suspensão de trometamol cetorolaco é geralmente seguida do retorno da função renal ao estado pré- tratamento.

Assim como outras drogas que inibem a síntese de prostaglandinas renais, podem ocorrer após uma dose de trometamol cetorolaco sinais de insuficiência renal, como elevação de creatinina e potássio, dentre outros.

EFEITOS ANAFILÁTICOS

Ocorrem principalmente, mas não exclusivamente, em pacientes com história de hipersensibilidade (alergia) ao ácido acetilsalicílico (alergia), outros AINEs ou trometamol cetorolaco e incluem, mas não estão limitados a: anafilaxia (reação muito grave, que inclui choque e parada respiratória, podendo levar à morte se não for tratada imediatamente), broncoespasmo (contração involuntária das paredes dos brônquios), rubor, erupção cutânea, hipotensão (pressão arterial baixa demais), edema laríngeo (inchaço da laringe, que pode prejudicar a respiração) e angioedema (inchaço que ocorre embaixo da pele, em geral na face, podendo impedir a respiração).

Trometamol cetorolaco deve ser usado com cautela se você tem história de asma (crises de chiado no peito e falta de ar), síndrome completa ou parcial de pólipo nasal (tumores benignos da mucosa nasal que entopem as narinas, dificultando a respiração), angioedema (inchaço no tecido abaixo da pele que ocorre geralmente na face, podendo afetar também a laringe e dificultar a respiração) e broncoespasmo (contração dos brônquios, que provoca chiado no peito e falta de ar).

Utilize sempre a via de administração recomendada.

EFEITOS SOBRE A CAPACIDADE DE DIRIGIR E OPERAR MÁQUINAS

Durante o tratamento com trometamol cetorolaco, você não deve dirigir veículo ou operar máquinas, pois sua habilidade e atenção podem estar prejudicadas.

FERTILIDADE

Trometamol cetorolaco e outros medicamentos de sua classe terapêutica podem prejudicar a fertilidade, e seu uso não é recomendado caso você esteja tentando engravidar.

GRAVIDEZ E AMAMENTAÇÃO

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião dentista.

Informe ao seu médico a ocorrência de gravidez na vigência do tratamento ou após seu término. Informar ao médico se estiver amamentando.

Até o momento, não há informações de que trometamol cetorolaco possa causar doping. Em caso de dúvida, consulte o seu médico.

• A duração do tratamento à base de cetorolaco se restringirá a: não deve ser superior a 2 dias

• As doses se restringirão a: Dose máxima para jovens: 90 mg/dia

• Dose máxima para idosos: 60 mg/dia

PRINCIPAIS INTERAÇÕES MEDICAMENTOSAS

Você deve utilizar trometamol cetorolaco com cuidado quando combinado com os seguintes medicamentos: anticoagulantes (que diminuem a coagulação do sangue) (por exemplo: varfarina, heparina, dextran), lítio (medicamento usado em algumas doenças psiquiátricas), diuréticos (medicamentos destinados a aumentar a quantidade de urina eliminada (por exemplo: furosemida e hidroclorotiazida), medicamentos para pressão alta (por exemplo: captopril, candesartana), metotrexato (usado em algumas doenças reumatológicas), antiplaquetários (dificultam a atividade das plaquetas e assim diminuem a coagulação do sangue) (por exemplo: ácido acetilsalicílico) e medicamentos antidepressivos (por exemplo: fluoxetina).

Abuso / dependência: trometamol cetorolaco é isento de potencial de dependência. Não foram observados sintomas de abstinência após sua descontinuação abrupta.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/trometamol-cetorolaco-injetavel-30-mg-ml', 'Vermelha', '2026-11-22T23:23:55.222Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/trometamol-cetorolaco-injetavel-30-mg-ml');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Fosfato de Codeína', 'Fosfato de Codeína', 'Codein é um medicamento que age no sistema nervoso central inibindo a sensação de dor e a resposta emocional relacionada à dor. Codein pertence a uma classe chamada analgésicos opioides.', 'Codein é indicado para o alívio da dor moderada.', 'POSOLOGIA

Administração oral para adultos:

A dose usual para adultos é de 30 mg (de 15 a 60 mg), a cada 4 ou 6 horas, para obtenção do efeito analgésico. O limite de administração é de até 360 mg/dia.

DURAÇÃO DO TRATAMENTO

Você deve tomar o medicamento pelo tempo recomendado pelo seu médico de acordo como tipo de dor que você tem,

se é uma dor que dura poucos dias (como a dor após uma cirurgia) ou se é uma dor contínua (que dura mais que um mês). Se você sentir qualquer evento adverso que consta nesta bula ou se a dor não melhorar, procure seu médico para uma nova avaliação. Ele irá decidir a melhor conduta a ser realizada, como por exemplo ajuste de dose ou substituição do medicamento.

USO EM CRIANÇAS

Codein comprimidos não é adequado para uso pediátrico. Ocorreram mortes de pacientes após adenoidectomia (remoção cirúrgica das adenoides) e amidalectomia (remoção cirúrgica das amigdalas) em crianças com apneia obstrutiva do sono (distúrbio do sono, em que a respiração é repetidamente interrompida e retomada) que receberam codeína. Isto pode ter sido resultado da conversão rápida de codeína em morfina no corpo, uma vez que há evidências de que estes pacientes eram metabolizadores ultrarrápidos de codeína. Codein solução oral e solução injetável estão contraindicados para o tratamento da dor em crianças menores de 12 anos e em menores de 18 anos para o controle da dor pós-operatória da adenoidectomia e amidalectomia. Codein comprimidos é indicado apenas para uso adulto. A codeína deve ser utilizada apenas quando os potenciais benefícios ultrapassarem os riscos.

USO EM IDOSOS

Pacientes idosos são mais susceptíveis a efeitos de depressão respiratória, pois seu metabolismo e eliminação são mais lentos. Para estes pacientes são recomendadas baixas doses ou longo intervalo entre as doses. Pacientes idosos geralmente com hipertrofia ou obstrução prostática e enfraquecimento da função renal, apresentam retenção urinária quando fazem uso de analgésico opioide.

INSUFICIÊNCIA RENAL

Recomenda-se precaução ao iniciar o tratamento com codeína em pacientes com insuficiência renal. Começar na menor dose efetiva e titular com cautela. Pacientes com insuficiência renal moderada a grave podem necessitar de ajuste de dose. Um aumento da sensibilidade à codeína pode ocorrer na presença de insuficiência renal, porém não relacionado à idade. Pacientes com insuficiência renal em hemodiálise devem ter cautela ao utilizar codeína. Sugere-se que sejam reduzidas as doses de manutenção ou aumento dos intervalos entre as doses para diminuir a probabilidade de toxicidade e maximizar a eficácia terapêutica de doses múltiplas quando necessário.

INSUFICIÊNCIA HEPÁTICA

Recomenda-se iniciar com cautela o tratamento com codeína em pacientes com insuficiência hepática. Iniciar com a menor dose eficaz e titular com cuidado. É provável que uma redução ou ajuste de dose seja necessária.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento. Não interrompa o tratamento sem o conhecimento do seu médico.', 'Você não deve usar Codein comprimidos:

• Se você for alérgico à codeína ou a outros opioides.

• Se você estiver com diarreia causada por envenenamento ou associada à colite pseudomembranosa causada por cefalosporina, lincomicina ou penicilina.

• Se você for dependente de drogas e álcool.

• Se você sentir dificuldades respiratórias ou estiver com problemas emocionais ou problemas no coração, fígado, rins, intestinos, próstata ou tireoide

• Se você estiver com aumento da pressão intracraniana ou tiver convulsões.

• Se você fez cirurgia recente do trato intestinal ou urinário.

É contraindicado o uso de Codein comprimidos em crianças, recém-nascido e bebês prematuros.

Este medicamento é contraindicado para o uso em pacientes com hipersensibilidade aos componentes da fórmula de Codein.

Codein é contraindicado durante a gravidez e lactação.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.', 'Você deve procurar o seu médico caso você apresente sinais de reações adversas além daquelas normalmente esperadas.

As reações mais comuns da codeína são dores abdominais e sonolência. Você deve relatar qualquer destes sintomas para seu médico.

Reações adversas muito comuns (ocorrem em 10% dos pacientes que utilizam este medicamento): sudorese, obstipação, náuseas, vômitos, tontura e vertigem, sedação, sonolência e dispneia.

Reações adversas raras (ocorrem entre 0,01% e 0,1% dos pacientes que utilizam este medicamento): reação anafilática.

Reações adversas com frequências desconhecidas:

• Sem informações detalhadas: náuseas e vômitos, espasmo no esfíncter de Oddi, miastenia gravis, delírio, disfunção sexual, urticária e vasodilatação provavelmente relacionada à liberação de histamina.

• Ocorrências: hipotensão, síncope, prurido, rash, hipocalcemia, obstrução intestinal, pancreatite, reação de sensibilidade cruzada, reação de hipersensibilidade, rash escarlatiniforme, pressão do líquido cefalorraquidiano

• aumentada, comprometimento da performance psicomotora, mioclonia, convulsão, ansiedade, disforia, euforia, edema pulmonar e óbito (por depressão respiratória em casos específicos).

• Relato de caso: transtorno psicótico, alucinações visuais e auditivas, insuficiência renal aguda, cólica renal, oligúria/anúria, poliúria com sede, nefrite intersticial aguda, depressão respiratória.

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também à empresa através do seu serviço de atendimento.', 'Os efeitos depressores da codeína são potencializados pela administração concomitante de outros depressores do sistema nervoso central como o álcool, sedativos, anti-histamínicos ou fármacos psicotrópicos. A quinidina pode inibir os efeitos analgésicos da codeína por impedir sua metabolização.

O uso concomitante de anticolinérgicos e codeína pode produzir íleo paralítico.

Abaixo consta um quadro expositivo das interações medicamento-planta medicinal; interações medicamento-substância química, interações medicamento-exame laboratorial e não laboratorial e as interações medicamentos-doenças:

Tipo de efeito

Gravidade

Menor

Moderada

Importante

Interação medicamento – medicamento

Aumento das concentrações plasmáticas do substrato CYP2D6

abiraterona, desvenlafaxina, eliglustate

bupropiona, darunavir, paroxetina

Aumento do prejuízo da função cognitiva e habilidades motoras, aumento do risco (e/ou efeito aditivo) de depressão do SNC, depressão respiratória, hipotensão, sedação excessiva e síncope

loxapina

alfentanila, alprazolam, anileridina, baclofen, bromazepam, carbinoxamina, carisoprodol, cetazolam, clobazam, clonazepam, clorazepato, clordiazepóxido, clorzoxazona, dantroleno, diazepam, estazolam, etclorvinol, fentanila, flibanserina, flunitrazepam, flurazepam, halazepam, hidrato de cloral, hidrocodona, hidromorfona, lorazepam, lormetazepam, meclizina, medazepam, mefenesina, mefobarbital, meperidina, meprobamato, metadona, metaxalona, metocarbamol, metoexital, midazolam, morfina, nitrazepam, ópio, orfenadrina, oxazepam, oxibato de sódio, oxicodona, oximorfona, pentazocina, prazepam, propoxifeno, quazepam, remifentanila, sufentanila, sulfato de morfina lipossomal, suvorexanto, temazepam, tizanidina, triazolam

Diminuição das concentrações plasmáticas dos metabólitos ativos da codeína e redução dos efeitos analgésicos.

quinidina

locarserina, mirabegrona

Diminuição do limiar de convulsão.

donepezila

Efeito no uso crônico: precipitação de sintomas de abstinência (cólicas abdominais, náuseas, vômitos, lacrimejamento, rinorreia,

buprenorfina, butalbital, dezocina, meptazinol, nalbufina

ansiedade, inquietação, elevação da temperatura ou piloereção).

Efeito no uso agudo: depressão do SNC aditiva e aumento do risco de depressão respiratória.

Precipitação de sintomas de abstinência a opioides; diminuição da eficácia do opioide.

naltrexona (contraindicado o uso concomitante)

Interações medicamento – planta medicinal

Aumento do risco de depressão do SNC

kava, valeriana

Redução da eficácia analgésica opioide

ginseng

Interação medicamento – substância química

Aumento da sedação

etanol

Interações medicamento-exame laboratorial e não laboratorial

Doseamento de morfina em exames antidoping

Doseamento de amilase e lipase

Interações medicamento-doenças

Motilidade gastrointestinal prejudicada, diarreia infecciosa, doença hepática, prematuros, disfunção renal, intoxicação aguda por com álcool, dependência a drogas, hipotensão, trauma craniano, tumor cerebral, desordem cerebral vascular, depressão respiratória.

x

Insuficiência adrenal, espasmo biliar, hipotireoidismo, distúrbios convulsivos, retenção urinária, arritmias.

x

• Micromedex® Solutions. Ann Arbor (MI): Truven Health Analytics Inc.; 2016. Codeine: Drug Interacions; [38 páginas].

• Kolen FC. Bundel Sport & Retch. Deventer: Kluwer; 2004.

• Drugs. Codeine Disease Interactions. [Internet]. Auckland: DrugsiteTrust; 2016.

• Drugs. Codeine Sulfate FDA prescribing information, side effects and uses. [Internet]. Auckland: DrugsiteTrust; 2016.

Informe ao seu médico ou cirurgião-dentista o aparecimento de reações indesejáveis.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'ADVERTÊNCIAS

Este fármaco pode suprimir os movimentos peristálticos no trato gastrointestinal, sendo contraindicado em pacientes com obstrução gastrointestinal, especialmente íleo paralítico devido ao risco de agravamento da obstrução.

Deve-se ter precaução no uso desse medicamento em pacientes com DPOC (Doença Pulmonar Obstrutiva Crônica), cor pulmonale ou com reserva respiratória diminuída.

Este fármaco pode causar dependência física ou psicológica com o uso crônico.

A suspensão do medicamento pode causar abstinência incluindo ansiedade, tremores, espasmos musculares, sudorese, rinorreia e delírios paranoicos.

Abuso e dependência – A codeína possui potencial de causar abuso e dependência, principalmente quando seu uso é prolongado e em altas doses.

Polimorfismo genético – No corpo, a codeína é convertida em morfina pela enzima CYP2D6. O polimorfismo genético dessa enzima resulta na incapacidade de converter codeína em morfina. Sendo assim, a codeína pode apresentar-se ineficaz em 10% da população caucasiana. Além deste fato, outro tipo de polimorfismo genético incrementa o metabolismo, tornando a população mais sensível ao efeito da codeína. Alguns pacientes que são metabolizadores ultrarrápidos da CYP2D6, convertem a codeína em morfina em uma taxa mais rápida do que o normal, resultando em níveis superiores aos normais de morfina no sangue, podendo conduzir a efeitos tóxicos, tais como dificuldade de respiração. Recomenda-se que para estes pacientes seja indicado outro tipo de analgésico para evitar o risco de ineficácia ou de severa toxicidade.

Uso odontológico – Analgésico opioide diminui ou inibe a formação de saliva, contribuindo para o desenvolvimento da cárie, doença periodontal, candidíase oral e certo desconforto.

GRAVIDEZ

Deve ser considerado o risco-benefício, uma vez que o analgésico opioide atravessa a placenta.

O uso regular durante a gravidez pode causar dependência física ao feto, causando ao recém-nascido síndrome de abstinência neonatal: convulsão, irritabilidade, choro excessivo, tremores, febre, vômitos, diarreia.

Em geral, o uso de analgésicos opioides está associado com efeitos adversos no feto, que incluem dependência física, abstinência, retardo do crescimento e depressão respiratória neonatal, malformações do trato respiratório, hérnia inguinal e umbilical estenose do piloro, defeitos do sistema circulatório e cardíaco, fenda palatina e lábio leporino e hidrocefalia, embora não se tenha estabelecida uma relação causal entre estas malformações e o uso de opioides.

A codeína é classificada como categoria C de risco na gravidez: não foram realizados estudos em animais e nem em mulheres grávidas; ou então, os estudos em animais revelaram risco, mas não existem estudos disponíveis realizados em mulheres grávidas

TRABALHO DE PARTO

O uso de analgésico durante o trabalho de parto pode causar depressão respiratória no recém- nascido.

LACTAÇÃO

O fosfato de codeína é excretado no leite materno. A relação risco-benefício deve ser considerada, uma vez que para algumas mães que são metabolizadoras ultrarrápidas da codeína em morfina, pode haver risco de morte para o lactente. Desta forma, ao prescrever codeína a uma lactante, deve-se selecionar a menor dose possível pelo menor tempo possível e monitorar de perto mãe e lactente. As lactantes devem entrar em contato com o médico ao perceber sinais nelas mesmas, como extrema sonolência, dificuldade para cuidar do bebê e no lactente, como aumento da sonolência, fraqueza, dificuldade para mamar ou respirar. Estes sinais podem ser indício de alto níveis de morfina no organismo. Se você estiver amamentando somente deverá utilizar Codein se o benefício justificar o risco potencial.

Este medicamento não deve ser utilizado durante a gravidez e a amamentação, exceto sob orientação médica. Informe ao seu médico se ocorrer gravidez ou iniciar amamentação durante o uso deste medicamento.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.

USO EM CRIANÇAS

Codein comprimidos não é adequado para uso pediátrico. Ocorreram mortes de pacientes após adenoidectomia (remoção cirúrgica das adenoides) e amidalectomia (remoção cirúrgica das amigdalas) em crianças com apneia obstrutiva do sono (distúrbio do sono, em que a respiração é repetidamente interrompida e retomada) que receberam codeína. Isto pode ter sido resultado da conversão rápida de codeína em morfina no corpo, uma vez que há evidências de que estes pacientes eram metabolizadores ultrarrápidos de codeína. Codein solução oral e solução injetável estão contraindicados para o tratamento da dor em crianças menores de 12 anos e em menores de 18 anos para o controle da dor pós-operatória da adenoidectomia e amidalectomia. Codein comprimidos é indicado apenas para uso adulto. A codeína deve ser utilizada apenas quando os potenciais benefícios ultrapassarem os riscos.

USO EM IDOSOS

Pacientes idosos são mais susceptíveis a efeitos de depressão respiratória, pois seu metabolismo e eliminação são mais lentos. Para estes pacientes são recomendadas baixas doses ou longo intervalo entre as doses. Pacientes idosos geralmente com hipertrofia ou obstrução prostática e enfraquecimento da função renal, apresentam retenção urinária quando fazem uso de analgésico opioide.

INSUFICIÊNCIA RENAL

Recomenda-se precaução ao iniciar o tratamento com codeína em pacientes com insuficiência renal. Começar na menor dose efetiva e titular com cautela. Pacientes com insuficiência renal moderada a grave podem necessitar de ajuste de dose. Um aumento da sensibilidade à codeína pode ocorrer na presença de insuficiência renal, porém não relacionado à idade. Pacientes com insuficiência renal em hemodiálise devem ter cautela ao utilizar codeína. Sugere-se que sejam reduzidas as doses de manutenção ou aumento dos intervalos entre as doses para diminuir a probabilidade de toxicidade e maximizar a eficácia terapêutica de doses múltiplas quando necessário.

INSUFICIÊNCIA HEPÁTICA

Recomenda-se iniciar com cautela o tratamento com codeína em pacientes com insuficiência hepática. Iniciar com a menor dose eficaz e titular com cuidado. É provável que uma redução ou ajuste de dose seja necessária.

EFEITOS SOBRE A CAPACIDADE DE DIRIGIR VEÍCULOS OU OPERAR MÁQUINAS

A codeína pode afetar as habilidades mentais e / ou físicas necessárias para realizar atividades potencialmente perigosas, como dirigir um carro ou operar máquinas. Dessa forma, deve-se ter cautela ao realizar estas atividades com a utilização de Codein.

Durante o tratamento com Codein, o paciente não deve dirigir veículos ou operar máquinas, pois sua habilidade e atenção podem estar prejudicadas.

PRECAUÇÕES

Informação relacionada ao doping: a codeína não está presente na lista de substâncias proibidas da agência mundial antidoping (AMA), Comitê Olímpico Internacional (COI) e Comitê Olímpico Brasileiro (COB) e seu uso não caracteriza doping. Contudo, ao ingerir codeína, no corpo ela irá se transformar em morfina, que se presente na urina pode gerar um resultado positivo e ser sugestivo de doping.

INTERAÇÕES MEDICAMENTOSAS

Os efeitos depressores da codeína são potencializados pela administração concomitante de outros depressores do sistema nervoso central como o álcool, sedativos, anti-histamínicos ou fármacos psicotrópicos. A quinidina pode inibir os efeitos analgésicos da codeína por impedir sua metabolização.

O uso concomitante de anticolinérgicos e codeína pode produzir íleo paralítico.

Abaixo consta um quadro expositivo das interações medicamento-planta medicinal; interações medicamento-substância química, interações medicamento-exame laboratorial e não laboratorial e as interações medicamentos-doenças:

Tipo de efeito

Gravidade

Menor

Moderada

Importante

Interação medicamento – medicamento

Aumento das concentrações plasmáticas do substrato CYP2D6

abiraterona, desvenlafaxina, eliglustate

bupropiona, darunavir, paroxetina

Aumento do prejuízo da função cognitiva e habilidades motoras, aumento do risco (e/ou efeito aditivo) de depressão do SNC, depressão respiratória, hipotensão, sedação excessiva e síncope

loxapina

alfentanila, alprazolam, anileridina, baclofen, bromazepam, carbinoxamina, carisoprodol, cetazolam, clobazam, clonazepam, clorazepato, clordiazepóxido, clorzoxazona, dantroleno, diazepam, estazolam, etclorvinol, fentanila, flibanserina, flunitrazepam, flurazepam, halazepam, hidrato de cloral, hidrocodona, hidromorfona, lorazepam, lormetazepam, meclizina, medazepam, mefenesina, mefobarbital, meperidina, meprobamato, metadona, metaxalona, metocarbamol, metoexital, midazolam, morfina, nitrazepam, ópio, orfenadrina, oxazepam, oxibato de sódio, oxicodona, oximorfona, pentazocina, prazepam, propoxifeno, quazepam, remifentanila, sufentanila, sulfato de morfina lipossomal, suvorexanto, temazepam, tizanidina, triazolam

Diminuição das concentrações plasmáticas dos metabólitos ativos da codeína e redução dos efeitos analgésicos.

quinidina

locarserina, mirabegrona

Diminuição do limiar de convulsão.

donepezila

Efeito no uso crônico: precipitação de sintomas de abstinência (cólicas abdominais, náuseas, vômitos, lacrimejamento, rinorreia,

buprenorfina, butalbital, dezocina, meptazinol, nalbufina

ansiedade, inquietação, elevação da temperatura ou piloereção).

Efeito no uso agudo: depressão do SNC aditiva e aumento do risco de depressão respiratória.

Precipitação de sintomas de abstinência a opioides; diminuição da eficácia do opioide.

naltrexona (contraindicado o uso concomitante)

Interações medicamento – planta medicinal

Aumento do risco de depressão do SNC

kava, valeriana

Redução da eficácia analgésica opioide

ginseng

Interação medicamento – substância química

Aumento da sedação

etanol

Interações medicamento-exame laboratorial e não laboratorial

Doseamento de morfina em exames antidoping

Doseamento de amilase e lipase

Interações medicamento-doenças

Motilidade gastrointestinal prejudicada, diarreia infecciosa, doença hepática, prematuros, disfunção renal, intoxicação aguda por com álcool, dependência a drogas, hipotensão, trauma craniano, tumor cerebral, desordem cerebral vascular, depressão respiratória.

x

Insuficiência adrenal, espasmo biliar, hipotireoidismo, distúrbios convulsivos, retenção urinária, arritmias.

x

• Micromedex® Solutions. Ann Arbor (MI): Truven Health Analytics Inc.; 2016. Codeine: Drug Interacions; [38 páginas].

• Kolen FC. Bundel Sport & Retch. Deventer: Kluwer; 2004.

• Drugs. Codeine Disease Interactions. [Internet]. Auckland: DrugsiteTrust; 2016.

• Drugs. Codeine Sulfate FDA prescribing information, side effects and uses. [Internet]. Auckland: DrugsiteTrust; 2016.

Informe ao seu médico ou cirurgião-dentista o aparecimento de reações indesejáveis.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/codein-comprimido-30-mg', 'Preta', '2026-11-22T23:23:55.224Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/codein-comprimido-30-mg');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Cefadroxila Mono-Hidratada', 'Cefadroxila Mono-Hidratada', 'A cefadroxila é um antibiótico que apresenta rápida ação de destruição de determinadas bactérias.', 'A cefadroxila está indicada para o tratamento de infecções causadas por bactérias sensíveis. A eficácia de cefadroxila manifesta-se pela melhora do estado geral do paciente, incluindo a regressão da febre, dos sinais e sintomas da infecção.', 'COMO USAR

Você deve tomar as cápsulas inteiras com líquido, por via oral somente sob prescrição médica. As cápsulas não devem ser partidas ou mastigadas. A cefadroxila pode ser administrada antes, durante ou após as refeições. Você pode tomá-lo junto com alimentos, o que diminui a intolerância gastrintestinal e melhora a absorção.

POSOLOGIA

Adultos: 1 a 2 g por dia, fracionados em duas tomadas de 12 em 12 horas.

Em algumas situações, seu médico poderá prescrever a cefadroxila poderá na dose de 1 a 2 g ao dia, em dose única diária.

Para o tratamento de faringite ou amigdalite estreptocócicas beta-hemolíticas:

• Adultos: 1 g de cefadroxila ao dia em uma única dose ou em duas doses iguais (intervalo de 12 horas) durante um mínimo de 10 dias.

Para o tratamento de infecções complicadas das vias urinárias:

• Adultos: 2 g de cefadroxila ao dia, fracionado em duas tomadas de 12 em 12 horas durante 7 a 10 dias

Posologia nos casos de disfunção renal: A dose de cefadroxila deverá ser ajustada de acordo com as taxas de clearance de creatinina para evitar acúmulo de droga. Em adultos, a dose inicial é 1 g de cefadroxila e a dose de manutenção (conforme o clearance de creatinina) é de 500 mg, nos intervalos de tempo abaixo relacionados:

Clearence de creatinina (mL/min/1,73 m)

Creatinina sérica (mg/dL)

Dose inicial

Dose subsequente

Intervalo de dosagem

25–50

1,4–2,5

1 g

500 mg

A cada 12 h

10–25

2,5–5,6

1 g

500 mg

A cada 24 h

0–10

≥5,6

1 g

500 mg

A cada 36 h

Pacientes com clearence de creatinina >50 mL/min/1,73 m podem ser tratados como pacientes com função renal normal.

Posologia em pacientes submetidos à hemodiálise: Para pacientes com disfunção renal, 48 horas antes de iniciar a diálise, 1 dose de 500 mg de cefadroxila e ao terminar a diálise outra dose de 500 mg.

Como a frequência da hemodiálise é de duas a três vezes por semana, a próxima dose deve ser administrada 48 horas antes da próxima diálise.

Mantenha esta bula em seu poder até o final do tratamento com cefadroxila. Você pode querer lê-la novamente.

Caso você esteja ajudando alguém a tomar este medicamento, leia atentamente todas as informações antes de dar a primeira dose.

Siga a orientação do seu médico, respeitando sempre os horários, as doses e a duração do tratamento.

Não interrompa o tratamento sem o conhecimento do seu médico.

Somente ele poderá avaliar a evolução do tratamento e decidir quando e como este poderá ser interrompido.

Qualquer modificação da dose somente deverá ser realizada sob orientação médica.

Não use o medicamento com o prazo de validade vencido. Antes de usar observe o aspecto do medicamento. Além de não obter o efeito desejado, as substâncias podem estar alteradas e o medicamento pode causar prejuízo para a sua saúde. A data de fabricação e o prazo de validade estão impressos na embalagem externa do produto.', 'A cefadroxila não deve ser utilizada caso você apresente alergia ou intolerância à cefadroxila ou a qualquer outro componente do produto. Também não deve ser utilizado caso você apresente alergia à penicilina e à antibióticos da classe cefalosporinas.

Antes da utilização, verifique se você possui antecedentes alérgicos, especialmente aos componentes da formulação.

Informe ao seu médico ou cirurgião-dentista o aparecimento de reações indesejáveis.

Não use medicamento sem o conhecimento do seu médico, pode ser perigoso para a sua saúde.', 'Informe ao seu médico o aparecimento de reações desagradáveis, como, por exemplo, náuseas, vômitos, diarréia e reações alérgicas.

As reações adversas da cefadroxila estão listadas de acordo com a sua frequência de incidência. Nesta lista, a frequência das reações está dividida da seguinte forma:

Categoria
Frequência

Muito comum
≥ 10%

Comum
≥ 1% e < 10%

Incomum
≥ 0,1% e < 1%

Raro
≥ 0,01% e < 0,1%

Muito raro
< 0,01%

Desconhecida
Não pode ser estimada pelos dados disponíveis

Reações comuns

• Gastrointestinal: diarreia.

• Sistema gênito-urinário: infecção por Candida (monília) na vagina e vulva.

Reações incomuns

• Gastrointestinal: dor de estômago, náuseas, vômitos, indigestão.

Reações raras

• Alterações em exames laboratoriais: alterações em testes de função hepática, teste de Coombs temporariamente positivo.

• Sistema imunológico: reação semelhante à doença do soro, reação alérgica grave (anafilaxia).

• Gastrointestinal: colite pseudomembranosa, hepatite medicamentosa, colite, icterícia.

• Sangue: hipoprotrombinemia (redução da trombina, proteína responsável pela coagulação), redução da contagem de plaquetas (células responsáveis pela coagulação), redução da contagem de granulócitos (tipo de glóbulo branco), anemia por destruição dos glóbulos vermelhos, aumento da contagem de eosinófilos (tipo de glóbulo branco), sangramento.

• Pele e anexos: eritema multiforme, urticária, coceira, síndrome de Stevens-Johnsons.

• Sintomas gerais: febre, fraqueza.

• Sistema nervoso: alucinações, convulsões, confusão mental, tontura, inquietação, dor de cabeça, formigamento.

• Sistema urinário: nefrite intersticial, insuficiência renal aguda.

• Sistema músculo-esquelético: dor nas juntas.', 'A cefadroxila não deve ser usada em combinação com quimioterápicos bacteriostáticos/antibióticos como tetraciclina, macrolídeos, sulfonamidas ou cloranfenicol, visto que poderá produzir um efeito antagônico.

A administração simultânea com probenicida pode resultar em concentrações séricas de cefadroxila mais elevadas e com duração mais prolongada.

A ocorrência de diarreia pode atrapalhar a absorção de outros medicamentos e, em consequência, poderá afetar a eficácia destes.

Como no caso de outras terapias com cefalosporínicos e penicilínicos é possível ocorrer resultados falsopositivo no teste de Coombs.

Pacientes tratados com cefadroxila podem apresentar resultados falso-positivo nos testes para glicose na urina se reagentes não-enzimáticos forem usados.

Deve-se evitar um tratamento com cefadroxila em combinação com antibióticos aminoglicosídicos, polimixina B, colistina ou altas doses de diuréticos, pois desse modo poderá aumentar o efeito tóxico aos rins.

Como em todos os tratamentos com cefalosporínicos (em doses elevadas) a combinação prolongada com anticoagulantes ou inibidores da agregação plaquetária requerem uma monitoração mais estreita dos parâmetros de coagulação com a finalidade de evitar eventuais complicações na forma de hemorragias. A cefadroxila, tal como outros antibióticos, poderá reduzir a segurança dos anticonceptivos orais.

Informe ao seu médico ou cirurgião-dentista se você estiver fazendo uso de algum outro medicamento.', 'ADVERTÊNCIAS E PRECAUÇÕES

Antes de iniciar o tratamento com cefadroxila, seu médico vai lhe perguntar sobre reações de hipersensibilidade prévias à cefadroxila, outras cefalosporinas, penicilinas ou outras drogas. Deve-se administrar o produto com cautela a pacientes sensíveis à penicilina ou outras drogas, visto que a sensibilidade cruzada entre antibióticos beta-lactâmicos têm sido claramente documentada e pode ocorrer em até 10% dos pacientes com história de alergia à penicilina. Caso ocorra reação alérgica à cefadroxila, o tratamento deve ser interrompido.

Avise seu médico caso apresente diarreia após administração de cefadroxila. Colite pseudomembranosa é uma complicação descrita com quase todos os agentes antibacterianos e pode variar de leve a grave, com risco de vida. Caso seja feito diagnóstico de colite pseudomembranose, seu médico iniciará o tratamento apropriado.

Caso você apresente problemas da função renal, a cefadroxila deverá ser usada com cautela e poderá ser feito um ajuste de dose (ver Posologia).

O uso prolongado de cefadroxila poderá resultar no supercrescimento de bactérias resistentes. Você deve observar cuidadosamente. Na ocorrência de superinfecção durante a terapia, medidas adequadas deverão ser tomadas.

A cefadroxila deverá ser prescrita com cautela para indivíduos com história de doença gastrintestinal, particularmente colite.

GRAVIDEZ E LACTAÇÃO

Estudos de reprodução realizados em camundongos e ratos não revelaram qualquer evidência de redução da fertilidade ou dano ao feto que pudessem ser atribuídos ao cefadroxila. Entretanto, não existem estudos adequados e bem controlados em mulheres grávidas.

Usar de cautela quando da administração de cefadroxila a lactantes, prematuros e recém-nascidos com idade inferior a seis semanas e meia.

POPULAÇÕES ESPECIAIS

Uso em idosos: deve-se seguir as orientações gerais descritas na bula. Contudo, o tratamento deve ser iniciado com a dose mínima.

Uso pediátrico: não é recomendado o uso de cefadroxila cápsulas em crianças menores de 12 anos. Nestes pacientes recomenda-se utilizar cefadroxila suspensão oral.

Insuficiência renal e insuficiência hepática: utilizar o medicamento cefadroxila conforme orientado no item Posologia.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação do médico ou cirurgião-dentista.

Durante o período de aleitamento materno ou doação de leite humano, só utilize medicamentos com o conhecimento do seu médico ou cirurgião-dentista, pois alguns medicamentos podem ser excretados no leite humano, causando reações indesejáveis no bebê.

INTERAÇÕES MEDICAMENTOSAS

A cefadroxila não deve ser usada em combinação com quimioterápicos bacteriostáticos/antibióticos como tetraciclina, macrolídeos, sulfonamidas ou cloranfenicol, visto que poderá produzir um efeito antagônico.

A administração simultânea com probenicida pode resultar em concentrações séricas de cefadroxila mais elevadas e com duração mais prolongada.

A ocorrência de diarreia pode atrapalhar a absorção de outros medicamentos e, em consequência, poderá afetar a eficácia destes.

Como no caso de outras terapias com cefalosporínicos e penicilínicos é possível ocorrer resultados falsopositivo no teste de Coombs.

Pacientes tratados com cefadroxila podem apresentar resultados falso-positivo nos testes para glicose na urina se reagentes não-enzimáticos forem usados.

Deve-se evitar um tratamento com cefadroxila em combinação com antibióticos aminoglicosídicos, polimixina B, colistina ou altas doses de diuréticos, pois desse modo poderá aumentar o efeito tóxico aos rins.

Como em todos os tratamentos com cefalosporínicos (em doses elevadas) a combinação prolongada com anticoagulantes ou inibidores da agregação plaquetária requerem uma monitoração mais estreita dos parâmetros de coagulação com a finalidade de evitar eventuais complicações na forma de hemorragias. A cefadroxila, tal como outros antibióticos, poderá reduzir a segurança dos anticonceptivos orais.

Informe ao seu médico ou cirurgião-dentista se você estiver fazendo uso de algum outro medicamento.', 'https://www.bulas.med.br/bula/cefadroxila-capsula-500-mg', 'Vermelha', '2026-11-22T23:23:55.225Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/cefadroxila-capsula-500-mg');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Cefepima', 'Cefepima', 'Cloridrato de cefepima pó para solução injetável é um antibiótico pertencente à classe das cefalosporinas para administração intramuscular ou intravenosa. Seu componente ativo, a cefepima, age contra uma grande variedade de bactérias, inibindo a formação da parede celular bacteriana.', 'Cloridrato de cefepima promove a melhora do paciente com o alívio dos sinais e sintomas da infecção.

ADULTOS

Cloridrato de cefepima é indicado no tratamento das infecções relacionadas a seguir, quando causadas por bactérias suscetíveis à cefepima:

• Infecções do trato respiratório inferior (traqueia, pulmões, brônquios, bronquíolos e alvéolos pulmonares), incluindo pneumonia e bronquite;

• Infecções complicadas das vias urinárias, incluindo pielonefrite (infecção nos rins);

• Infecções não complicadas das vias urinárias;

• Infecções da pele e estruturas cutâneas (unhas, pelos, glândulas sudoríparas);

• Infecções intra-abdominais, incluindo peritonite (inflamação do peritônio, a membrana que reveste parte da cavidade abdominal e vísceras) e infecções do trato biliar;

• Infecções ginecológicas;

• Septicemia (infecção grave e generalizada no organismo)

• Terapia empírica (tratamento iniciado apenas com base nos sintomas e na epidemiologia, sem que a doença tenha sido comprovada através de exames de laboratório ou outros exames) em pacientes que apresentam Neutropenia Febril (quantidade menor e anormal de um tipo de glóbulos brancos, que se relaciona com febre): monoterapia com cefepima é indicada como tratamento empírico. Em pacientes com alto risco de infecção grave (por exemplo, pacientes com histórico de recente transplante de medula óssea, com hipotensão desde o início do quadro, com doença maligna de sangue subjacente, ou com neutropenia grave ou prolongada), monoterapia antimicrobiana pode não ser apropriada. Não há dados suficientes que comprovem a eficácia da monoterapia com cefepima nestes pacientes;

• Cloridrato de cefepima também está indicado para a profilaxia cirúrgica (prevenção de infecções relacionadas a cirurgias) em pacientes submetidos à cirurgia de cólon e reto.

CRIANÇAS

Cloridrato de cefepima é indicado no tratamento, em crianças, das infecções relacionadas a seguir, quando causadas por bactérias suscetíveis à cefepima:

• Pneumonia;

• Infecções complicadas das vias urinárias, incluindo pielonefrite;

• Infecções não complicadas das vias urinárias;

• Infecções da pele e estruturas cutâneas;

• Septicemia;

• Terapia empírica em pacientes que apresentam Neutropenia Febril: monoterapia com cefepima é indicada para o tratamento empírico de pacientes neutropênicos febris. Em pacientes com alto risco de infecção grave (por exemplo, pacientes com histórico de recente transplante de medula óssea, com hipotensão desde o início do quadro, com doença maligna de sangue subjacente, ou com neutropenia grave ou prolongada), monoterapia antimicrobiana pode não ser apropriada. Não há dados suficientes que comprovem a eficácia da monoterapia com cefepima nestes pacientes.

• Meningite bacteriana (inflamação das membranas que recobrem o cérebro);

Devem ser realizados testes de cultura e suscetibilidade quando apropriados para se determinar a suscetibilidade do patógeno (agente que causa a infecção) à cefepima. A terapia empírica com cloridrato de cefepima pode ser instituída antes de se conhecer os resultados dos testes de suscetibilidade; entretanto, a antibioticoterapia deverá ser ajustada de acordo com os resultados, assim que estiverem disponíveis.', 'Cloridrato de cefepima pode ser administrado por via intramuscular ou intravenosa.

MODO DE PREPARO

Cloridrato de cefepima pó deve ser reconstituído por um profissional de saúde, utilizando-se os volumes de diluentes descritos na Tabela 1; os diluentes a serem utilizados são identificados após a tabela.

Tabela 1 – Preparo das soluções de cloridrato de cefepima

Administração
Volume de diluente a ser adicionado (mL)
Volume final aproximado no medicamento preparado (mL)
Concentração final aproximada de cefepima no medicamento preparado (mg/mL)

Intravenosa 1 g
10
11,4
90

Intravenosa 2 g
10
12,8
160

Intramuscular 1 g
3
4,4
230

ATENÇÃO: o produto preparado em capela de fluxo unidirecional (laminar) qualificado pode ser armazenado pelos tempos descritos a seguir. Para produtos preparados fora desta condição, recomenda-se o uso imediato.

CEFEPIMA 1 G – VIA INTRAMUSCULAR

Reconstituição

• Diluente: água para injetáveis, lidocaína 0,5% ou 1%, cloreto de sódio 0,9% ou glicose 5%.

• Volume: 3 mL.

• Aparência da solução reconstituída: amarelo claro a amarelo escuro.

Estabilidade após reconstituição:

• Temperatura ambiente (15 a 30°C): 4 horas.

• Sob refrigeração (2 a 8°C): 3 dias.

ATENÇÃO: como ocorre com outras cefalosporinas, a cor da solução reconstituída de cloridrato de cefepima pode escurecer durante a armazenagem, porém a potência do produto permanece inalterada.

Administração: por injeção profunda em uma grande massa muscular, como o quadrante superior externo da região glútea. Não injetar mais do que 1 g de cloridrato de cefepima em cada glúteo.

CEFEPIMA 1 G – VIA INTRAVENOSA DIRETA

É a via de administração preferencial para pacientes com infecções graves ou com risco de morte, principalmente se existe a possibilidade de choque.

Reconstituição

• Diluente: água para injetáveis, cloreto de sódio 0,9% ou glicose 5%.

• Volume: 10 mL.

• Aparência da solução reconstituída: amarelo claro a amarelo escuro.

Estabilidade após reconstituição:

• Temperatura ambiente (15 a 30°C): 4 horas

• Sob refrigeração (2 a 8°C): 3 dias.

ATENÇÃO: como ocorre com outras cefalosporinas, a cor da solução reconstituída de cloridrato de cefepima pode escurecer durante a armazenagem, porém a potência do produto permanece inalterada.

Tempo de injeção: 3 a 5 minutos. A solução reconstituída deve ser injetada diretamente na veia ou no tubo do equipo de administração, enquanto o paciente estiver recebendo líquido intravenoso compatível.

CEFEPIMA 1 G – INFUSÃO INTRAVENOSA

Reconstituição

• Diluente: água para injetáveis, cloreto de sódio 0,9% ou glicose 5%.

• Volume: 10 mL.

• Aparência da solução reconstituída: amarelo claro a amarelo escuro.

Estabilidade após reconstituição:

• Temperatura ambiente (15°C a 30°C): 4 horas

• Sob refrigeração (2°C a 8°C): 3 dias.

ATENÇÃO: como ocorre com outras cefalosporinas, a cor da solução reconstituída de cloridrato de cefepima pode escurecer durante a armazenagem, porém a potência do produto permanece inalterada.

Diluição

• Diluente: cloreto de sódio 0,9% ou glicose 5%.

• Volume: 50 a 100 mL.

• Aparência da solução diluída: amarelo claro a amarelo escuro.

• Estabilidade após diluição: Temperatura ambiente (15 a 30°C): 4 horas. Sob refrigeração (2 a 8°C): 3 dias.

ATENÇÃO: como ocorre com outras cefalosporinas, a cor da solução diluída de cloridrato de cefepima pode escurecer durante a armazenagem, porém a potência do produto permanece inalterada.

Tempo de Infusão: 30 minutos.

CEFEPIMA 1 G (SISTEMA FECHADO)

O frasco-ampola do medicamento deve ser acoplado à bolsa conforme ilustrações abaixo.

ATENÇÃO: devem ser observados cuidados usuais para evitar contaminação na montagem do sistema fechado, conforme ilustração a seguir:

• Abra o invólucro que recobre a bolsa estéril, rasgando no picote existente. Antes de utilizá-la aperte para verificar se há vazamento. Se houver, a bolsa deve ser descartada.

• Retire o flip-off do frasco. Faça assepsia da rolha com álcool 70%

• Retire a tampa, rompendo o lacre para liberar o perfurador plástico da bolsa.

• Pressione o perfurador da bolsa no frasco.

• Rompa o cone do perfurador plástico para permitir a passagem do diluente para o frasco do produto.

• Faça movimento de pressão com a bolsa nas posições A e B até que todo o produto se dissolva e retorne à bolsa de diluente.

• Rompa a borboleta através de movimento giratório.

• Instale o equipo para infusão intravenosa.

ATENÇÃO: o frasco deve ser mantido acoplado à bolsa. Isso garante o fechamento do circuito de infusão e o próprio frasco é o indicador do produto que está sendo administrado.

Reconstituição e Diluição (realizadas simultaneamente)

• Diluente: cloreto de sódio 0,9%.

• Volume: 100 mL.

• Aparência da solução diluída: amarelo claro a amarelo escuro.

Estabilidade após a diluição:

• Temperatura ambiente (15°C a 30°C): 4 horas.

• Sob refrigeração (2°C a 8°C): 3 dias.

ATENÇÃO: como ocorre com outras cefalosporinas, a cor da solução reconstituída/diluída de cloridrato de cefepima pode escurecer durante a armazenagem, porém a potência do produto permanece inalterada.

Tempo de Infusão: 30 minutos.

CEFEPIMA 2 G – VIA INTRAVENOSA DIRETA

Reconstituição

• Diluente: água para injetáveis, cloreto de sódio 0,9% ou glicose 5%.

• Volume: 10 mL.

• Aparência da solução reconstituída: amarelo claro a amarelo escuro.

Estabilidade após reconstituição:

• Temperatura ambiente (15°C a 30°C): 4 horas.

• Sob refrigeração (2°C a 8°C): 3 dias.

ATENÇÃO: como ocorre com outras cefalosporinas, a cor da solução reconstituída de cloridrato de cefepima pode escurecer durante a armazenagem, porém a potência do produto permanece inalterada.

Tempo de injeção: 3 a 5 minutos. A solução reconstituída deve ser injetada diretamente na veia ou no tubo do equipo de administração, enquanto o paciente estiver recebendo líquido intravenoso compatível.

CEFEPIMA 2 G – INFUSÃO INTRAVENOSA

Reconstituição

• Diluente: água para injetáveis, cloreto de sódio 0,9% ou glicose 5%.

• Volume: 10 mL.

• Aparência da solução reconstituída: amarelo claro a amarelo escuro.

Estabilidade após reconstituição:

• Temperatura ambiente (15°C a 30°C): 4 horas.

• Refrigeração (2°C a 8°C): 3 dias.

ATENÇÃO: como ocorre com outras cefalosporinas, a cor da solução reconstituída de cloridrato de cefepima pode escurecer durante a armazenagem, porém a potência do produto permanece inalterada.

Diluição

• Diluente: cloreto de sódio 0,9% ou glicose 5%.

• Volume: 50 a 100 mL.

• Aparência da solução diluída: amarelo claro a amarelo escuro.

Estabilidade após diluição:

• Temperatura ambiente (15°C a 30°C): 4 horas.

• Sob refrigeração (2°C a 8°C): 3 dias.

ATENÇÃO: como ocorre com outras cefalosporinas, a cor da solução diluída de cloridrato de cefepima pode escurecer durante a armazenagem, porém a potência do produto permanece inalterada.

Tempo de Infusão: 30 minutos.

CEFEPIMA 2 G (SISTEMA FECHADO)

O frasco-ampola do medicamento deve ser acoplado à bolsa conforme ilustrações acima.

ATENÇÃO: devem ser observados cuidados usuais para evitar contaminação na montagem do sistema fechado, conforme ilustração.

Reconstituição e Diluição (realizadas simultaneamente)

• Diluente: cloreto de sódio 0,9%.

• Volume: 100 mL.

• Aparência da solução diluída: amarelo claro a amarelo escuro.

Estabilidade após a diluição:

• Temperatura ambiente (15°C a 30°C): 4 horas.

• Refrigeração (2°C a 8°C): 3 dias.

ATENÇÃO: como ocorre com outras cefalosporinas, a cor da solução reconstituída/diluída de cloridrato de cefepima pode escurecer durante a armazenagem, porém a potência do produto permanece inalterada.

Tempo de Infusão: 30 minutos.

Os medicamentos de uso parenteral devem ser visualmente inspecionados antes da administração com relação a materiais estranhos, e não devem ser utilizados se estes estiverem presentes.

INCOMPATIBILIDADE:

As soluções de cloridrato de cefepima, assim como a maioria dos antibióticos betalactâmicos, não devem ser associadas com soluções de metronidazol, vancomicina, gentamicina, sulfato de tobramicina ou sulfato de netilmicina, devido à incompatibilidade física e química. Entretanto, caso a terapia concomitante com cloridrato de cefepima seja indicada, cada um desses antibióticos poderá ser administrado separadamente.

POSOLOGIA

Cloridrato de cefepima pode ser administrado por via intravenosa ou por via intramuscular. A dose e a via de administração variam de acordo com a gravidade da infecção, com a função renal e com a condição geral do paciente.

Para segurança e eficácia desta apresentação, cloridrato de cefepima injetável não deve ser administrado por vias não recomendadas. A administração deve ser somente pela via intravenosa ou intramuscular.

O tempo de duração do seu tratamento deve estar de acordo com a orientação médica.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento. Não interrompa o tratamento sem o conhecimento do seu médico.', 'Cloridrato de cefepima é contraindicado para uso por pacientes alérgicos a algum componente da formulação, a antibióticos da classe das cefalosporinas, a penicilinas ou a outros antibióticos betalactâmicos.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica.', 'Os seguintes eventos adversos foram relatados para os antibióticos da classe das cefalosporinas: síndrome de Stevens-Johnson (forma bolhosa de eritema multiforme), eritrema multiforme (distúrbio severo da pele resultante de uma reação alérgica caracterizada por bolhas e ulcerações), necrólise epidérmica tóxica (doença severa descamativa da pele), nefropatia (doença relacionada ao rim) tóxica, anemia aplásica (formação diminuída de hemácias e hemoglobinas), anemia hemolítica (maior destruição das hemácias), hemorragia e testes falso positivo para glicose na urina.

EXPERIÊNCIA CLÍNICA

Os eventos adversos mais frequentemente relatados e considerados relacionados ao cloridrato de cefepima, em estudos clínicos, foram sintomas no aparelho digestivo e reações alérgicas. Eventos adversos em relação ao cloridrato de cefepima estão relacionados a seguir.

Reações Adversas Comuns (ocorrem entre 1% e 10% dos pacientes que utilizam este medicamento):

• Reações no local da administração da infusão intravenosa ocorreram em 5,2% dos pacientes; estas reações incluíram flebite (inflamação de uma veia) que ocorreu em 2,9% dos pacientes.

• A administração intramuscular de cloridrato de cefepima foi muito bem tolerada; apenas 2,6% dos pacientes apresentaram dor ou inflamação no local da aplicação.

• Erupções da pele ocorreram em 1,8% dos pacientes.

• Diarreia ocorreu em 1,2% dos pacientes

Reações Adversas Incomuns (ocorre entre 0,1% e 1% dos pacientes que utilizam este medicamento):

• Reações de Hipersensibilidade (alergia): prurido (coceira), urticária (manchas avermelhadas que coçam).

• Gastrintestinais: náuseas, vômitos, candidíase oral (sapinho), colite (inflamação do cólon (intestino) - inclusive colite pseudomembranosa).

• Sistema nervoso central: cefaleia (dor de cabeça).

• Outros: febre, vaginite (inflamação da vagina), eritema (vermelhidão inflamatória da pele).

Reações Adversas Raras (ocorre entre 0,01% e 0,1% dos pacientes que utilizam este medicamento): Dor abdominal, constipação (dificuldade para evacuar), vasodilatação (dilatação dos vasos sanguíneos), dispneia (dificuldade para respirar), tontura, inflamação no local da administração da infusão intravenosa ocorreu em 0,1% dos pacientes, parestesia (adormecimento), prurido genital, alteração de paladar, calafrios e candidíase inespecífica.

Eventos de significância clínica que ocorreram com incidência inferior a 0,05% incluem anafilaxia e convulsões. O perfil de segurança de cloridrato de cefepima em crianças e lactentes é similar ao dos adultos.

As alterações nos testes laboratoriais que ocorreram durante estudos clínicos em pacientes com valores basais normais foram passageiras.

EXAMES LABORATORIAIS

As anormalidades nos testes laboratoriais que ocorreram durante estudos clínicos em pacientes com valores basais normais foram transitórias. Aqueles que ocorreram com incidência entre 1% e 2% foram: elevações na alanina aminotransferase (3,6%), aspartato aminotransferase (2,5%), fosfatase alcalina e bilirrubina total, anemia, eosinofilia, tempo de protrombina prolongado, tempo de tromboplastina parcial alterado (2,8%) e teste de Coombs positivo sem hemólise (18,7%). Elevações transitórias de nitrogênio ureico plasmático e/ou creatinina sérica e trombocitopenia transitória foram observadas em 0,5% a 1% dos pacientes. Leucopenia transitória e neutropenia também foram constatadas (<0,5%).

Foram relatados testes falso-positivos para glicose urinária.

EXPERIÊNCIA DE PÓS-COMERCIALIZAÇÃO – FARMACOVIGILÂNCIA

Em adição aos eventos relatados durante os estudos clínicos na América do Norte com cefepima, os seguintes eventos adversos foram relatados durante a experiência de comercialização em todo o mundo.

Assim como outras drogas desta classe, foram relatados: encefalopatia (reação adversa grave que envolve distúrbios de consciência incluindo confusão, alucinação, lentidão e coma), convulsões, mioclonia (movimentos musculares involuntários), e/ou insuficiência renal. A maioria dos casos ocorreu em pacientes com problemas renais que receberam doses de cloridrato de cefepima maiores do que a recomendada.

Assim como outras cefalosporinas, foram relatadas reações anafiláticas, incluindo choque anafilático (reação alérgica intensa e rápida que produz obstrução das vias aéreas), leucopenia (quantidade menor e anormal de leucócitos no sangue) passageira, neutropenia (quantidade menor e anormal de neutrófilos no sangue), eosinofilia (aumento de eosinófilos no sangue), agranulocitose e trombocitopenia (quantidade menor e anormal de plaquetas no sangue).

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também à empresa através do seu serviço de atendimento.', 'Você deve ter acompanhamento médico em relação à função renal se estiver utilizando altas doses de antibióticos aminoglicosídeos (como, por exemplo, a amicacina e a gentamicina) juntamente com cloridrato de cefepima, pois podem aumentar o risco de nefrotoxicidade e ototoxicidade (toxicidade auditiva).

Há casos de nefrotoxicidade com o uso de outras cefalosporinas com diuréticos potentes (como, por exemplo, a furosemida). Pode ocorrer reação falso-positiva para glicose na urina com os testes de redução de cobre (Benedict, solução de Fehling ou comprimidos Clinitest®*), mas não com os testes enzimáticos para glicosúria (p. ex.: Clinistix®*).

* Detentor da Marca registrada no FDA (Food and Drug Administration - Estados Unidos da América): Bayer Healthcare llc

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'Se você apresenta insuficiência renal (clearance da creatinina ≤ 50 mL/min), ou outras condições que comprometam a função dos rins, o seu médico irá ajustar a dose de cloridrato de cefepima para compensar o índice menor de eliminação renal. Ajustes na dose podem ser requeridos dependendo do grau da disfunção renal, gravidade da infecção e suscetibilidade dos agentes patógenos (ver “6. Como devo usar este medicamento? – Posologia”).

Durante a experiência pós-comercialização, houve casos de eventos indesejáveis sérios como encefalopatia reversível (distúrbios de consciência incluindo confusão, alucinações, lentidão e coma), mioclonia (movimentos musculares involuntários), convulsões (incluindo estado epiléptico não convulsivo), e/ou insuficiência renal (ver “8. Quais os males que este medicamento pode me causar?”). A maioria dos casos ocorreu em pacientes com problemas renais que receberam doses de cloridrato de cefepima maiores que a recomendada.

Em geral, os sintomas de toxicidade neurológica foram resolvidos após a interrupção do tratamento com cefepima e/ou após a hemodiálise. Porém, alguns destes casos tiveram efeito fatal.

Precauções: os antibióticos devem ser administrados com cautela a qualquer paciente que tenha demonstrado alguma alergia, principalmente a medicamentos. Se você apresentar reação alérgica ao usar cloridrato de cefepima, você deve interromper o tratamento e procurar seu médico imediatamente. Reações graves de hipersensibilidade (alergia) podem exigir a administração de epinefrina ou outra terapia de suporte.

Diarreia associada a Clostridium difficile (DACD) foi descrita com o uso de praticamente todos os agentes antibacterianos, incluindo cloridrato de cefepima, e pode variar quanto ao grau de gravidade, desde diarreia leve até colite fatal. DACD deve ser considerada em todos os pacientes que apresentem diarreia após o uso do antibiótico. É necessário cuidado com o histórico médico, já que foi reportada a ocorrência de DACD até dois meses depois da administração de agentes antibacterianos. Se há suspeita ou confirmação de DACD, o uso contínuo de antibióticos que não ajam diretamente contra C. difficile poderão ter a necessidade de ser descontinuados. Você deve procurar seu médico caso apresente esses sintomas.

Seu médico deve ser informado se você faz o uso de cloridrato de cefepima com outro medicamento.

Altas concentrações de cefepima inalterada são encontradas na urina, este medicamento é eliminado quase que exclusivamente por mecanismos renais, principalmente por filtração glomerular, por tal motivo, você deve ter acompanhamento médico em relação à função renal se estiver utilizando medicamentos que possam causar nefrotoxicidade (toxicidade renal), como, por exemplo, aminoglicosídeos e potentes diuréticos, juntamente com cloridrato de cefepima.

Como ocorre com outros antibióticos, o uso de cloridrato de cefepima pode levar a um supercrescimento de organismos não suscetíveis (ou seja, organismos que não respondem ao medicamento). Na ocorrência de superinfecção durante a terapia, o seu médico deverá tomar medidas apropriadas.

EFEITOS NA HABILIDADE DE DIRIGIR E USAR MÁQUINAS

Não há dados sobre o efeito que cloridrato de cefepima possa causar sobre pacientes dirigindo veículos ou operando máquinas. No entanto, possíveis reações adversas como alteração do estado de consciência, tontura, estado de confusão ou alucinação podem afetar a habilidade de dirigir e operar máquinas.

GRAVIDEZ

Não há estudos adequados e bem controlados em mulheres grávidas. Você só deverá utilizar este medicamento na gravidez sob orientação de um médico e se claramente necessário.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião dentista.

LACTAÇÃO

A cefepima é excretada no leite humano em concentrações muito baixas. A administração de cefepima deve ser feita com muita cautela a lactantes (mulheres que estejam amamentando).

Você não deve utilizar este medicamento se estiver grávida ou amamentando, a não ser sob orientação de seu médico. Informe ao seu médico se ficar grávida ou iniciar amamentação durante o uso de cloridrato de cefepima.

USO EM CRIANÇAS

A segurança de cloridrato de cefepima em lactentes (recém-nascidos) e crianças é similar à observada em adultos. Em estudos clínicos, o evento adverso mais frequentemente relatado e considerado relacionado a cloridrato de cefepima, em estudos clínicos, foi erupção da pele.

USO EM IDOSOS

Sabe-se que a cefepima é substancialmente excretada pelos rins e o risco de reações tóxicas a esta droga pode ser maior em pacientes com função renal prejudicada. Como os pacientes idosos têm maior probabilidade de terem função renal diminuída, cuidados devem ser tomados na escolha da dose e a função renal deve ser monitorada (ver “4. O que devo saber antes de usar este medicamento? – Advertências”).

Há casos de pacientes idosos com insuficiência renal que apresentaram eventos adversos sérios, incluindo encefalopatia reversível (distúrbios de consciência incluindo confusão, alucinações, torpor e coma), mioclonia, convulsões (incluindo estado epiléptico não convulsivo) e/ou insuficiência renal, ao utilizar doses usuais de cefepima (ver “4. O que devo saber antes de usar este medicamento? – Advertências” e “8. Quais os males que este medicamento pode me causar?”).

Você não deve usar este medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.

INTERAÇÕES MEDICAMENTOSAS

Você deve ter acompanhamento médico em relação à função renal se estiver utilizando altas doses de antibióticos aminoglicosídeos (como, por exemplo, a amicacina e a gentamicina) juntamente com cloridrato de cefepima, pois podem aumentar o risco de nefrotoxicidade e ototoxicidade (toxicidade auditiva).

Há casos de nefrotoxicidade com o uso de outras cefalosporinas com diuréticos potentes (como, por exemplo, a furosemida). Pode ocorrer reação falso-positiva para glicose na urina com os testes de redução de cobre (Benedict, solução de Fehling ou comprimidos Clinitest®*), mas não com os testes enzimáticos para glicosúria (p. ex.: Clinistix®*).

* Detentor da Marca registrada no FDA (Food and Drug Administration - Estados Unidos da América): Bayer Healthcare llc

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/cloridrato-de-cefepima-injetavel-1-g-e-2-g', 'Vermelha', '2026-11-22T23:23:55.226Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/cloridrato-de-cefepima-injetavel-1-g-e-2-g');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Estearato de Eritromicina', 'Estearato de Eritromicina', 'Este medicamento apresenta ação bactericida, assim sendo destrói as bactérias causadoras do processo infeccioso.', 'Este medicamento é indicado para crianças e adultos no tratamento das seguintes infecções ressaltando que culturas e testes de sensibilidade devem ser feitos:

• Infecções do trato respiratório superior de leve a moderada gravidade causadas por Streptococcus pyogenes, estreptococos do grupo viridans, Streptococcus pneumoniae, ou Haemophilus influenzae quando este medicamento for utilizado concomitantemente com doses adequadas de sulfonamidas, uma vez que nem todas as cepas de H. influenzae são sensíveis à eritromicina em concentrações normalmente alcançadas.

• Infecções do trato respiratório inferior de leve a moderada gravidade causadas por S. pyogenes, S. pneumoniae, Mycoplasma pneumoniae ou Legionella pneumophila.

• Sífilis primária causada por Treponema pallidum. A eritromicina é uma alternativa para o tratamento da sífilis primária em pacientes alérgicos à penicilina. No tratamento da sífilis primária devem ser efetuados exames do líquido cefalorraquidiano antes do tratamento e como parte do seguimento pós-terapia.

• Difteria: como adjuvante à antitoxina, na prevenção de portadores e na erradicação do micro-organismo Corynebacterium diphtheriae em portadores.

• Eritrasma: no tratamento de infecções devidas ao Corynebacterium minutissimum.

• Amebíase intestinal causada por Entamoeba histolytica. Amebíase extraentérica requer tratamento com outras drogas.

• Infecções devidas a Listeria monocytogenes.

• Infecções da pele e tecidos moles de leve a moderada gravidade causadas por S. pyogenes ou Staphylococcus aureus. Pode desenvolver resistência em estafilococos durante o tratamento.

• Coqueluche causada por Bordetella pertussis. A eritromicina é eficaz na eliminação do micro-organismo da nasofaringe. Alguns estudos clínicos sugerem que a eritromicina pode ajudar na profilaxia da coqueluche em indivíduos sensíveis expostos à doença.

• Conjuntivite do recém-nascido, pneumonia da infância e infecções urogenitais durante a gravidez causadas por Chlamydia trachomatis (vide Advertências). Quando as tetraciclinas são contraindicadas ou não toleradas, a eritromicina é indicada no tratamento de pacientes adultos com infecções uretrais não complicadas, endocervicais ou retais causadas por C. trachomatis.

• Profilaxia a curto prazo contra endocardite bacteriana (Streptococcus viridans - alfa-hemolíticos) antes de intervenções cirúrgicas ou dentárias em pacientes com histórias de febre reumática ou cardiopatia congênita ou adquirida, que sejam hipersensíveis à penicilina.

• Doença dos legionários (Legionella pneumophila): embora nenhum estudo controlado de eficácia clínica tenha sido realizado, dados in vitro e clínicos preliminares demonstram que a eritromicina pode ser eficaz no tratamento da doença dos legionários.', 'MODO DE USAR

Tomar 1 comprimido a cada 12 horas ou dose maior a critério médico, na dependência da gravidade da infecção. A duração do tratamento deve ser determinada a critério médico dependendo da indicação e da resposta individual ao tratamento. Quando indicado, devem ser feitas incisões e drenagem ou outros procedimentos cirúrgicos em conjunto com a terapia antibiótica. A atividade antibacteriana da eritromicina é maior em meio alcalino do que neutro ou ácido. Vários investigadores têm recomendado a administração concomitante de agentes urinários alcalinizantes, tal como bicarbonato de sódio, quando a eritromicina é prescrita para o tratamento de infecções urinárias.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento. Não interrompa o tratamento sem o conhecimento do seu médico.', 'Este medicamento é contraindicado a pacientes hipersensíveis ao estolato de eritromicina ou qualquer componente da formulação. Este medicamento é contraindicado para pacientes com doença no fígado já conhecida.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião- dentista.', 'O uso de eritromicina envolve um risco de hepatotoxicidade (hepatite colestática) com ou sem o aparecimento de icterícia, quando em uso por mais de 10 dias, que o contraindica para pacientes com perturbação da função hepática. Informe seu médico o aparecimento de reações desagradáveis, tais como: mal-estar, náusea, vômito, diarreia e/ou cólica abdominal. Tem ocorrido insuficiência hepática com ou sem icterícia, principalmente em adultos, relacionada com a administração de eritromicina. As reações adversas mais frequentes dos preparados de eritromicina são as gastrointestinais (por ex.: cólica abdominal e mal-estar) e estão relacionadas com a dose. Náuseas, vômitos e diarreia ocorrem em baixa frequência com as doses orais usuais. O início de sintomas de colite pseudomembranosa pode ocorrer durante ou após o tratamento antibiótico (vide Advertências). Durante o tratamento prolongado ou repetido, há possibilidade de superinfecção por bactérias não sensíveis ou fungos. Nestes casos, a medicação deverá ser suspensa e instituída terapêutica adequada. Tem ocorrido reações alérgicas leves, tais como urticária e outras erupções cutâneas. Têm sido relatadas reações alérgicas graves, incluindo anafilaxia. Há relatos isolados da ocorrência de perda de audição e/ou zumbido em pacientes recebendo eritromicina. O efeito ototóxico da substância é usualmente reversível com a interrupção. Contudo, em raras ocasiões, envolvendo a administração intravenosa, o efeito ototóxico foi irreversível. O efeito ototóxico ocorre principalmente em pacientes com insuficiência renal ou hepática e em pacientes recebendo altas doses de eritromicina. Raramente, a eritromicina foi associada com a ocorrência de arritmia ventricular, incluindo taquicardia ventricular torsade des pointes, em indivíduos com intervalos QT prolongados. Há vários relatos de estenose pilórica hipertrófica infantil em recém-nascidos recebendo vários medicamentos contendo eritromicina, incluindo estolato de eritromicina. A eritromicina deve ser usada com cuidado nos três primeiros meses de vida. (vide Gravidez).

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também à empresa através do seu serviço de atendimento.', 'Uma vez que a probenecida inibe a reabsorção tubular da eritromicina em animais, a manutenção dos níveis plasmáticos é prolongada. Foi demonstrado antagonismo entre a clindamicina e eritromicina. O uso de eritromicina em pacientes que estejam recebendo altas doses de teofilina pode estar associado a um aumento dos níveis séricos e do potencial de toxicidade da teofilina. No caso de toxicidade e/ou níveis séricos elevados de teofilina, a dose desta droga deve ser reduzida, enquanto o paciente estiver recebendo o tratamento concomitante com eritromicina. Foi relatado que a administração concomitante de eritromicina e digoxina resultou em elevados níveis séricos de digoxina. Houve relatos de aumento dos efeitos anticoagulantes quando a eritromicina foi usada junto com os anticoagulantes orais. O aumento dos efeitos anticoagulantes, devido a essa interação de drogas, podem ser mais intensos nos idosos. O uso concomitante de eritromicina e ergotamina ou dihidroergotamina foi associado em alguns pacientes com toxicidade aguda do ergot, caracterizada por grave fechamento dos vasos periféricos e alteração de sensibilidade em mãos e pés. Tem sido reportado que a eritromicina diminui a eliminação renal do triazolam e do midazolam, podendo aumentar os efeitos farmacológicos desses benzodiazepínicos. O uso de eritromicina em pacientes que estejam tomando concomitantemente drogas metabolizadas pelo sistema citocromo P450 pode estar associado com elevações dos níveis sanguíneos destas drogas. Há relatos de elevações de concentrações séricas das seguintes drogas, quando administradas concomitantemente com a eritromicina: carbamazepina, ciclosporina, hexobarbital, fenitoína, alfentanil, disopiramida, bromocriptina e inibidores da HMG-CoA redutase tais como sinvastatina e lovastatina. As concentrações sanguíneas destas e de outras drogas metabolizadas pelo sistema citocromo P450 devem ser monitoradas cuidadosamente nos pacientes que estejam recebendo eritromicina.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'ADVERTÊNCIAS E PRECAUÇÕES

Pode ocorrer mal funcionamento do fígado com ou sem icterícia (amarelamento da pele e mucosas), principalmente em adultos, relacionada com a administração do estolato de eritromicina. Pode estar acompanhada de mal-estar, enjoo, vômito, cólica abdominal e febre. Em alguns casos, a dor abdominal é tão grave que pode fazer com que seja necessário que o paciente procure um pronto-socorro. Quando ocorrer um quadro semelhante, a medicação deve ser descontinuada imediatamente. Em vista da eritromicina ser principalmente excretada pelo fígado, devem ser tomadas precauções na administração do antibiótico a pacientes com mal funcionamento do fígado. A administração do estolato de eritromicina tem sido associada com a ocorrência infrequente de hepatite colestática. Os achados de laboratórios têm sido caracterizados por valores de função hepática anormais, eosinofilia e leucocitose e também aumento das transaminases hepáticas. Os sintomas podem incluir: mal-estar, náuseas, vômitos, cólica abdominal e febre. A icterícia pode ou não estar presente. Em alguns casos, a dor abdominal intensa poderá simular a dor de cólica biliar, pancreatite, úlcera perfurada ou um problema de abdômen agudo cirúrgico. Em outros casos, sintomas clínicos e resultados dos testes de função hepática têm-se assemelhado a um quadro de icterícia obstrutiva extra-hepática; se os achados acima ocorrerem, deve-se descontinuar a medicação imediatamente. Em alguns casos, os sintomas iniciais podem aparecer após alguns dias de tratamento, mas geralmente estes sintomas só aparecem após uma ou duas semanas de tratamento contínuo. Os sintomas reaparecem rapidamente, geralmente 48 horas após a droga ser readministrada a pacientes sensíveis. A síndrome, que parece resultar de uma forma de sensibilização, ocorre principalmente em adultos e tem sido reversível quando a medicação é interrompida. Colite pseudomembranosa tem sido relatada com todo antibiótico de largo espectro, incluindo estolato de eritromicina, podendo variar de leve a gravíssima. Portanto, é importante considerar este diagnóstico em pacientes que apresentam diarreia após a administração de drogas antibacterianas. Casos leves de colite pseudomembranosa usualmente respondem com a interrupção da droga. Nos casos moderados a graves, medidas apropriadas devem ser tomadas. Rabdomiólise com ou sem insuficiência renal foi reportada em pacientes recebendo eritromicina concomitantemente com inibidores da HMG-CoA redutase tais como lovastatina e sinvastatina. Portanto, pacientes recebendo inibidores da HMG-CoA redutase e eritromicina concomitantemente devem ser cuidadosamente monitorados para os níveis de creatinina quinase e transaminase sérica.

GRAVIDEZ E LACTAÇÃO

Há vários relatos de estenose pilórica hipertrófica infantil em recém-nascidos recebendo vários medicamentos contendo eritromicina, incluindo estolato de eritromicina. A eritromicina deve ser usada com cuidado nos três primeiros meses de vida.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião- dentista.

Informe ao seu médico sobre a ocorrência de gravidez na vigência de seu tratamento ou após o seu término.

Informe se está amamentando. Estudos de dois anos, efetuados em ratos com doses orais de eritromicina, não demonstraram evidência de formação de tumores ou mutagenicidade.

Foram efetuados estudos de reprodução em ratos, camundongos e coelhos usando eritromicina e seus vários sais e ésteres em doses equivalentes a várias vezes a dose usual humana. Nenhuma evidência de danos à fertilidade ou aos fetos relacionada com a eritromicina foi relatada nestes estudos. Contudo, não há estudos adequados e bem controlados em mulheres grávidas. Devido os estudos de reprodução em animais nem sempre predizerem a resposta em humanos, essa droga só deve ser usada durante a gravidez se absolutamente necessária. O efeito do estolato de eritromicina no parto é desconhecido.

A eritromicina é excretada no leite materno; portanto, deve-se ter cuidado ao administrar essa droga a mulheres que estejam amamentando. Teste de laboratório: a eritromicina pode interferir com as determinações das transaminases (TGO e TGP), se forem usadas colorações colorimétricas com difenilhidrasina ou violeta B. Interfere também com a determinação fluorométrica de catecolaminas na urina. O tratamento com lincomicina ou clindamicina deve ser evitado em infecções devidas a micro-organismos resistentes à eritromicina. Não ingerir bebidas alcoólicas enquanto estiver sob tratamento com eritromicina.

IDOSOS

Não existem indicações especiais de uso para pacientes idosos.

INTERAÇÕES MEDICAMENTOSAS

Uma vez que a probenecida inibe a reabsorção tubular da eritromicina em animais, a manutenção dos níveis plasmáticos é prolongada. Foi demonstrado antagonismo entre a clindamicina e eritromicina. O uso de eritromicina em pacientes que estejam recebendo altas doses de teofilina pode estar associado a um aumento dos níveis séricos e do potencial de toxicidade da teofilina. No caso de toxicidade e/ou níveis séricos elevados de teofilina, a dose desta droga deve ser reduzida, enquanto o paciente estiver recebendo o tratamento concomitante com eritromicina. Foi relatado que a administração concomitante de eritromicina e digoxina resultou em elevados níveis séricos de digoxina. Houve relatos de aumento dos efeitos anticoagulantes quando a eritromicina foi usada junto com os anticoagulantes orais. O aumento dos efeitos anticoagulantes, devido a essa interação de drogas, podem ser mais intensos nos idosos. O uso concomitante de eritromicina e ergotamina ou dihidroergotamina foi associado em alguns pacientes com toxicidade aguda do ergot, caracterizada por grave fechamento dos vasos periféricos e alteração de sensibilidade em mãos e pés. Tem sido reportado que a eritromicina diminui a eliminação renal do triazolam e do midazolam, podendo aumentar os efeitos farmacológicos desses benzodiazepínicos. O uso de eritromicina em pacientes que estejam tomando concomitantemente drogas metabolizadas pelo sistema citocromo P450 pode estar associado com elevações dos níveis sanguíneos destas drogas. Há relatos de elevações de concentrações séricas das seguintes drogas, quando administradas concomitantemente com a eritromicina: carbamazepina, ciclosporina, hexobarbital, fenitoína, alfentanil, disopiramida, bromocriptina e inibidores da HMG-CoA redutase tais como sinvastatina e lovastatina. As concentrações sanguíneas destas e de outras drogas metabolizadas pelo sistema citocromo P450 devem ser monitoradas cuidadosamente nos pacientes que estejam recebendo eritromicina.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/estolato-de-eritromicina-comprimido-500-mg', 'Vermelha', '2026-11-22T23:23:55.475Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/estolato-de-eritromicina-comprimido-500-mg');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Nifedipino', 'Nifedipino', 'Adalat® cápsulas contém a substância ativa nifedipino, que pertence a um grupo de substâncias conhecidas como antagonistas do cálcio. Sua atividade mais importante é dilatar os vasos sanguíneos, diminuindo a resistência ao fluxo do sangue. Assim, o sangue corre livremente pelas artérias, e isso faz diminuir a pressão arterial. O tratamento da pressão alta diminui o risco de ocorrerem complicações no coração, no cérebro e nos vasos sanguíneos. O nifedipino serve também para tratar a dor no peito, conhecida como angina do peito, porque essa substância ativa faz com que chegue mais sangue ao cora', 'Adalat® cápsulas é indicado para o tratamento da pressão alta, da crise aguda de pressão alta e da doença arterial coronariana.

Nos pacientes com hipertensão essencial ou angina do peito crônica estável tratados com as formas de liberação rápida de nifedipino (Adalat® cápsulas 10 mg), podem ocorrer aumento dependente da dose no risco de complicações cardiovasculares (por exemplo, infarto do miocárdio) e de mortalidade. Por isso, o nifedipino (Adalat® cápsulas) somente deve ser utilizado para tratamento de pacientes com hipertensão essencial ou angina do peito crônica estável se nenhum outro tratamento for apropriado.', 'A cápsula deve ser engolida inteira com um pouco de líquido, podendo ser tomada com uma refeição ou não. Deve-se observar um intervalo de pelo menos 2 horas entre as tomadas. Suco de toronja deve ser evitado (veja item “4. O que devo saber antes de usar este medicamento? – Interações com alimentos”).

Adalat® cápsulas destina-se a tratamentos prolongados. O seu médico lhe dirá durante quanto tempo você deverá tomar Adalat® cápsulas.

Não tome mais cápsulas do que aquelas que o médico receitou.

Geralmente o médico receita 1 cápsula de Adalat® cápsulas três vezes por dia para o tratamento da pressão alta e da doença coronária. Na crise aguda de pressão alta a dose recomendada é de 1 cápsula em dose única.

Caso necessário, a dose pode ser aumentada até o máximo de 60 mg por dia.

A retirada de Adalat® cápsulas deve ser feita gradualmente. Fale com seu médico.

Na coadministração de medicamentos que estimulam ou inibam as enzimas do metabolismo pode ser necessário adaptar a dose de nifedipino ou não usá-lo (veja item “4. O que devo saber antes de usar este medicamento? – Interações medicamentosas”). Fale com seu médico.

INFORMAÇÕES ADICIONAIS PARA POPULAÇÕES ESPECIAIS

Crianças e adolescentes: A segurança e eficácia de Adalat® cápsulas não foram estabelecidas em crianças com menos de 18 anos de idade.

Idosos: A farmacocinética de Adalat® cápsulas é alterada em pacientes idosos. Assim, é necessária uma dose de manutenção mais baixa quando comparado a pacientes mais jovens.

Pacientes com disfunção hepática: Deve-se efetuar monitoramento cuidadoso em pacientes com problema no fígado leve, moderado ou grave e pode ser necessário reduzir a dose. A farmacocinética do nifedipino não foi estudada em pacientes com problema no fígado grave.

Pacientes com disfunção renal: Baseando-se em dados farmacocinéticos não há necessidade de ajuste de dose em pacientes com problemas nos rins.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento.

Não interrompa o tratamento sem o conhecimento do seu médico.” “Este medicamento não deve ser partido, aberto ou mastigado.', 'Adalat® cápsulas não deve ser usado nas situações a seguir:

• alergia ao nifedipino ou a qualquer um dos outros ingredientes do medicamento. Caso haja dúvida com relação a ter tido ou não qualquer alergia ao nifedipino, consulte seu médico;

• em caso de choque de origem cardíaca;

• paciente fazendo uso do antibiótico rifampicina, que é um medicamento que combate infecções;

• se sofrer de angina do peito instável ou se sofreu infarto agudo do miocárdio, nas últimas quatro semanas;

• antes da 20ª semana de gravidez e durante a amamentação.

Informe ao seu médico caso ocorra gravidez ou início de amamentação durante o uso deste medicamento.', 'Como qualquer medicamento, nifedipino pode provocar efeitos indesejáveis, apesar de nem todos os pacientes os apresentarem.

Se qualquer efeito adverso se tornar grave, ou se você notar qualquer efeito adverso não listado nessa bula, informe seu médico.

Segue abaixo a lista de possíveis reações adversas pela frequência.

Reação comum (≥ 1/100 a < 1/10): dor de cabeça, inchaço, dilatação dos vasos sanguíneos, prisão de ventre e mal-estar geral.

Reação incomuns (≥ 1/1.000 a < 1/100): reação alérgica, reação alérgica com inchaço na língua e na garganta, podendo dificultar a respiração (angioedema) e resultar em complicação potencialmente fatal, ansiedade, alterações do sono, vertigem, enxaqueca, tontura, tremor, alterações da visão, aceleração ou palpitações das batidas do coração, pressão muito baixa, desmaio, sangramento no nariz, congestão nasal, dor abdominal e gastrintestinal, náusea, indisposição do estômago, gases intestinais, secura na boca, alterações nos exames de sangue que avaliam a função do fígado, vermelhidão inflamatória da pele, cãibras, inchaço das articulações, urina excessiva, dificuldade ou dor ao urinar, dificuldade de ereção, dores inespecíficas e calafrios.

Reação rara (≥ 1/10.000 a < 1/1.000): coceira, urticária, aparecimento de lesões avermelhadas na pele, sensações anormais na pele como queimação, agulhadas, cócegas ou formigamento, comprometimento da sensibilidade chegando quase à anestesia, crescimento e inflamações das gengivas.

Reação de frequência desconhecida: agranulocitose (falta ou acentuada redução de leucócitos granulócitos (neutrófilos, basófilos e eosinófilos), que são subtipos específicos dos glóbulos brancos), leucopenia (diminuição de glóbulos brancos do sangue), reação anafilática/anaflactoide (do tipo reação alérgica), hiperglicemia (excesso de açúcar – glicose no sangue), hipoestesia (perda ou diminuição de sensibilidade em determinada região do organismo), sonolência, dor nos olhos, dor no peito (angina do peito), falta de ar, vômito, mau funcionamento do esfíncter do esôfago, pele amarelada devido à presença de bile no sangue, inflamação grave da pele (necrólise epidérmica tóxica), reação alérgica pela luz, manchas roxas na pele, dores articulares e musculares.

Nos pacientes em diálise, com hipertensão maligna e hipovolemia, pode ocorrer queda significativa da pressão devido à vasodilatação.

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também à empresa através do seu serviço de atendimento.', 'Medicamentos que podem ter seu efeito alterado se tomados com nifedipino:

• medicamentos para o tratamento da pressão alta, como diuréticos, beta- bloqueadores, inibidores da ECA, antagonistas do receptor de angiotensina II (AT-1), outros antagonistas de cálcio, agentes bloqueadores alfa-adrenérgicos, inibidores da PDE5 e alfa-metildopa, podem ter o seu efeito aumentado;

• beta-bloqueadores, usados para tratar a pressão alta e algumas doenças do coração, podem provocar queda muito forte da pressão e piorar o funcionamento do coração. Esses pacientes devem ser monitorados com muito cuidado;

• digoxina, usada para tratar doenças do coração, principalmente insuficiência cardíaca, pode ter seu efeito aumentado, podendo ser necessário ajuste de dose;

• quinidina, usada para o tratamento das alterações dos batimentos do coração: pode ser necessário ajuste de dose ao se iniciar ou terminar o tratamento com nifedipino, é preciso consultar o médico a respeito;

• tacrolimo, usado em doentes transplantados: quando for administrado junto com nifedipino poderá ser necessário reduzir a dose de tacrolimo.

Medicamentos que alteram o efeito do nifedipino se tomados juntos: Os seguintes medicamentos reduzem o efeito de nifedipino:

• rifampicina (antibiótico): não pode ser administrada junto com nifedipino; fenitoína, carbamazepina, fenobarbital (antiepilépticos).

Os seguintes medicamentos podem aumentar o efeito de nifedipino:

• antibióticos macrolídeos (exceto azitromicina), p. ex. eritromicina; antivirais usados para o tratamento de AIDS, p. ex. ritonavir; antifúngicos azólicos, p. ex. cetoconazol; antidepressivos como fluoxetina e nefazodona; quinupristina/dalfopristina (antibióticos); ácido valproico (antiepiléptico); cimetidina (para o tratamento de úlceras do estômago ou do duodeno); cisaprida (para o tratamento de certas doenças do estômago e do intestino).

Interação com Alimentos

Não se deve tomar suco de toronja, conhecida também como grapefruit, enquanto estiver em tratamento com nifedipino, pois poderá ocorrer uma queda maior da pressão. Após o consumo regular de suco de toronja, o efeito pode perdurar por, pelo menos, três dias após a última ingestão do suco.

Outras Formas de Interação

O nifedipino pode causar um falso aumento dos valores de ácido vanililmandélico urinário determinados por alguns métodos laboratoriais.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento.

Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'ADVERTÊNCIAS E PRECAUÇÕES

Adalat® cápsulas deve ser usado com precaução nos seguintes casos:

• em pacientes que tem pressão muito baixa (pressão máxima abaixo de 90 mmHg) ou mau funcionamento do coração, o que é chamado insuficiência cardíaca, ou em pessoas que tenham um estreitamento importante da valva aórtica, conhecido como estenose aórtica grave;

• o tratamento com nifedipino em cápsulas de ação rápida pode causar uma queda excessiva da pressão arterial, com taquicardia reflexa, o que pode resultar em complicações cardiovasculares.

• em pacientes que observarem um agravamento da angina do peito preexistente, em especial no início do tratamento, devendo o seu médico ser informado imediatamente. Em casos muito raros, o uso de nifedipino de liberação rápida pode causar angina do peito, especialmente no início do tratamento. Têm-se documentado casos isolados de infarto do miocárdio, ainda que não seja possível diferenciá-lo da história natural da doença subjacente;

• em pacientes com doença do fígado leve, moderada ou grave, pois, poderá ser necessário reduzir a dose do medicamento. A farmacocinética do nifedipino não foi estudada em pacientes com doença do fígado grave. Portanto, o nifedipino deve ser utilizado com cautela em pacientes com doença do fígado grave;

• uso após a 20ª semana de gestação: exige uma avaliação individual cuidadosa do risco-benefício e somente será considerado se outras opções de tratamento não forem indicadas ou forem ineficazes.

No caso de mulheres grávidas, monitorar cuidadosamente a pressão arterial, inclusive ao se administrar o nifedipino com sulfato de magnésio por via intravenosa, devido à possibilidade de uma queda acentuada da pressão arterial, o que pode ser prejudicial à mãe e ao feto. Converse com seu médico.

Alguns medicamentos como antibióticos macrolídeos (p.ex. eritromicina), antivirais usados para o tratamento de AIDS (p.ex. ritonavir), antimicóticos azólicos (p.ex. cetoconazol), antidepressivos nefazodona e fluoxetina, quinupristina/dalfopristina, ácido valproico e cimetidina podem aumentar as concentrações de nifedipino no sangue. Portanto, o médico deve monitorar sua pressão arterial se você estiver tomando um desses medicamentos juntamente com o nifedipino e, se necessário, reduzir a dose de nifedipino.

Para verificar o uso em Populações Especiais veja item “6. Como devo usar este medicamento?”.

EFEITOS SOBRE A CAPACIDADE DE DIRIGIR VEÍCULOS E OPERAR MÁQUINAS

Reações ao medicamento, que variam em intensidade de indivíduo para indivíduo, podem prejudicar a capacidade de dirigir veículos ou de operar máquinas. Isso pode ocorrer, sobretudo, no início do tratamento, na mudança de medicação ou sob ingestão alcoólica concomitante.

GRAVIDEZ E AMAMENTAÇÃO

O nifedipino é contraindicado antes da 20ª semana de gravidez (veja item “3. Quando não devo usar este medicamento?”).

“Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.”

O nifedipino pode ser a causa de insucesso na fertilização artificial em homens que estejam tomando o medicamento e não apresentem outras causas que justifiquem esse insucesso.

O nifedipino é eliminado no leite materno. Suspenda a amamentação se precisar usar nifedipino.

INTERAÇÕES MEDICAMENTOSAS

Medicamentos que podem ter seu efeito alterado se tomados com nifedipino:

• medicamentos para o tratamento da pressão alta, como diuréticos, beta- bloqueadores, inibidores da ECA, antagonistas do receptor de angiotensina II (AT-1), outros antagonistas de cálcio, agentes bloqueadores alfa-adrenérgicos, inibidores da PDE5 e alfa-metildopa, podem ter o seu efeito aumentado;

• beta-bloqueadores, usados para tratar a pressão alta e algumas doenças do coração, podem provocar queda muito forte da pressão e piorar o funcionamento do coração. Esses pacientes devem ser monitorados com muito cuidado;

• digoxina, usada para tratar doenças do coração, principalmente insuficiência cardíaca, pode ter seu efeito aumentado, podendo ser necessário ajuste de dose;

• quinidina, usada para o tratamento das alterações dos batimentos do coração: pode ser necessário ajuste de dose ao se iniciar ou terminar o tratamento com nifedipino, é preciso consultar o médico a respeito;

• tacrolimo, usado em doentes transplantados: quando for administrado junto com nifedipino poderá ser necessário reduzir a dose de tacrolimo.

Medicamentos que alteram o efeito do nifedipino se tomados juntos: Os seguintes medicamentos reduzem o efeito de nifedipino:

• rifampicina (antibiótico): não pode ser administrada junto com nifedipino; fenitoína, carbamazepina, fenobarbital (antiepilépticos).

Os seguintes medicamentos podem aumentar o efeito de nifedipino:

• antibióticos macrolídeos (exceto azitromicina), p. ex. eritromicina; antivirais usados para o tratamento de AIDS, p. ex. ritonavir; antifúngicos azólicos, p. ex. cetoconazol; antidepressivos como fluoxetina e nefazodona; quinupristina/dalfopristina (antibióticos); ácido valproico (antiepiléptico); cimetidina (para o tratamento de úlceras do estômago ou do duodeno); cisaprida (para o tratamento de certas doenças do estômago e do intestino).

Interação com Alimentos

Não se deve tomar suco de toronja, conhecida também como grapefruit, enquanto estiver em tratamento com nifedipino, pois poderá ocorrer uma queda maior da pressão. Após o consumo regular de suco de toronja, o efeito pode perdurar por, pelo menos, três dias após a última ingestão do suco.

Outras Formas de Interação

O nifedipino pode causar um falso aumento dos valores de ácido vanililmandélico urinário determinados por alguns métodos laboratoriais.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento.

Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/adalat', 'Vermelha', '2026-11-22T23:23:56.476Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/adalat');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Fumarato de Bisoprolol', 'Fumarato de Bisoprolol', 'O hemifumarato de bisoprolol tem como substância ativa o bisoprolol, que pertence a um grupo de medicamentos chamados betabloqueadores. Estes medicamentos alteram a resposta do organismo a alguns impulsos nervosos, principalmente no coração. Como resultado, o bisoprolol diminui o ritmo cardíaco, aumentando a eficiência do coração no bombeamento de sangue para todo o corpo. Ao mesmo tempo, diminui as necessidades de sangue e de consumo de oxigênio do coração.', 'O hemifumarato de bisoprolol, nas concentrações de 5 e 10 mg, é indicado no tratamento da angina pectoris ou da pressão alta. O hemifumarato de bisoprolol, em todas as suas concentrações, também é indicado na insuficiência cardíaca crônica estável, em combinação com outros medicamentos adequados ao tratamento desta doença (inbidores da ECA, diuréticos e, opcionalmente, glicosídeos cardíacos).', 'O tratamento com hemifumarato de bisoprolol requer acompanhamento regular por parte do seu médico, sendo particularmente necessário no início do tratamento e durante aumentos da dose. Tome o comprimido com um pouco de líquido de manhã, com ou sem alimentos. O tratamento com hemifumarato de bisoprolol é habitualmente de longa duração.

Tratamento de hipertensão (pressão alta) ou angina pectoris

Em todos os casos, a posologia deve ser ajustada individualmente, em especial em função da sua pulsação e do sucesso do tratamento. Para ambas as indicações, a dose habitual é de 1 comprimido de hemifumarato de bisoprolol 5 mg uma vez por dia. Se necessário, a dose pode ser aumentada para 1 comprimido de hemifumarato de bisoprolol 10 mg uma vez por dia. A dose máxima recomendada é de 20 mg uma vez por dia (4 comprimidos de hemifumarato de bisoprolol 5 mg ou 2 comprimidos de hemifumarato de bisoprolol 10 mg).

Tratamento de insuficiência cardíaca crônica estável

É recomendável que o médico que lhe atenda tenha experiência no tratamento de insuficiência cardíaca crônica.

O início do tratamento com hemifumarato de bisoprolol necessita de uma fase de aumento gradual da dose e requer acompanhamento regular pelo médico. A dose inicial recomendada é 1 comprimido de hemifumarato de bisoprolol 1,25 mg uma vez ao dia. Dependendo da tolerância individual, a dose é aumentada gradualmente, do modo que se segue:

• 1ª semana: 1,25 mg uma vez ao dia (1 comprimido de hemifumarato de bisoprolol 1,25 mg); caso essa dose seja bem tolerada, aumentar para:

• 2ª semana: 2,5 mg uma vez ao dia ( 1 comprimido de hemifumarato de bisoprolol 2,5 mg); caso essa dose seja bem tolerada, aumentar para:

• 3ª semana: 3,75 mg uma vez ao dia (1 comprimido de hemifumarato de bisoprolol 1,25 mg e 1 comprimido de hemifumarato de bisoprolol 2,5 mg) ; caso essa dose seja bem tolerada, aumentar para:

• 4ª a 7ª semana: 5 mg uma vez ao dia (1 comprimido de hemifumarato de bisoprolol 5 mg); caso essa dose seja bem tolerada, aumentar para:

• 8ª a 11ª semana: 7,5 mg uma vez ao dia (1 comprimido de hemifumarato de bisoprolol 2,5 mg e 1 comprimido de hemifumarato de bisoprolol 5 mg); caso essa dose seja bem tolerada, aumentar para:

• 12ª semana em diante: 10 mg uma vez ao dia (1 comprimido de hemifumarato de bisoprolol 10 mg) como manutenção do tratamento.

A dose máxima recomendada é de 10 mg de bisoprolol uma vez ao dia. Dependendo do modo como tolerar o medicamento, seu médico pode decidir prolongar o intervalo de tempo entre os aumentos da dose. Se a sua doença se agravar ou se deixar de tolerar o medicamento, pode ser necessário reduzir a dose novamente ou interromper o tratamento. Em alguns pacientes pode ser suficiente uma dose de manutenção inferior a 10 mg ao dia. Normalmente, se tiver de parar o tratamento por completo, o seu médico irá recomendar reduzir a dose gradualmente, uma vez que, de outro modo, a sua doença poderá se agravar.

Uso em crianças

Como não existe experiência com o uso do bisoprolol em uso pediátrico, hemifumarato de bisoprolol não pode ser recomendado para crianças.

Uso em idosos

Não é necessário fazer ajustes da dose em idosos.

Uso em pacientes com problemas no fígado ou rins

No tratamento da pressão alta e da angina pectoris normalmente não é necessário efetuar ajustes da dose em pacientes com insuficiência renal ou hepática de gravidade ligeira a moderada. Em pacientes com insuficiência renal grave (clearance de creatina < 20 ml/min) ou insuficiência hepática grave não se deve exceder uma dose diária de 10 mg de bisoprolol.

Para o tratamento de insuficiência cardíaca crônica estável, não há informações sobre o comportamento do hemifumarato de bisoprolol na presença de deficiência renal ou hepática. Assim, nestes pacientes, o aumento da dose deve ser realizado com maior cautela.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento.

Não interrompa o tratamento sem o conhecimento do seu médico.

Este medicamento não deve ser partido ou mastigado.', 'Você não pode tomar hemifumarato de bisoprolol nas seguintes situações:

• hipersensibilidade (alergia) ao bisoprolol ou a qualquer outro componente da fórmula;

• asma grave;

• problemas graves da circulação sanguínea nos membros (tal como Síndrome de Raynaud), que pode fazer com que os dedos das mãos e dos pés fiquem dormentes, pálidos ou azuis;

• feocromocitoma não tratado, que é um tumor raro da glândula suprarrenal;

• acidose metabólica, que é uma condição na qual o sangue está muito ácido;

Não tome hemifumarato de bisoprolol se tiver algum dos seguintes problemas no coração:

• insuficiência cardíaca aguda;

• agravamento da insuficiência cardíaca que requeira tratamento intravenoso com medicamentos que aumentem a força de contração do coração;

• pressão baixa, que cause sintomas;

• ritmo cardíaco lento, que cause sintomas;

• determinadas doenças cardíacas que provocam ritmo cardíaco muito lento ou batimentos cardíacos irregulares;

• choque cardiogênico, que é uma condição cardíaca aguda grave que provoca queda da pressão e insuficiência circulatória.', 'Como todos os medicamentos, hemifumarato de bisoprolol pode causar algumas reações desagradáveis; no entanto, estas não ocorrem em todas as pessoas. Caso você tenha uma reação alérgica, deve parar de tomar o medicamento.

As reações secundárias mais graves estão relacionadas com a função do coração e ao tratamento da insuficiência cardíaca:

• diminuição do ritmo cardíaco (reação muito comum, ocorrendo em 10% ou mais em pacientes com insuficiência crônica cardíaca estável; reação incomum, ocorrendo entre 0,1% e 1% em pacientes com hipertensão ou angina pectoris);

• agravamento da insuficiência cardíaca (reação comum, ocorrendo entre 1% e 10% em pacientes com insuficiência crônica cardíaca estável; reação incomum, ocorrendo entre 0,1% e 1% em pacientes com hipertensão ou angina pectoris);

• batimentos cardíacos lentos ou irregulares (reação incomum, ocorrendo entre 0,1% e 1% dos pacientes que utilizam este medicamento).

Caso sinta tonturas ou fraqueza, ou se tiver dificuldade respiratória, consulte o seu médico o mais rapidamente possível.

Outras reações secundárias são listadas a seguir de acordo com a frequência com que podem ocorrer:

Reações comuns (ocorrem entre 1% e 10% dos pacientes que utilizam este medicamento): cansaço, sensação de fraqueza, (em pacientes com insuficiência cardíaca crônica estável) tonturas, dores de cabeça; sensação de frio ou dormência nas mãos ou nos pés; pressão baixa; problemas de estômago ou de intestinos como náuseas, vômitos, diarreia ou prisão de ventre.

Reações incomuns (ocorrem entre 0,1% e 1% dos pacientes que utilizam este medicamento): perturbações do sono; depressão; sensação de fraqueza (em pacientes com hipertensão ou angina pectoris); tontura ao levantar; problemas respiratórios em pacientes com asma ou doença pulmonar crônica; fraqueza muscular, cãibras musculares.

Reações raras (ocorrem entre 0,01% e 0,1% dos pacientes que utilizam este medicamento): problemas de audição; corrimento nasal alérgico; redução do fluxo lacrimal; inflamação do fígado que pode provocar amarelecimento da pele ou do branco dos olhos; alterações dos resultados de exames da função do fígado ou dos níveis de gordura no sangue, com valores diferentes dos normais; reações do tipo alérgico como coceira, rubor, erupção cutânea; dificuldade de ereção; pesadelos, alucinações.

Reações muito raras (ocorrem em menos de 0,01% dos pacientes que utilizam este medicamento): irritação e vermelhidão dos olhos (conjuntivite); queda de cabelo; aparecimento ou agravamento de erupção cutânea com descamação (psoríase), erupção do tipo psoríase.

Frequência não conhecida: síncope.

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também à empresa através do seu serviço de atendimento.', NULL, 'Advertências e precauções

Se você apresentar alguma das seguintes condições, informe o médico antes de iniciar tratamento com hemifumarato de bisoprolol, pois ele poderá querer tomar alguns cuidados especiais (por exemplo, administrar um tratamento adicional ou realizar controles com maior frequência):

• diabetes;

• jejum rigoroso;

• determinadas doenças cardíacas como perturbações do ritmo cardíaco ou dor grave do peito em repouso (angina de Prinzmetal);

• problemas do fígado ou rins;

• problemas menos graves de circulação sanguínea nos seus membros;

• doenças brônquicas (asma ou doenças aéreas obstrutivas crônicas menos graves). Embora betabloqueadores cardiosseletivos (beta1) possam apresentar menos efeitos sobre a função pulmonar do que os betabloqueadores não-seletivos, como acontece com todos os betabloqueadores, estes devem ser evitados em pacientes com doenças obstrutivas das vias aéreas, a menos que existam razões clínicas relevantes para seu uso. Nessas situações, hemifumarato de bisoprolol pode ser usado com cautela;

• psoríase ou histórico pessoal de psoríase (doença da pele em que aparecem manchas vermelhas, frequentemente com escamas de cor prateada);

• tumor da glândula suprarrenal (feocromocitoma);

• doenças da tiroide.

Além disso, informe o médico se estiver em vias de ser submetido a:

• terapia de dessensibilização (vacinas para alergias), uma vez que hemifumarato de bisoprolol pode aumentar a probabilidade de ter uma reação alérgica ou que essa reação seja mais grave;

• anestesia (por exemplo, para cirurgia), uma vez que Hemifumarato de bisoprolol pode influenciar o modo como o seu organismo reage a esta situação.

Gravidez e amamentação

Existe o risco da utilização de hemifumarato de bisoprolol durante a gravidez poder ser prejudicial para o bebê.

Se estiver grávida ou planejando engravidar, informe o médico. Ele decidirá se pode tomar hemifumarato de bisoprolol durante a gravidez.

Não se sabe se bisoprolol é excretado no leite materno. Assim, não se recomenda o uso de hemifumarato de bisoprolol durante a amamentação.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista

Direção de veículos e operação de máquinas

A sua capacidade de dirigir ou de operar de máquinas pode ser afetada, dependendo do modo como tolera o medicamento. Tenha um cuidado especial no início do tratamento, quando a dose é aumentada ou quando a medicação é trocada, assim como quando em combinação com bebidas alcoólicas.

Interações com alimentos e outros medicamentos

A ingestão com alimentos não prejudica a ação do hemifumarato de bisoprolol.

Não tome os seguintes medicamentos com hemifumarato de bisoprolol sem a recomendação do seu médico:

Na indicação de insuficiência cardíaca crônica estável 

• medicamentos utilizados para tratar batimentos cardíacos irregulares ou anormais (medicamentos antiarrítmicos Classe I como quinidina, disopiramida, lidocaína, fenitoína, flecainida, propafenona).

Em todas as indicações

• medicamentos utilizados para tratar pressão alta, angina pectoris ou batimentos cardíacos irregulares (antagonistas do cálcio como o verapamil e o diltiazem);

• medicamentos utilizados no tratamento da pressão alta, tais como clonidina, metildopa, moxonodina, rilmenidina (no entanto, não pare de tomar estes medicamentos sem primeiro consultar o seu médico).

Fale com o médico antes de tomar os seguintes medicamentos com hemifumarato de bisoprolol, pois ele necessitará acompanhar o seu estado com mais frequência:

Na indicação de hipertensão ou anginas pectoris

• medicamentos utilizados para tratar batimentos cardíacos irregulares ou anormais (medicamentos antiarrítmicos Classe I como quinidina, disopiramida, lidocaína, fenitoína, flecainida, propafenona);

Em todas as indicações

• medicamentos utilizados para tratar pressão alta ou angina pectoris (antagonistas do cálcio do tipo di- hidropiridina, tais como nifedipino, felodipino ou anlodipino);

• medicamentos utilizados para tratar os batimentos cardíacos irregulares ou anormais (medicamentos antiarrítmicos de Classe III, tal como amiodarona);

• betabloqueadores de aplicação local (tais como colírios à base de timolol para o tratamento do glaucoma);

• medicamentos utilizados para tratar, por exemplo, a doença de Alzheimer ou glaucoma (parassimpaticomiméticos como tacrina ou carbacol) ou medicamentos que são utilizados para tratar problemas cardíacos agudos (simpaticomiméticos como isoprenalina e dobutamina);

• medicamentos antidiabéticos, incluindo insulina;

• agentes anestésicos (por exemplo, durante cirurgia);

• glicosídeos cardíacos, utilizados no tratamento da insuficiência cardíaca;

• medicamentos anti-inflamatórios não esteroidais (AINEs), utilizados para tratar artrite, dor ou inflamação (tais como ibuprofeno ou diclofenaco);

• Epinefrina, medicamento usado para tratar reações alérgicas graves com risco de vida e paradas cardíacas;

• qualquer medicamento que possa diminuir a pressão sanguínea como efeito desejado ou não desejado, como os anti-hipertensivos, determinados medicamentos para a depressão (antidepressivos tricíclicos como imipramina ou amitriptilina), determinados medicamentos utilizados no tratamento da epilepsia ou usados durante a anestesia (barbitúricos como o fenobarbital), ou determinados medicamentos para tratar doenças mentais caracterizadas pela perda de contato com a realidade (fenotiazinas como a levomepromazina);

• mefloquina, utilizada na prevenção ou tratamento da malária;

• medicamentos para o tratamento da depressão denominados inibidores da monoaminoxidase (exceto os inibidores da MAO-B), tal como a moclobemida.

Este medicamento pode causar doping.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento.

Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/hemifumarato-de-bisoprolol', 'Vermelha', '2026-11-22T23:23:56.477Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/hemifumarato-de-bisoprolol');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Etexilato de Dabigatrana', 'Etexilato de Dabigatrana', 'PRADAXA age inibindo uma substância chamada trombina, que é umas das responsáveis pela formação do coágulo sanguíneo. Seu início de ação é rápido, cerca de 2 horas após a administração.', 'PRADAXA é indicado para prevenir a formação e migração de coágulos nas veias (tromboembolismo venoso) em pacientes submetidos à cirurgia ortopédica de grande porte; para tratar coágulos em veias profundas (trombose venosa profunda) e nos pulmões (embolia pulmonar) em pacientes que receberam anticoagulante injetável por 5-10 dias e para prevenir sua recorrência e morte relacionada em pacientes já tratados anteriormente.

Indicado também para prevenção do acidente vascular cerebral (AVC ou derrame), embolia sistêmica (migração de coágulos do coração para a circulação, provocando obstrução de artérias) e redução do risco de morte em pacientes com fibrilação atrial (doença que altera o ritmo dos batimentos cardíacos).', 'A cápsula de PRADAXA pode ser ingerida com ou sem alimentos e com um copo de água para facilitar o trânsito do medicamento até o estômago. Em caso de desenvolvimento de sintomas gastrointestinais, é recomendado administrar PRADAXA com alimentos, maiores informações podem ser obtidas com seu médico.

Este medicamento não deve ser aberto ou mastigado.

A dose de PRADAXA será prescrita por seu médico e vai depender da indicação de uso e características individuais como idade e condições de funcionamento de seus rins.

PREVENÇÃO DE TROMBOEMBOLISMO VENOSO EM PACIENTES SUBMETIDOS À CIRURGIA ORTOPÉDICA DE GRANDE PORTE

A dose recomendada é de 220 mg (2 cápsulas de 110 mg) uma vez ao dia.

PREVENÇÃO DE TROMBOEMBOLISMO VENOSO APÓS CIRURGIA DE SUBSTITUIÇÃO DA ARTICULAÇÃO DO JOELHO OU QUADRIL

O tratamento deve ser iniciado por via oral dentro de 1 a 4 horas após o término da cirurgia com uma única cápsula de 110 mg e continuar com 2 cápsulas de 110 mg uma vez ao dia, por um total de 10 dias no caso de substituição da articulação do joelho, e por 28 a 35 dias no caso de substituição da articulação do quadril. Quando houver risco de sangramento, o médico poderá retardar o início do tratamento. Se o tratamento não for iniciado no dia da cirurgia, deve ser iniciado com 2 cápsulas uma vez ao dia.

PACIENTES ESPECIAIS

Para prevenção de tromboembolismo venoso após cirurgia ortopédica de grande porte, a dose deve ser reduzida para 150 mg uma vez ao dia (2 cápsulas de 75 mg) se você tiver mau funcionamento moderado dos rins, se estiver usando amiodarona, quinidina ou verapamil. Deve-se evitar iniciar o tratamento com verapamil em pacientes submetidos à cirurgia ortopédica de grande porte e já tratados com PRADAXA, assim como o início simultâneo de tratamento com tais medicamentos. Para prevenção de tromboembolismo venoso após cirurgia de substituição da articulação do joelho ou quadril, o tratamento deve ser iniciado por via oral dentro de 1 a 4 horas após o término da cirurgia com uma única cápsula de 75 mg e continuar com 2 cápsulas de 75 mg uma vez ao dia, por um total de 10 dias no caso de substituição da articulação do joelho, e por 28 a 35 dias no caso de substituição da articulação do quadril.

Não é necessário ajustar a dose se você é idoso, exceto se houver declínio da função renal relacionado à idade (considerar a dosagem para mau funcionamento dos rins).

Se seu médico irá trocar PRADAXA por um anticoagulante injetável, deverá aguardar 24 horas após a última dose antes de mudar o tratamento.

TRATAMENTO DE TROMBOSE VENOSA PROFUNDA E EMBOLIA PULMONAR

A dose diária recomendada é de 300 mg (1 cápsula de 150 mg 2 vezes ao dia) após tratamento com anticoagulante parenteral por pelo menos 5 dias. O tratamento deve continuar por no mínimo 6 meses.

Pacientes especiais: não é necessário ajustar a dose se você tiver mau funcionamento leve a moderado dos rins, se estiver usando amiodarona, quinidina, verapamil ou se for idoso.

Também não é necessário ajustar a dose se você tiver apenas 1 destes fatores de risco: ≥75 anos, mau funcionamento moderado dos rins ou sangramento gastrintestinal prévio; porém, se você tiver mais de 1 destes fatores, seu médico irá avaliar se o beneficio de usar PRADAXA será maior que os riscos de sangramento.

Se seu médico irá trocar PRADAXA por um anticoagulante injetável, deverá aguardar 12 horas após a última dose antes de mudar o tratamento.

Se seu médico for trocar PRADAXA por um anticoagulante injetável ou um antagonista de vitamina K, ou substituir um antagonista de vitamina K (anticoagulante oral, como varfarina) por PRADAXA, você deverá seguir estritamente suas orientações.

PREVENÇÃO DE RECORRÊNCIA TROMBOSE VENOSA PROFUNDA E EMBOLIA PULMONAR

A dose diária recomendada é de 300 mg (1 cápsula de 150 mg 2 vezes ao dia). O tratamento deve ser mantido por toda vida dependendo do risco individual do paciente.

Pacientes especiais: não é necessário ajustar a dose se você tiver mau funcionamento leve a moderado dos rins, se estiver usando amiodarona, quinidina, verapamil ou se for idoso.

Também não é necessário ajustar a dose se você tiver apenas 1 destes fatores de risco: ≥75 anos, mau funcionamento moderado dos rins ou sangramento gastrintestinal prévio; porém, se você tiver mais de 1 destes fatores, seu médico irá avaliar se o beneficio de usar PRADAXA será maior que os riscos de sangramento.

Se seu médico irá trocar PRADAXA por um anticoagulante injetável, deverá aguardar 12 horas após a última dose antes de mudar o tratamento.

Se seu médico for trocar PRADAXA por um anticoagulante injetável ou um antagonista de vitamina K, ou substituir um antagonista de vitamina K (anticoagulante oral, como varfarina) por PRADAXA, você deverá seguir estritamente suas orientações.

PREVENÇÃO DE DERRAME (AVC), MIGRAÇÃO DO COÁGULO E REDUÇÃO DO RISCO DE MORTE EM PACIENTES COM FIBRILAÇÃO ATRIAL

A dose recomendada é de 150 mg por via oral (1 cápsula) 2 vezes ao dia. O tratamento deve ser mantido por toda a vida.

Se você tiver 80 anos ou mais, a dose diária deve ser reduzida para 110 mg (1 cápsula) 2 vezes ao dia.

Pacientes especiais: não é necessário ajustar a dose se você tiver mau funcionamento dos rins ou se estiver usando amiodarona, quinidina ou verapamil. Porém, se você tiver um ou mais fatores que causam maior risco de sangramento, como 75 anos ou mais, mau funcionamento moderado dos rins, se está usando amiodarona, quinidina ou verapamil, antiplaquetários (como ácido acetilsalicílico, clopidogrel) ou tenha tido sangramento gastrointestinal, é possível que seu médico escolha a dose de 110 mg (1 cápsula) 2 vezes ao dia.

Se seu médico for trocar PRADAXA por um anticoagulante injetável ou um antagonista de vitamina K, ou substituir um antagonista de vitamina K (anticoagulante oral, como varfarina) por PRADAXA, você deverá seguir estritamente suas orientações.

Seu tratamento com PRADAXA pode ser mantido se você for submetido aos procedimentos de cardioversão (choque elétrico no coração) e de ablação (eliminação do foco de arritmia) por cateter em caso de fibrilação atrial. Caso você tenha sido submetido a um cateterismo cardíaco com colocação de stent, você poderá ser tratado com PRADAXA em combinação com antiplaquetário, após a estabilização clínica.

Considerar ainda as seguintes orientações para qualquer indicação de uso: Não é necessário ajustar a dose em relação ao peso corporal.

Se seu médico irá trocar um anticoagulante injetável por PRADAXA: PRADAXA deve ser administrado de zero a duas horas antes do horário em que a próxima dose do anticoagulante injetável seria administrada ou quando da interrupção do seu uso em caso de tratamento contínuo (por exemplo, heparina não fracionada injetável).

INSTRUÇÕES PARA USO/MANUSEIO

Ao remover a cápsula do blister, por favor, observe as seguintes instruções:

• Destaque um blister individual da cartela ao longo da linha perfurada.

• Retire a película de alumínio e remova a cápsula.

• A cápsula não deve ser empurrada através da folha de alumínio.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento.

Não interrompa o tratamento sem conhecimento do seu médico.', 'Você não deve tomar PRADAXA se tiver alergia à dabigatrana, etexilato de dabigatrana ou a algum dos excipientes do produto; se tiver mau funcionamento grave dos rins (insuficiência renal grave); se tiver sangramentos ou algum distúrbio que afete a coagulação sanguínea; se tiver lesão de órgãos com risco de sangramento clinicamente importante, inclusive derrame do tipo hemorrágico nos últimos 6 meses; se estiver em tratamento com cetoconazol sistêmico e se possuir próteses de valvas do coração.', 'Reação adversa/indicação de uso
Prevenção de AVC/embolia sistêmica
Prevenção de TEV-cirurgia ortopédica de grande porte
Tratamento de TVP aguda e/ou EP
Prevenção de TVP e/ou EP recorrentes

Anemia
Comum
Incomum
Incomum
Rara

- anemia pós-operatória
-
Rara
-
-

Trombocitopenia (redução do número de plaquetas)
Incomum
Rara
Rara
Rara

Hipersensibilidade (alergia)
Incomum
Incomum
Incomum
Incomum

- prurido (coceira)
Incomum
Rara
Rara
Incomum

- rash (erupções e vermelhidão na pele)
Incomum
Rara
Incomum
Incomum

- urticária (coceira e vermelhidão)
Rara
Rara
Rara
Rara

Angioedema (inchaço na língua, lábios e garganta)
Rara
Rara
Rara
Rara

Dor de cabeça
-
-
Incomum
Incomum

Dor abdominal (na barriga)
Comum
Rara
Incomum
Incomum

Diarreia
Comum
Incomum
Incomum
Incomum

Dispepsia (indigestão)
Comum
Rara
Comum
Comum

Disfagia (dificuldade para engolir)
Incomum
Rara
Rara
Rara

Úlcera gastrointestinal (estômago/intestino)
Incomum
Rara
Incomum
Rara

Gastroesofagite (inflamação do estômago/esôfago)
Incomum
Rara
Incomum
Incomum

Doença do refluxo gastroesofágico (subida de conteúdo do estômago para o esôfago)
Incomum
Rara
Incomum
Incomum

Náusea (enjoo)
Comum
Incomum
Incomum
Incomum

Vômito
Incomum
Incomum
Incomum
Incomum

Função hepática anormal (alteração de exames laboratoriais do fígado)
Incomum
Comum
Incomum
Incomum

Epistaxe (sangramento nasal)
Comum
Incomum
Comum
Comum

Hemoptise (escarro com sangue)
Incomum
Rara
Incomum
Incomum

Hemorragias (sangramento):
Incomum
Rara
Incomum
Incomum

- intracraniana (dentro da cabeça)
Incomum
Rara
Rara
Rara

- gastrointestinal (estômago/ intestino)
Comum
Incomum
Comum
Comum

- cutânea (na pele)
Comum
Incomum
Comum
Comum

- urogenital (vias urinárias ou genitais)
Comum
Incomum
Comum
Comum

- no local da injeção/cateter/ incisão
Rara
Rara
Rara
Rara

- traumática (após contusão)
Rara
Incomum
Incomum
Rara

- em ferimentos
-
Incomum
-
-

- após procedimento médico
-
Incomum
-
-

Hemartrose (sangue nas articulações/juntas)
Rara
Incomum
Incomum
Rara

Hematuria (sangue na urina)
Comum
Incomum
Comum
Comum

Secreção sanguinolenta
-
Rara
-
-

Hematoma (acúmulo de sangue)
Incomum
Incomum
Incomum
Incomum

- após procedimento médico
-
Incomum
-
-

Saída de secreção após procedimento médico e de ferida
-
Incomum
-
-

Drenagem após procedimento médico e de ferida
-
Rara
-
-

Reações com frequência desconhecida para todas as indicações de uso (não foi possível calcular a frequência com base nos dados disponíveis): broncoespasmo (falta de ar) e reação anafilática (reação alérgica grave).

Reação comum: ocorre entre 1% e 10% dos pacientes que utilizam este medicamento.

Reação incomum: ocorre entre 0,1% e 1% dos pacientes que utilizam este medicamento.

Reação rara: ocorre entre 0,01% e 0,1% dos pacientes que utilizam este medicamento.

Atenção: este produto é um medicamento que possui nova indicação terapêutica no país e, embora as pesquisas tenham indicado eficácia e segurança aceitáveis, mesmo que indicado e utilizado corretamente, podem ocorrer eventos adversos imprevisíveis ou desconhecidos. Nesse caso, informe seu médico.', 'O uso concomitante de alguns medicamentos que podem alterar a coagulação ou provocar sangramentos pode interferir na ação de PRADAXA.

Você deverá informar ao seu médico se usa algum dos seguintes medicamentos, que podem aumentar ou reduzir o efeito do seu tratamento: amiodarona, verapamil, quinidina, claritromicina, cetoconazol, rifampicina, erva-de- são-joão e carbamazepina.

O uso de PRADAXA com os seguintes medicamentos pode aumentar o risco de sangramento: heparina e

derivados (exceto nas doses necessárias para manter a permeabilidade de cateter venoso central ou arterial ou durante a ablação por cateter em caso de fibrilação atrial), fondaparinux, desirudina, agentes trombolíticos (usados para desobstruir vasos como no infarto), antagonistas do receptor GPIIb/IIIa, ticlopidina, dextrano, sulfimpirazona, rivaroxabana, prasugrel, ticagrelor, antagonistas da vitamina K (como varfarina), itraconazol, tacrolimo, ciclosporina, ritonavir, tipranavir, nelfinavir e saquinavir.

Especificamente para a prevenção de complicações da fibrilação atrial, o uso de PRADAXA com os seguintes medicamentos pode aumentar o risco de sangramento: ácido acetilsalicílico, clopidogrel, anti-inflamatórios não esteroides e antidepressivos que atuam sobre a serotonina e/ou norepinefrina (por exemplo, fluoxetina, paroxetina, sertralina, venlafaxina, citalopram, fluvoxamina, duloxetina).

O uso concomitante com dronedarona não é recomendado. O uso concomitante com cetoconazol sistêmico está contraindicado.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem conhecimento do seu médico. Pode ser perigoso para sua saúde.', 'Risco de sangramentos: PRADAXA é um medicamento anticoagulante e por isso deve ser usado com cuidado em condições de alto risco de sangramento, incluindo cirurgias, procedimentos, biópsias recentes, traumatismos e endocardite bacteriana. Seu médico poderá interromper temporariamente o uso de PRADAXA em alguns casos. Assim, informe sempre o seu médico ou cirurgião-dentista de que está usando PRADAXA antes de qualquer cirurgia ou procedimento. Isto se aplica também a procedimentos como anestesia raquidiana ou peridural. Seu tratamento com PRADAXA pode ser mantido se você for submetido aos procedimentos de cardioversão (choque elétrico no coração) e de ablação (eliminação do foco de arritmia) por cateter em caso de fibrilação atrial. Informe também sobre qualquer sangramento durante o tratamento com PRADAXA.

Se durante o tratamento você se machucar ou sofrer queda, e principalmente se bater a cabeça, procure atendimento médico imediatamente, pois nestes casos o risco de desenvolver sangramentos pode aumentar. Se você tiver sangramento descontrolado ou que ameace a vida e precisar que o sangue volte a ter a coagulação normal de forma rápida, está disponível o agente reversor específico de PRADAXA (PRAXBIND, idarucizumabe). Além disso, algumas cirurgias ou procedimentos podem precisar, a critério do médico, da reversão da anticoagulação de PRADAXA, pelo uso do agente reversor PRAXBIND.

Seu médico não precisa solicitar exames periódicos para monitorar sua coagulação com o uso de PRADAXA, porém há testes disponíveis caso necessário.

Se você tiver 75 anos ou mais e estiver usando PRADAXA junto com medicamentos que aumentem o risco de sangramento (por exemplo, ácido acetilsalicílico, clopidogrel e anti-inflamatórios), seu médico poderá solicitar testes para verificar sangue oculto nas fezes ou queda nos níveis de hemoglobina.

Função dos rins: idosos com 75 anos ou mais podem ter diminuição da função dos rins devido à idade e o médico poderá prescrever uma dose menor nesses casos. Seu médico deve avaliar sua função renal antes de iniciar o tratamento com PRADAXA, e também durante o tratamento, caso ocorram situações que possam afetar sua função renal.

Se você tiver diminuição moderada da função renal e seu médico receitou PRADAXA para uso por tempo indeterminado, ele deverá avaliar sua função renal pelo menos 1 vez ao ano ou sempre que necessário; se surgir mau funcionamento agudo dos rins, o tratamento deverá ser interrompido.

PRADAXA contém corante amarelo crepúsculo que pode causar reações alérgicas.

PRADAXA não foi investigado em pacientes abaixo dos 18 anos de idade, não sendo recomendado para tratamento em crianças. Não foram realizados estudos sobre o efeito na capacidade de dirigir e operar máquinas.

GRAVIDEZ E LACTAÇÃO

Se você tem possibilidade de engravidar, deve evitar a gravidez durante o tratamento com PRADAXA, ou se estiver grávida, só deve ser tratada com o produto se o seu médico considerar que o benefício esperado supera os riscos. Como precaução, a amamentação deve ser interrompida durante o tratamento com PRADAXA.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião- dentista.

INTERAÇÕES MEDICAMENTOSAS

O uso concomitante de alguns medicamentos que podem alterar a coagulação ou provocar sangramentos pode interferir na ação de PRADAXA.

Você deverá informar ao seu médico se usa algum dos seguintes medicamentos, que podem aumentar ou reduzir o efeito do seu tratamento: amiodarona, verapamil, quinidina, claritromicina, cetoconazol, rifampicina, erva-de- são-joão e carbamazepina.

O uso de PRADAXA com os seguintes medicamentos pode aumentar o risco de sangramento: heparina e

derivados (exceto nas doses necessárias para manter a permeabilidade de cateter venoso central ou arterial ou durante a ablação por cateter em caso de fibrilação atrial), fondaparinux, desirudina, agentes trombolíticos (usados para desobstruir vasos como no infarto), antagonistas do receptor GPIIb/IIIa, ticlopidina, dextrano, sulfimpirazona, rivaroxabana, prasugrel, ticagrelor, antagonistas da vitamina K (como varfarina), itraconazol, tacrolimo, ciclosporina, ritonavir, tipranavir, nelfinavir e saquinavir.

Especificamente para a prevenção de complicações da fibrilação atrial, o uso de PRADAXA com os seguintes medicamentos pode aumentar o risco de sangramento: ácido acetilsalicílico, clopidogrel, anti-inflamatórios não esteroides e antidepressivos que atuam sobre a serotonina e/ou norepinefrina (por exemplo, fluoxetina, paroxetina, sertralina, venlafaxina, citalopram, fluvoxamina, duloxetina).

O uso concomitante com dronedarona não é recomendado. O uso concomitante com cetoconazol sistêmico está contraindicado.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem conhecimento do seu médico. Pode ser perigoso para sua saúde.', 'https://www.bulas.med.br/bula/pradaxa', 'Vermelha', '2026-11-22T23:23:56.479Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/pradaxa');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Enoxaparina Sódica', 'Enoxaparina Sódica', 'O princípio ativo de Clexane é a enoxaparina sódica. Trata-se de uma heparina de baixo peso molecular com peso médio de 4.500 dáltons. A enoxaparina sódica é um sal de sódio. A distribuição do peso molecular é:', '• Tratamento da trombose venosa profunda com ou sem embolismo pulmonar;

• Tratamento da angina instável e infarto do miocárdio sem elevação do segmento ST, administrado concomitantemente ao ácido acetilsalicílico;

• Tratamento de infarto agudo do miocárdio com elevação do segmento ST, incluindo pacientes a serem tratados clinicamente ou com subsequente intervenção coronariana percutânea;

• Profilaxia do tromboembolismo venoso, em particular aqueles associados à cirurgia ortopédica ou à cirurgia geral;

• Profilaxia do tromboembolismo venoso em pacientes acamados devido a doenças agudas incluindo insuficiência cardíaca, falência respiratória, infecções severas e doenças reumáticas;

• Prevenção da formação de trombo na circulação extracorpórea durante a hemodiálise.', 'A via de administração de Clexane varia dependendo da indicação do produto. Abaixo estão descritas as técnicas de injeção subcutânea e bolus intravenoso.

Técnica de injeção subcutânea de seringas pré-enchidas com sistema de segurança:

A injeção subcutânea aplicada corretamente é essencial para reduzir a dor e ferimento no local da injeção.

Para evitar ferimentos acidentais com a agulha após a injeção, as seringas pré-enchidas são providas de um dispositivo de segurança automático.

PREPARO DO LOCAL PARA INJEÇÃO:

O local recomendado para injeção é na gordura da parte inferior do abdômen, pelo menos 5 centímetros de distância do umbigo para fora e em ambos os lados.

Antes da injeção, lavar as mãos. Limpar (não esfregar) com álcool o local selecionado para injeção. Selecionar um local diferente do abdômen inferior a cada aplicação.

PREPARO DA SERINGA ANTES DA INJEÇÃO:

Verifique se a seringa não está danificada e se o medicamento dentro está como uma solução límpida, sem partículas. Se a seringa estiver danificada ou o medicamento não for límpido, utilizar outra seringa.

Para as doses de 20 mg e 40 mg:

• Retire a capa protetora da agulha (figura 1).

Uma gota pode aparecer na ponta da agulha. Caso isto ocorra, remova-a antes de injetar o medicamento através de batidas suaves no corpo da seringa com a agulha apontada para baixo. Não expelir qualquer bolha de ar da seringa antes de administrar a injeção.

Para as doses de 60 mg, 80 mg e 100 mg:

• Retire a capa protetora da agulha (figura 1).

• Ajuste a dose a ser injetada (se necessário):

A quantidade do medicamento a ser injetada deve ser ajustada dependendo do peso corpóreo do paciente; consequentemente qualquer excesso do medicamento deve ser expelido antes da injeção. Segure a seringa apontando para baixo (para manter a bolha de ar na seringa) e expelindo o excesso do medicamento em um recipiente adequado.

Nota: Caso o excesso de medicamento não seja expelido antes da aplicação, o dispositivo de segurança não será ativado ao final da injeção.

Quando não houver a necessidade de ajuste da dose, a seringa pré-enchida está pronta para o uso. Não expelir qualquer bolha de ar da seringa antes de administrar a injeção.

Uma gota pode aparecer na ponta da agulha. Se isso ocorrer, remova a gota antes da administração através de batidas no corpo da seringa com a agulha apontada para baixo.

Administração da injeção:

• A seringa pré-enchida (20 mg/0,2 mL e 40 mg/0,4 mL) já está pronta para uso. Para evitar a perda da medicação, não pressione o êmbolo para expelir qualquer bolha de ar antes de administrar a injeção.

• A injeção deve ser administrada por injeção subcutânea profunda, no tecido subcutâneo da parede abdominal, com o paciente deitado ou sentado em posição confortável, alternando entre os lados esquerdo e direito a cada aplicação.

• A agulha deve ser introduzida perpendicularmente na espessura de uma prega cutânea feita entre os dedos polegar e indicador. A prega deve ser mantida durante todo o período da injeção (figura 2). Não esfregue o local da injeção após a administração

• O dispositivo de segurança é automaticamente ativado, quando o êmbolo é pressionado até o final, deste modo protegendo completamente a agulha usada e sem causar desconforto ao paciente. A ativação do dispositivo de segurança só é possível se o êmbolo for completamente abaixado.

Nota: o dispositivo de segurança somente poderá ser ativado com a seringa completamente vazia.

TÉCNICA DE INJEÇÃO INTRAVENOSA (BOLUS)

Apenas para a indicação de tratamento de infarto agudo do miocárdio com elevação do segmento ST

Clexane deve ser administrado através de uma linha intravenosa e não deve ser misturado ou coadministrado com outros medicamentos. Para evitar a possibilidade de mistura de Clexane com outros medicamentos, o acesso intravenoso escolhido deve ser lavado com quantidade suficiente de solução salina ou solução dextrose antes e imediatamente após a administração do bolus intravenoso de Clexane para limpar o dispositivo de acesso do medicamento. Clexane pode ser utilizado com segurança com solução salina normal 0,9% ou dextrose a 5% em água.

Bolus intravenoso inicial de 30 mg: utiliza-se uma seringa pré-enchida graduada de Clexane e despreza-se o excesso do volume, obtendo apenas 30 mg (0,3 mL) na seringa. Injeta-se, então, a dose de 30 mg diretamente na linha intravenosa.

Bolus adicional para pacientes submetidos à intervenção coronariana percutânea quando a última dose subcutânea de Clexane foi administrada há mais de 8 horas antes de o balão ser inflado: para pacientes submetidos à intervenção coronariana percutânea, um bolus intravenoso adicional de 0,3 mg/kg deve ser administrado se a última dose subcutânea de Clexane foi administrada há mais de 8 horas antes de o balão ser inflado (vide item “8. Posologia e modo de usar – Tratamento do infarto agudo do miocárdio com elevação do segmento ST”).

Para assegurar a precisão do pequeno volume a ser injetado, recomenda-se a diluição do medicamento para uma solução de 3 mg/mL.

Para obter uma solução a 3 mg/mL utilizando uma seringa pré-enchida de 60 mg de Clexane, recomenda-se usar uma bolsa de infusão de 50 mL (contendo, por exemplo, solução salina normal 0,9% ou dextrose a 5% em água). Com o auxílio de uma seringa, retira-se 30 mL da solução contida na bolsa e despreza- se este volume. Aos 20 mL restantes na bolsa de infusão, injeta-se o conteúdo total de uma seringa pré-enchida graduada de 60 mg. Mistura-se gentilmente a solução final. Retira-se com uma seringa o volume requerido da solução para administração na linha intravenosa. Recomenda-se que esta solução seja preparada imediatamente antes de sua utilização.

Após finalizada a diluição, o volume a ser injetado na linha intravenosa deve ser calculado utilizando-se a seguinte fórmula: [volume da solução diluída (mL) = peso do paciente (kg) x 0,1] ou utilizando a tabela abaixo.

Volume de solução a 3 mg/mL a ser injetado na linha intravenosa

Peso do paciente

(kg)

Dose requerida = 0,3 mg/kg

(mg)

Volume a ser injetado (mL) após ser diluído para a concentração final de 3 mg/mL

45

13,5

4,5

50

15

5

55

16,5

5,5

60

18

6

65

19,5

6,5

70

21

7

75

22,5

7,5

80

24

8

85

25,5

8,5

90

27

9

95

28,5

9,5

100

30

10

POSOLOGIA

A posologia de Clexane é determinada pela predisposição individual de ocorrer o tromboembolismo venoso em situações desencadeantes como cirurgia, imobilização prolongada e trauma, entre outras. Dessa maneira, são considerados com risco moderado os indivíduos que apresentem os seguintes fatores predisponentes: idade superior a 40 anos, obesidade, varizes dos membros inferiores, neoplasia, doença pulmonar ou cardíaca crônica, estrogenioterapia, puerpério, infecções sistêmicas, entre outros. São considerados com alto risco os indivíduos com histórico de tromboembolismo venoso prévio, neoplasia abdominal ou pélvica, cirurgia ortopédica de grande porte dos membros inferiores, entre outros.

1. PROFILAXIA DO TROMBOEMBOLISMO VENOSO EM PACIENTES CIRÚRGICOS

Em pacientes que apresentam risco moderado de tromboembolismo (por exemplo: cirurgia abdominal), a dose recomendada de Clexane é de 20 mg ou 40 mg uma vez ao dia por via subcutânea. Na cirurgia geral, a primeira injeção deve ser administrada 2 horas antes da intervenção cirúrgica.

Em pacientes com alto risco de tromboembolismo (por exemplo: cirurgia ortopédica), a dose recomendada de Clexane administrada por via subcutânea é de 40 mg uma vez ao dia, iniciada 12 horas antes da cirurgia, ou de 30 mg, duas vezes ao dia, iniciada 12 a 24 horas após a cirurgia.

O tratamento com Clexane é geralmente prescrito por um período médio de 7 a 10 dias. Um tratamento mais prolongado pode ser apropriado em alguns pacientes e deve ser continuado enquanto houver risco de tromboembolismo venoso e até que o paciente seja ambulatorial.

A administração única diária de 40 mg de Clexane por mais 3 semanas, seguindo a profilaxia inicial (em geral, após a alta hospitalar), comprovou ser benéfica em pacientes submetidos à cirurgia ortopédica.

Para recomendações especiais sobre o intervalo entre as dosagens para anestesia espinhal/peridural e procedimentos de revascularização coronária percutânea: vide item “5.Advertências e Precauções”.

2. PROFILAXIA DO TROMBOEMBOLISMO VENOSO EM PACIENTES CLÍNICOS

A dose recomendada para pacientes clínicos é de 40 mg de Clexane, uma vez ao dia, administrada por via subcutânea. A duração do tratamento deve ser de no mínimo, 6 dias, devendo ser continuado até que o paciente recupere a capacidade plena de se locomover, por um período máximo de 14 dias.

3. TRATAMENTO DA TROMBOSE VENOSA PROFUNDA COM OU SEM EMBOLISMO PULMONAR

A posologia de Clexane recomendada para o tratamento da trombose venosa profunda é de 1,5 mg/kg, uma vez ao dia ou 1 mg/kg, duas vezes ao dia, administrado por via subcutânea. Em pacientes com distúrbios tromboembólicos complicados, recomenda-se a administração da dose de 1 mg/kg, duas vezes ao dia.

O tratamento com Clexane é geralmente prescrito por um período médio de 10 dias. A terapia anticoagulante oral deve ser iniciada quando apropriada e o tratamento com Clexane deve ser mantido até que o efeito terapêutico do anticoagulante tenha sido atingido (International Normalisation Ratio 2 a 3).

4. PREVENÇÃO DA FORMAÇÃO DE TROMBO NO CIRCUITO DE CIRCULAÇÃO EXTRACORPÓREA DURANTE A HEMODIÁLISE

A dose recomendada é de 1 mg/kg de Clexane.

Em pacientes com alto risco hemorrágico, a dose deve ser reduzida para 0,5 mg/kg quando o acesso vascular for duplo ou 0,75 mg/kg quando o acesso vascular for simples.

Durante a hemodiálise, Clexane deve ser introduzido na linha arterial do circuito, no início da sessão de hemodiálise. O efeito desta dose geralmente é suficiente para uma sessão com duração de 4 horas; entretanto, caso haja o aparecimento de anéis de fibrina, por exemplo, após uma sessão mais longa que o normal, pode ser administrada dose complementar de 0,5 a 1,0 mg/kg de Clexane.

5. TRATAMENTO DE ANGINA INSTÁVEL E INFARTO DO MIOCÁRDIO SEM ELEVAÇÃO DO SEGMENTO ST

A dose de Clexane recomendada é de 1 mg/kg a cada 12 horas, por via subcutânea, administrada concomitantemente com ácido acetilsalicílico oral (100 a 325 mg, uma vez ao dia).

Nestes pacientes, o tratamento com Clexane deve ser prescrito por no mínimo 2 dias e mantido até estabilização clínica. A duração normal do tratamento é de 2 a 8 dias.

6. TRATAMENTO DO INFARTO AGUDO DO MIOCÁRDIO COM ELEVAÇÃO DO SEGMENTO ST

A dose recomendada de Clexane é de um bolus intravenoso único de 30 mg acompanhado de uma dose de 1 mg/kg por via subcutânea, seguido por 1 mg/kg por via subcutânea a cada 12 horas (as duas primeiras doses subcutâneas devem ser de no máximo 100 mg cada dose e as demais doses 1 mg/kg por via subcutânea). Para pacientes com 75 anos de idade ou mais, verifique instruções específicas descritas abaixo em “Populações Especiais – Idosos”.

Quando administrado em conjunto com um trombolítico (específico para fibrina ou não), Clexane deve ser administrado entre 15 minutos antes e 30 minutos depois do início da terapia fibrinolítica. Todos os pacientes devem receber ácido acetilsalicílico tão logo seja diagnosticado o infarto agudo do miocárdio com elevação do segmento ST. Esta medicação deve ser mantida com dosagem de 75 a 325 mg uma vez ao dia, a menos que haja contraindicação para o seu uso.

A duração recomendada do tratamento com Clexane é de 8 dias ou até que o paciente receba alta do hospital, considerando-se o que ocorrer primeiro.

Para pacientes submetidos à intervenção coronariana percutânea: se a última dose subcutânea de Clexane foi administrada há menos de 8 horas antes de o balão ser inflado, não é necessária dose adicional deste medicamento. Entretanto, caso a última dose subcutânea tenha sido administrada há mais de 8 horas antes de o balão ser inflado, uma dose adicional de 0,3 mg/kg de Clexane deve ser administrada através de bolus intravenoso.

RISCO DE USO POR VIA DE ADMINISTRAÇÃO NÃO RECOMENDADA

Não há estudos dos efeitos de Clexane administrado por vias não recomendadas. Portanto, por segurança e para garantir a eficácia deste medicamento, a administração deve ser somente por via intravenosa ou subcutânea (dependendo da indicação terapêutica).

POPULAÇÕES ESPECIAIS

Idosos: para o tratamento do infarto agudo do miocárdio com elevação do segmento ST em pacientes idosos (com idade igual ou maior a 75 anos), não deve ser administrado o bolus intravenoso inicial. A dose inicial é de 0,75 mg/kg por via subcutânea a cada 12 horas (as duas primeiras doses subcutâneas devem ser de no máximo 75 mg cada dose e as demais 0,75 mg/kg por via subcutânea).

Para as demais indicações do produto, não é necessário realizar ajuste posológico em idosos, a menos que a função renal esteja prejudicada (vide itens “3. Características Farmacológicas – Propriedades farmacocinéticas”, “5. Advertências e Precauções” e “8. Posologia e modo de usar – Insuficiência renal”).

Insuficiência renal: (vide itens “3. Características Farmacológicas – Propriedades farmacocinéticas” e “5. Advertências e Precauções”)

Insuficiência renal severa: é necessário realizar ajuste posológico em pacientes com insuficiência renal severa (clearance de creatinina < 30 mL/min), de acordo com as tabelas a seguir, uma vez que a exposição ao Clexane é significativamente aumentada nesta população de pacientes.

Para uso terapêutico, os seguintes ajustes posológicos são recomendados:

Dose padrão

Insuficiência renal severa

1 mg/kg por via subcutânea, duas vezes ao dia

1 mg/kg por via subcutânea, uma vez ao dia

1,5 mg/kg por via subcutânea, uma vez ao dia

1 mg/kg por via subcutânea, uma vez ao dia

Tratamento do infarto agudo do miocárdio com elevação do segmento ST em pacientes com idade inferior a 75 anos

30 mg em bolus intravenoso único acompanhado de uma dose de 1 mg/kg por via subcutânea, seguido de 1 mg/kg por via subcutânea duas vezes ao dia (as duas primeiras doses subcutâneas devem ser de no máximo 100 mg cada)

30 mg em bolus intravenoso único acompanhado de uma dose de 1 mg/kg por via subcutânea, seguido de 1 mg/kg por via subcutânea uma vez ao dia (a primeira dose subcutânea deve ser de no máximo 100 mg)

Tratamento do infarto agudo do miocárdio com elevação do segmento ST em pacientes idosos com idade maior ou igual a 75 anos

0,75 mg/kg por via subcutânea duas vezes ao dia SEM bolus intravenoso inicial (as duas primeiras doses subcutâneas devem ser de no máximo 75 mg cada)

1 mg/kg por via subcutânea uma vez ao dia SEM bolus intravenoso inicial (a primeira dose subcutânea deve ser de no máximo 100 mg)

Para uso profilático, os seguintes ajustes posológicos são recomendados:

Dose padrão

Insuficiência renal severa

40 mg por via subcutânea, uma vez ao dia

20 mg por via subcutânea, uma vez ao dia

20 mg por via subcutânea, uma vez ao dia

20 mg por via subcutânea, uma vez ao dia

Estes ajustes posológicos não se aplicam à indicação em hemodiálise.

Insuficiência renal leve e moderada: embora não seja recomendado realizar ajuste posológico em pacientes com insuficiência renal moderada (clearance de creatinina 30–50 mL/min) e leve (clearance de creatinina 50–80 mL/min), é aconselhável que se faça um monitoramento clínico cuidadoso.

Insuficiência hepática: em decorrência da ausência de estudos clínicos, recomenda-se cautela em pacientes com insuficiência hepática.

Anestesia espinhal/peridural: para pacientes que recebendo anestesia espinhal/peridural vide item “5. Advertências e Precauções – Anestesia espinhal/peridural”.', '• Hipersensibilidade à enoxaparina sódica, à heparina e seus derivados, inclusive outras heparinas de baixo peso molecular;

• História de trombocitopenia induzida por heparina mediada por imunidade (HIT) nos últimos 100 dias ou na presença de anticorpos circulantes;

• Hemorragias ativas de grande porte e condições com alto risco de desenvolvimento de hemorragia incontrolável, incluindo acidente vascular cerebral hemorrágico recente.', 'A enoxaparina foi avaliada em mais de 15000 pacientes que receberam enoxaparina em estudos clínicos. Estes estudos incluíram 1776 pacientes para profilaxia de trombose venosa profunda (TVP) seguida de cirurgia ortopédica ou abdominal em pacientes com risco de complicações tromboembólicas, 1169 para profilaxia de TVP em pacientes intensamente doentes com mobilidade severamente restrita, 559 para tratamento de TVP com ou sem embolismo pulmonar, 1578 para tratamento de angina instável e infarto do miocárdio sem elevação do segmento ST e 10176 para tratamento de infarto agudo do miocárdio com elevação do segmento ST.

O regime de enoxaparina sódica administrada durante estes estudos clínicos varia dependendo da indicação. A dose de enoxaparina sódica foi de 40 mg por via subcutânea uma vez ao dia para profilaxia de TVP seguida de cirurgia ou em pacientes intensamente doentes com mobilidade severamente restrita. No tratamento da TVP com ou sem embolismo pulmonar, pacientes recebendo enoxaparina foram tratados também com uma dose de 1 mg/kg por via subcutânea a cada 12 horas ou uma dose de 1,5 mg/kg por via subcutânea uma vez ao dia. Nos estudos clínicos para o tratamento de angina instável e infarto do miocárdio sem elevação do segmento ST, as doses foram de 1 mg/kg por via subcutânea a cada 12 horas e no estudo clinico para tratamento de infarto agudo do miocárdio com elevação do segmento ST, o regime de enoxaparina sódica foi de 30 mg por via intravenosa em bolus seguida de 1 mg/kg por via subcutânea a cada 12 horas.

As reações adversas observadas nestes estudos clínicos e reportadas na experiência pós-comercialização estão detalhadas abaixo:

Categoria
Frequência

Muito comum
≥ 10%

Comum
≥ 1% e < 10%

Incomum
≥ 0,1% e < 1%

Raro
≥ 0,01% e < 0,1%

Muito raro
< 0,01%

Desconhecida
Não pode ser estimada pelos dados disponíveis

HEMORRAGIAS

Em estudos clínicos, hemorragias foram as reações mais comumente relatadas. Estas incluem hemorragias de grande porte, reportadas no máximo em 4,2% dos pacientes (pacientes cirúrgicos**). Alguns destes casos foram fatais.

Assim como com outros anticoagulantes, pode ocorrer hemorragia na presença de fatores de risco associados, tais como: lesões orgânicas suscetíveis a sangramento, procedimentos invasivos ou uso concomitante de medicamentos que afetam a hemostasia (vide itens “5. Advertências e Precauções – Populações especiais” e “6. Interações Medicamentosas”).

DISTÚRBIOS VASCULARES

Profilaxia em pacientes cirúrgicos:

• Muito comum: hemorragia*

• Rara: hemorragia retroperitoneal

Profilaxia em pacientes sob tratamento médico:

• Comum: hemorragia*

Tratamento em pacientes com trombose venosa profunda com ou sem embolismo pulmonar:

• Muito comum: hemorragia*

• Incomum: hemorragia intracraniana, hemorragia retroperitoneal

Tratamento em pacientes com angina instável e infarto do miocárdio sem elevação do segmento ST:

• Comum: hemorragia*

• Rara: hemorragia retroperitoneal

Tratamento em pacientes com infarto agudo do miocárdio com elevação do segmento ST:

• Comum: hemorragia*

• Incomum: hemorragia intracraniana, hemorragia retroperitoneal

* como hematoma, outras equimoses além do local da injeção, ferimento com hematoma, hematúria, epistaxe e hemorragia gastrintestinal.

** em pacientes cirúrgicos as complicações hemorrágicas foram consideradas de grande porte: (1) se a hemorragia causou um evento clínico significativo, ou (2) se acompanhado por uma diminuição da hemoglobina ≥ 2 g/dL ou transfusão de 2 ou mais unidades de produto sanguíneo. As hemorragias retroperitoneal e

intracraniana foram sempre consideradas de grande porte.

TROMBOCITOPENIA E TROMBOCITOSE

Distúrbios do sangue e sistema linfático

Profilaxia em pacientes cirúrgicos

• Muito comum: trombocitose (aumento de plaquetas > 400.000/ mm3)

• Comum: trombocitopenia

Profilaxia em pacientes sob tratamento médico

• Incomum: trombocitopenia

Tratamento em pacientes com trombose venosa profunda com ou sem embolismo pulmonar

• Muito comum: trombocitose

• Comum: trombocitopenia

Tratamento em pacientes com angina instável e infarto do miocárdio sem elevação do segmento ST

• Incomum: trombocitopenia

Tratamento em pacientes com infarto agudo do miocárdio com elevação do segmento ST

• Comum: trombocitose, trombocitopenia

• Muito rara: trombocitopenia imunoalérgica

OUTRAS REAÇÕES ADVERSAS CLINICAMENTE RELEVANTES

Estas reações estão apresentadas abaixo, qualquer que sejam as indicações, por sistema órgão classe, frequência e ordem decrescente de gravidade.

Distúrbios do sistema imune:

• Comum: reação alérgica

• Rara: reação anafilática/anafilactoide (ver também experiência pós-comercialização)

Distúrbios hepatobiliares:

• Muito comum: aumento das enzimas hepáticas, principalmente transaminases (níveis de transaminases > 3 vezes o limite superior de normalidade)

Distúrbios da pele e tecido subcutâneo:

• Comum: urticária, prurido, eritema

• Incomum: dermatite bolhosa

Distúrbios gerais e condições no local da administração:

• Comum: hematoma, dor e outras reações no local da injeção (como edema, hemorragia, hipersensibilidade, inflamação, tumoração, dor ou reação não especificada)

• Incomum: irritação no local, necrose na pele do local de injeção

Investigação:

• Rara: hiperpotassemia

EXPERIÊNCIA PÓS-COMERCIALIZAÇÃO

As reações adversas a seguir foram identificadas durante o período após a aprovação do uso de Clexane. As reações adversas são derivadas de relatos espontâneos e, portanto, a frequência é desconhecida (não pode ser estimada a partir dos dados).

Distúrbios do sistema imunológico

• Reação anafilática/anafilactoide incluindo choque

Distúrbios do sistema nervoso

• Dor de cabeça

Distúrbios vasculares

• Foram relatados casos de hematoma espinhal (ou hematoma neuroaxial) com o uso concomitante de enoxaparina sódica e anestesia espinhal/peridural ou punção espinhal. Estas reações resultaram em graus variados de lesão neurológica, incluindo paralisia por tempo prolongado ou permanente (vide item “5.Advertências e Precauções”).

Distúrbios do sangue e linfáticos:

• Anemia hemorrágica

• Casos de trombocitopenia imunoalérgica com trombose:em alguns casos, a trombose foi complicada por infarto orgânico ou isquemia de extremidade (vide item “5. Advertências e Precauções”).

• Eosinofilia

Distúrbios da pele e tecidos subcutâneos:

• Vasculite cutânea, necrose cutânea geralmente ocorrendo no local da administração (estes fenômenos são geralmente precedidos por púrpura ou placas eritematosas, infiltradas e dolorosas), devendo-se interromper o tratamento com Clexane. - Nódulos no local de injeção (nódulos inflamatórios que não são inclusões císticas de enoxaparina) que desaparecem após alguns dias e não devem ser motivo para interrupção do tratamento.

• Alopecia

Distúrbios hepatobiliares:

• Lesão hepatocelular

• Lesão colestática

Distúrbios musculoesqueléticos e de tecido conjuntivo

• Osteoporose em terapia prolongada (acima de 3 meses)

Atenção: este produto é um medicamento que possui nova indicação terapêutica no país e, embora as pesquisas tenham indicado eficácia e segurança aceitáveis, mesmo que indicado e utilizado corretamente, podem ocorrer eventos adversos imprevisíveis ou desconhecidos. Nesse caso, notifique ao Sistema de Notificações em Vigilância Sanitária – NOTIVISA ou à Vigilância Sanitária Estadual ou Municipal.', 'MEDICAMENTO-MEDICAMENTO

Recomenda-se a interrupção do uso de medicamentos que afetam a hemostasia antes do início do tratamento com Clexane, a menos que seu uso seja estritamente indicado. Tais medicamentos incluem:

• salicilatos sistêmicos, ácido acetilsalicílico e outros AINEs, incluindo o cetorolaco;

• dextrana 40, ticlopidina e clopidogrel;

• glicocorticoides sistêmicos;

• agentes trombolíticos e anticoagulantes;

• outros agentes antiplaquetários, incluindo os antagonistas de glicoproteína IIb/IIIa.

Em caso de indicação do uso de qualquer uma destas associações, deve-se utilizar Clexane sob cuidadoso monitoramento clínico e laboratorial quando apropriado.

MEDICAMENTO-EXAME LABORATORIAL

Nas doses utilizadas na profilaxia do tromboembolismo venoso, Clexane não influencia significativamente o tempo de sangramento e os testes de coagulação sanguínea global, nem afeta a agregação plaquetária ou a ligação do fibrinogênio às plaquetas.

Pode ocorrer aumento do tempo de tromboplastina parcial ativada (TTPa) e do tempo de coagulação ativada (TCA) com a administração de doses mais altas. Aumentos no TTPa e TCA não estão linearmente correlacionados ao aumento da atividade antitrombótica de Clexane, sendo, portanto, inadequados e inseguros para monitoramento da atividade de Clexane.', 'Não administrar Clexane por via intramuscular.

HEMORRAGIA

Assim como com outros anticoagulantes, pode ocorrer sangramento em qualquer local (vide item “9. Reações Adversas”). Se ocorrer sangramento, a origem da hemorragia deve ser investigada e tratamento apropriado deve ser instituído.

Clexane, assim como qualquer outra terapia anticoagulante, deve ser utilizado com cautela em condições com alto risco de hemorragia, tais como:

• alterações na hemostasia;

• histórico de úlcera péptica;

• acidente vascular cerebral isquêmico recente;

• hipertensão arterial severa não controlada;

• retinopatia diabética;

• neurocirurgia ou cirurgia oftálmica recente;

• uso concomitante de medicamentos que afetem a hemostasia (vide item “6. Interações Medicamentosas”).

MONITORAMENTO DA CONTAGEM PLAQUETÁRIA

O risco de trombocitopenia induzida por heparina (reação mediada por anticorpos) também existe com heparinas de baixo peso molecular. Pode ocorrer trombocitopenia, geralmente entre o 5º e 21º dia após o início do tratamento com Clexane. Portanto, recomenda-se a realização de contagem plaquetária antes do início e regularmente durante o tratamento. Na prática, em caso de confirmação de diminuição significativa da contagem plaquetária (30 a 50% do valor inicial), o tratamento com Clexane deve ser imediatamente interrompido e substituído por outra terapia.

ADVERTÊNCIAS GERAIS

As heparinas de baixo peso molecular (HBPM) não devem ser intercambiáveis, pois existem diferenças entre elas quanto ao processo de fabricação, peso molecular, atividade anti-Xa específica, unidade e dosagem. Isto ocasiona diferenças em suas atividades farmacocinéticas e biológicas associadas (por exemplo, a atividade antitrombina e a interação plaquetária). Portanto, é necessário obedecer às instruções de uso de cada medicamento.

ANESTESIA ESPINHAL/PERIDURAL

Foram relatados casos de hematoma intraespinhal com o uso concomitante de Clexane e anestesia espinhal/peridural, resultando em paralisia prolongada ou permanente. Estes eventos são raros com a administração de doses iguais ou inferiores a 40 mg/dia de Clexane. O risco destes eventos pode ser aumentado com administração de doses maiores de Clexane, uso de cateter epidural no pós-operatório ou em caso de administração concomitante de medicamentos que alteram a hemostasia, tais como anti-inflamatórios não esteroidais (vide item “6. Interações Medicamentosas”). O risco parece também ser aumentado por traumatismo ou punções espinhais repetidas ou em pacientes com histórico de cirurgia ou deformidade espinhal.

Para reduzir o risco potencial de sangramento associado ao uso concomitante de Clexane e anestesia/analgesia peridural ou espinhal, deve-se considerar o perfil farmacocinético do fármaco (vide item “3. Características Farmacológicas – Propriedades Farmacocinéticas”). A introdução e remoção do cateter devem ser realizadas quando o efeito anticoagulante de Clexane estiver baixo, no entanto, o momento exato para chegar a um efeito anticoagulante suficientemente baixo em cada paciente não é conhecido.

A introdução ou remoção do cateter deve ser postergada por pelo menos 12 horas após a administração de doses baixas de Clexane (20 mg uma vez ao dia, 30 mg uma ou duas vezes ao dia, ou 40 mg uma vez ao dia) e, pelo menos, 24 horas após a administração de doses mais elevadas de Clexane (0,75 mg/kg, duas vezes ao dia, 1 mg/kg duas vezes ao dia, ou 1,5 mg/kg uma vez ao dia). Níveis de anti-Xa ainda são detectáveis neste momento, e estes atrasos não são uma garantia de que um hematoma neuroaxial (espinhal) será evitado. Pacientes recebendo a dose de 0,75 mg/kg duas vezes ao dia, ou a dose de 1 mg/kg duas vezes ao dia não devem receber a segunda dose de enoxaparina no regime de duas vezes ao dia para permitir um atraso maior antes da colocação ou remoção do cateter. Da mesma forma, apesar de uma recomendação específica para o intervalo da dose subsequente de enoxaparina após a remoção do cateter não poder ser feita, considerar adiar esta dose seguinte por pelo menos quatro horas, com base numa avaliação do risco-benefício considerando tanto o risco de trombose como o risco de sangramento no contexto do procedimento e fatores de risco do paciente. Para pacientes com clearance de creatinina < 30 mL/minuto, são necessárias considerações adicionais porque a eliminação de enoxaparina é mais prolongada; considerar a duplicação do tempo de remoção de um cateter, pelo menos, 24 horas para a menor dose prescrita de enoxaparina (30 mg uma vez ao dia) e, pelo menos, 48 horas para a dose mais elevada (1 mg/kg/dia).

Caso o médico decida administrar anticoagulantes durante o uso de anestesia peridural/espinhal ou punção lombar, deve-se empregar o monitoramento frequente para detectar qualquer sinal ou sintoma de lesão neurológica, tais como, dor na linha média da região dorsal, deficiências sensoriais e motoras (entorpecimento ou fraqueza dos membros inferiores), alterações intestinais e/ou urinárias. Os pacientes devem ser instruídos a informar imediatamente a seu médico caso apresentem qualquer sintoma ou sinal descrito acima. Em caso de suspeita de sinais ou sintomas de hematoma intraespinhal, devem ser efetuados o diagnóstico e tratamento, incluindo descompressão da medula espinhal, com urgência.

TROMBOCITOPENIA INDUZIDA PELA HEPARINA

A utilização de enoxaparina sódica em pacientes com história de HIT mediada por imunidade nos últimos 100 dias ou na presença de anticorpos circulantes está contraindicada. Os anticorpos circulantes podem persistir vários anos.

A enoxaparina sódica deve ser usada com extrema cautela em pacientes com história (mais de 100 dias) de trombocitopenia induzida por heparina sem anticorpos circulantes. A decisão de utilizar enoxaparina sódica neste caso, deve ser feita apenas após uma cuidadosa avaliação do risco benefício e após terem sido considerados tratamentos alternativos sem heparina.

PROCEDIMENTOS DE REVASCULARIZAÇÃO CORONARIANA PERCUTÂNEA

Para minimizar o risco de sangramento após a instrumentação vascular durante o tratamento da angina instável, infarto do miocárdio sem elevação do segmento ST e infarto agudo do miocárdio com elevação do segmento ST, devem-se respeitar precisamente os intervalos entre as doses recomendadas de Clexane. É importante estabelecer a hemostasia no local da punção após a intervenção coronariana percutânea. Caso tenha sido utilizado um dispositivo de fechamento, a bainha de acesso vascular pode ser removida imediatamente. Caso tenha sido utilizado um método de compressão manual, a bainha deve ser removida 6 horas após a última administração intravenosa ou subcutânea de Clexane. Se o tratamento com Clexane continuar, a próxima dose programada não deve ser administrada antes de 6 a 8 horas após a remoção da bainha. Deve-se ter atenção especial ao local do procedimento para detecção de sinais de sangramento ou formação de hematoma.

GRAVIDEZ E LACTAÇÃO

Estudos em animais não demonstraram qualquer evidência de fetotoxicidade ou teratogenicidade. Em ratas prenhes, a passagem de S-enoxaparina sódica através da placenta para o feto é mínima.

Em humanos, não existe evidência da passagem da enoxaparina sódica através da placenta durante o segundo trimestre da gravidez. Ainda não existem informações disponíveis a este respeito durante o primeiro e terceiro trimestres da gravidez.

Como não foram realizados estudos adequados e bem controlados em gestantes, e uma vez que os estudos realizados em animais nem sempre são bons indicativos da resposta humana, deve-se utilizar Clexane durante a gravidez somente se o médico considerar como estritamente necessário.

Categoria de risco na gravidez: B. Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica.

Em ratas lactantes, a concentração de S-enoxaparina sódica ou de seus metabólitos marcados no leite é muito

baixa. Não se sabe se a enoxaparina sódica inalterada é excretada no leite humano. A absorção oral da enoxaparina sódica é improvável, porém, como precaução, não se deve amamentar durante o tratamento com Clexane.

GESTANTES COM PRÓTESES MECÂNICAS VALVULARES CARDÍACAS

Não foram realizados estudos adequados para avaliar a utilização de Clexane na tromboprofilaxia em gestantes com próteses mecânicas valvulares cardíacas. Em um estudo clínico em gestantes com próteses mecânicas valvulares cardíacas, administrou-se enoxaparina (1 mg/kg duas vezes ao dia) para redução do risco de tromboembolismo, 2 de 8 gestantes desenvolveram coágulos resultando em bloqueio da válvula, levando a óbitos materno e fetal. Houve relatos isolados pós-comercialização de trombose da valva em gestantes com próteses mecânicas valvulares cardíacas enquanto eram medicadas com enoxaparina para tromboprofilaxia. Gestantes com próteses mecânicas valvulares cardíacas podem apresentar maior risco de tromboembolismo (vide item “5. Advertências e Precauções – Próteses mecânicas valvulares cardíacas”).

POPULAÇÕES ESPECIAIS

Pacientes idosos: não foi observado aumento na tendência de hemorragia em idosos com doses profiláticas. Porém, pacientes idosos (especialmente pacientes com idade igual ou maior a 80 anos) podem ter um aumento no risco de complicações hemorrágicas com doses terapêuticas. Portanto, aconselha-se um monitoramento clínico cuidadoso (vide itens “3. Características Farmacológicas – Propriedades Farmacocinéticas” e 8. Posologia e modo de usar”).

Pacientes idosos podem apresentar retardo na eliminação da enoxaparina sódica. (vide item “8. Posologia e modo de usar”).

Crianças: a segurança e eficácia de Clexane em crianças ainda não foram estabelecidas.

Próteses mecânicas valvulares cardíacas: o uso de Clexane não foi adequadamente estudado para casos de tromboprofilaxia em pacientes com próteses mecânicas valvulares cardíacas. Foram relatados casos isolados de trombose com próteses valvulares cardíacas em pacientes com próteses mecânicas valvulares cardíacas que receberam enoxaparina para tromboprofilaxia. A avaliação destes casos é limitada devido aos fatores causais serem confusos, incluindo doenças anteriores e dados clínicos insuficientes. Alguns destes casos ocorreram em gestantes nas quais a trombose resultou em óbitos materno e fetal. Gestantes com próteses mecânicas valvulares cardíacas podem apresentar maior risco para tromboembolismo (vide item “5. Advertências e Precauções – Gestantes com próteses mecânicas valvulares cardíacas”).

Insuficiência renal: em pacientes com insuficiência renal, existe aumento da exposição ao Clexane, aumentando também o risco de hemorragia. Como a exposição ao Clexane aumenta significativamente em pacientes com insuficiência renal severa (clearance de creatinina < 30 mL/min), o ajuste posológico é recomendado para dosagens terapêuticas e profiláticas. Embora não seja recomendado ajuste posológico em pacientes com insuficiência renal moderada (clearance de creatinina 30–50 mL/min) e leve (clearance de creatinina 50–80 mL/min), é aconselhável realizar um monitoramento clínico cuidadoso (vide itens “3. Características Farmacológicas- Propriedades Farmacocinéticas” e “8. Posologia e modo de usar”).

Peso baixo: um aumento na exposição ao Clexane em doses profiláticas (não ajustadas ao peso) tem sido observado em mulheres e homens com baixo peso (< 45 kg e < 57 kg, respectivamente), que pode resultar em maior risco de hemorragia. Portanto, é aconselhável realizar um monitoramento clínico cuidadoso nestes pacientes (vide item “3. Características Farmacológicas- Propriedades Farmacocinéticas”).

Pacientes obesos: pacientes obesos apresentam risco aumentado de tromboembolismo. A segurança e a eficácia de doses profiláticas em pacientes obesos (IMC > 30 kg/m) não foram totalmente determinadas e não há consenso para ajuste de dose. Estes pacientes devem ser observados cuidadosamente quanto aos sinais e sintomas de tromboembolismo.

ALTERAÇÕES NA CAPACIDADE DE DIRIGIR VEÍCULOS E OPERAR MÁQUINAS

A utilização de Clexane não afeta a habilidade de dirigir ou operar máquinas.', 'https://www.bulas.med.br/bula/clexane-profissional', 'Vermelha', '2026-11-22T23:23:56.482Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/clexane-profissional');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Fosfato de Sitagliptina', 'Fosfato de Sitagliptina', 'JANUVIA® pertence a uma classe de medicamentos denominados inibidores da DPP-4 (inibidores da dipeptidil peptidase-4) que reduz os níveis de açúcar no sangue em pacientes com diabetes mellitus tipo 2. O diabetes tipo 2 também é denominado diabetes não-insulino-dependente.', 'Seu médico prescreveu JANUVIA® para ajudar a reduzir seus níveis de açúcar no sangue, que estão aumentados por causa do diabetes tipo 2. JANUVIA® pode ser utilizado isoladamente ou em combinação com alguns outros medicamentos que diminuem os níveis de açúcar no sangue e deverá ser associado à dieta e a um programa de exercícios que seu médico lhe recomendará.

O que é diabetes tipo 2?

Diabetes tipo 2 é um distúrbio no qual seu organismo não produz insulina em quantidade suficiente, e a insulina produzida não age tão bem como deveria. Seu organismo também pode produzir muito açúcar. Quando isto ocorre, o açúcar (glicose) no sangue aumenta e pode causar problemas graves de saúde.

O principal objetivo do tratamento do diabetes é normalizar os níveis de açúcar no sangue. A redução e o controle dos níveis de açúcar no sangue podem ajudar a prevenir ou retardar as complicações do diabetes, como doença cardíaca, doença renal, perda da visão e amputação de membros.

Como já foi mencionado, níveis altos de açúcar no sangue podem ser reduzidos por meio de dieta e exercícios e com o uso de alguns medicamentos.', 'Dosagem

Tome JANUVIA® exatamente como seu médico prescreveu. A dose recomendada deve ser tomada da seguinte maneira:

• 1 comprimido de 100 mg,

• uma vez ao dia,

• por via oral, com ou sem alimentos.

Seu médico poderá lhe receitar uma dose mais baixa, caso você apresente problemas renais.

Como usar

Seu médico poderá lhe prescrever JANUVIA® associado a alguns outros medicamentos que reduzem os níveis de açúcar no sangue.

A dieta e a prática de exercícios podem ajudar o seu organismo a utilizar melhor o açúcar existente no sangue. É importante que você mantenha a dieta recomendada pelo seu médico, os exercícios e o programa de perda de peso enquanto estiver tomando JANUVIA®.

Continue a tomar JANUVIA® pelo tempo prescrito pelo seu médico. Assim, você continuará a controlar os níveis de açúcar no sangue.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento.

Não interrompa o tratamento sem o conhecimento do seu médico.', 'Você não deve tomar JANUVIA® se: for alérgico a qualquer um dos seus ingredientes (veja COMPOSIÇÃO).', 'A exemplo de todos os medicamentos, JANUVIA® pode causar efeitos adversos. Nos estudos, os efeitos adversos foram, em geral, leves e não foram motivo para que os pacientes parassem de tomar JANUVIA®. Os efeitos adversos relatados por pacientes que estavam tomando JANUVIA® foram semelhantes aos relatados pelos pacientes que estavam tomando um comprimido que não continha nenhum medicamento (um placebo).

Quando JANUVIA® é tomado em combinação com uma sulfonilureia ou com insulina, podem ocorrer baixos níveis de açúcar no sangue com sintomas (hipoglicemia), em razão da sulfonilureia ou da insulina. Pode ser necessário reduzir as doses de sulfonilureia ou insulina.

Quando JANUVIA® foi utilizado em combinação com insulina, foram relatados os efeitos adversos adicionais:

• resfriado

• dor de cabeça

Quando JANUVIA® e metformina foram iniciados juntos, os seguintes efeitos adversos foram relatados:

• diarreia

• indigestão

• flatulência

• vômitos

• dor de cabeça

Quando JANUVIA® e pioglitazona foram iniciados concomitantemente, houve relato de níveis reduzidos de açúcar no sangue sem sintomas de hipoglicemia.

Quando JANUVIA® foi utilizado em combinação com metformina e rosiglitazona, os seguintes efeitos adversos foram relatados:

• dor de cabeça

• nível baixo de açúcar no sangue, com sintomas (hipoglicemia)

• diarreia

• infecção do trato respiratório superior

• náuseas

• tosse

• infecção fúngica cutânea

• inchaço das mãos ou pernas

• vômitos

Efeitos adversos adicionais foram relatados no uso geral de JANUVIA® isoladamente e/ou em combinação com outros medicamentos para diabetes:

• reações alérgicas, que podem ser graves, incluindo erupção cutânea, urticária e inchaço da face, lábios, língua e garganta que podem causar dificuldade de respirar ou engolir. Se tiver uma reação alérgica, pare de tomar JANUVIA® e ligue para seu médico imediatamente. Seu médico poderá

• prescrever um medicamento para tratar sua reação alérgica e um outro medicamento para seu diabetes.

• inflamação do pâncreas.

• problemas renais (algumas vezes com necessidade de diálise).

• infecção do trato respiratório superior.

• nariz entupido ou escorrendo e dor de garganta.

• constipação (prisão de ventre).

• vômitos.

• dor de cabeça.

• dor nas articulações.

• dores musculares.

• dor no braço ou perna.

• dor nas costas.

• coceira.

• bolhas na pele.

Informe ao seu médico se apresentar qualquer efeito adverso incomum ou se algum efeito adverso conhecido não desaparecer ou piorar.

Seu médico poderá lhe dar informações mais detalhadas sobre JANUVIA® e diabetes.

Atenção: este produto é um medicamento que possui nova indicação terapêutica no país e, embora as pesquisas tenham indicado eficácia e segurança aceitáveis, mesmo que indicado e utilizado corretamente, podem ocorrer eventos adversos imprevisíveis ou desconhecidos. Nesse caso, informe seu médico.', NULL, 'Gravidez e amamentação: mulheres grávidas ou que pretendem engravidar devem consultar seu médico antes de tomar JANUVIA®. O uso de JANUVIA® não é recomendado durante a gravidez.

Ainda não se sabe se JANUVIA® passa para o leite materno, por isso você não deve usar JANUVIA® se estiver amamentando ou se planeja amamentar.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.

Crianças: JANUVIA® não foi estudado em indivíduos com menos de 18 anos de idade.

Idosos: nos estudos, JANUVIA® demonstrou boa ação e foi bem tolerado por pacientes adultos mais idosos, por isso não é necessário ajuste de dose com base na idade.

Precauções: informe ao seu médico se você apresenta ou já apresentou:

• diabetes tipo 1;

• cetoacidose diabética (níveis elevados de cetonas no sangue ou na urina);

• problemas renais ou outros problemas clínicos;

• uma reação alérgica a JANUVIA®.

Enquanto estiver tomando JANUVIA®

Foram relatados casos de inflamação do pâncreas (pancreatite) em pacientes que usavam JANUVIA®. A pancreatite pode ser uma doença grave, até fatal. Pare de tomar JANUVIA® e entre em contato com seu médico se sentir dor estomacal forte e persistente, com ou sem vômitos, porque você pode estar com pancreatite.

Casos de reações na pele denominadas penfigoide bolhoso, que podem exigir tratamento hospitalar, foram relatados entre os pacientes tratados com JANUVIA®. Caso você desenvolva bolhas ou ruptura (erosão) na pele, informe o seu médico. Ele poderá orientá-lo a parar de tomar JANUVIA®.

Dirigir veículos ou operar máquinas: não existem informações sugestivas de que JANUVIA® afete sua capacidade de dirigir veículos ou operar máquinas.

Interações medicamentosas: JANUVIA® pode ser tomado com a maioria dos medicamentos. Informe seu médico sobre todos os medicamentos que esteja tomando, inclusive aqueles obtidos com receita médica, de venda livre e suplementos fitoterápicos (medicamentos feitos a partir de vegetais).

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento.

Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/januvia', 'Vermelha', '2026-11-22T23:23:56.484Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/januvia');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Vildagliptina', 'Vildagliptina', 'O diabetes mellitus tipo 2 se desenvolve quando o corpo não produz quantidade suficiente de insulina ou se a insulina produzida pelo seu corpo não funciona adequadamente. Também pode se desenvolver se o corpo produz muito glucagon.', 'Cada comprimido de Galvus® contém 50 mg da substância ativa vildagliptina.

Galvus® é um medicamento usado para tratar pacientes com diabetes mellitus tipo 2, cuja condição não pode ser controlada pela dieta e exercício sozinhos. Ele ajuda a controlar os níveis sanguíneos de açúcar. Tais medicamentos são conhecidos como antidiabéticos orais.

Galvus® é indicado em combinação com insulina (com ou sem metformina), apenas quando uma dose estável de insulina, aliado à dieta e exercício físico, não resultarem em controle glicêmico adequado.', 'Siga cuidadosamente todas as instruções dadas a você pelo seu médico, mesmo se forem diferentes das informações contidas nessa bula. Não tome mais Galvus® do que o seu médico prescreveu.

QUANDO E COMO TOMAR GALVUS®

Galvus® deve ser tomado pela manhã (50 mg uma vez ao dia), ou pela manhã e à noite (50 mg duas vezes ao dia). Os comprimidos devem ser engolidos inteiros com um copo de água.

QUANTO TOMAR

Seu médico falará exatamente quantos comprimidos de Galvus® tomar.

A dose usual de Galvus® é 50 mg ou 100 mg por dia. A dose de 50 mg deve ser tomada como 50 mg uma vez ao dia (manhã). A dose de 100 mg deve ser tomada como 50 mg duas vezes ao dia (manhã e noite). Dependendo da sua resposta ao tratamento, seu médico pode sugerir uma dose maior ou menor.

Seu médico prescreverá Galvus® tanto sozinho quanto em combinação a outro antidiabético, dependendo da sua condição.

O limite máximo diário de administração é 100 mg/dia.

POR QUANTO TEMPO TOMAR GALVUS®

Continue tomando Galvus® todos os dias por quanto tempo o seu médico disser para fazê-lo. Você pode continuar nesse tratamento por um longo período de tempo. O seu médico monitorará regularmente sua condição para verificar que o tratamento está surtindo o efeito desejável.

Se seu médico o orientou a interromper o tratamento com Galvus® devido a problemas no fígado, você não deve recomeçar o tratamento com Galvus®.

Se você tiver dúvida sobre quanto tempo tomar Galvus®, fale com o seu médico.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento. Não interrompa o tratamento sem o conhecimento do seu médico.

Este medicamento não deve ser partido, aberto ou mastigado.', 'Não tome Galvus®

Se você for alérgico (hipersensível) à vildagliptina ou a qualquer outro excipiente de Galvus®.', 'Como todos os medicamentos, pacientes tratados com Galvus® podem apresentaralgumas reações adversas, embora nem todos apresentem.

Alguns pacientes apresentaram as seguintes reações adversas enquanto tomavam Galvus® sozinho ou em combinação com outro medicamento antidiabético.

ALGUNS SINTOMAS NECESSITAM DE CUIDADOS MÉDICOS IMEDIATOS:

Você deve interromper o tratamento com Galvus® e procurar imediatamente seu médico se você apresentar os seguintes sintomas:

• Face, língua ou garganta inchadas, dificuldade para engolir, dificuldade para respirar, aparecimento repentino de erupção cutânea ou urticária (sintomas de reação alérgica grave, resultando em inchaço, conhecida como “angioedema”);

• Pele e/ou olhos amarelados, náusea, perda de apetite, urina de coloração escura (possíveis sintomas de problemas no fígado);

• Dor de forte intensidade na região do estômago (possível sintoma de inflamação no pâncreas);

• Dor de cabeça, sonolência, fraqueza, tonturas, confusão, irritabilidade, fome, batimento cardíaco acelerado, sudorese, sensação nervosa (possíveis sintomas de um baixo nível de açúcar no sangue conhecido como "hipoglicemia").

OUTRAS REAÇÕES ADVERSAS

Reações adversas comuns (ocorrem entre 1% e 10% dos pacientes que utilizam este medicamento).

Reações adversas incomuns (ocorrem entre 0,1% e 1% dos pacientes que utilizam este medicamento).

Alguns pacientes apresentaram as seguintes reações adversas enquanto tomavam Galvus® em monoterapia:

• Comum: tontura;

• Incomuns: dor de cabeça, constipação, mãos, tornozelos ou pés inchados (edema).

Alguns pacientes apresentaram as seguintes reações adversas enquanto tomavam Galvus® em combinação com metformina:

• Comuns: tremor, dor de cabeça, tontura.

Alguns pacientes apresentaram as seguintes reações adversas enquanto tomavam Galvus® em combinação com uma sulfonilureia:

• Comuns: tremor, dor de cabeça, tontura, fraqueza.

Alguns pacientes apresentaram as seguintes reações adversas enquanto tomavam Galvus® em combinação com uma glitazona:

• Comuns: aumento de peso, mãos, tornozelos ou pés inchados (edema);

• Incomum: dor de cabeça.

Alguns pacientes apresentaram as seguintes reações adversas enquanto tomavam Galvus® em combinação com insulina (com ou sem metformina):

• Comuns: dor de cabeça, náusea, calafrios, hipoglicemia (baixa quantidade de açúcar no sangue), azia;

• Incomuns: diarreia, flatulência.

Alguns pacientes apresentaram as seguintes reações adversas enquanto tomavam Galvus® em combinação com metformina e sulfonilureia:

• Comuns: tontura, tremor, fraqueza, baixo nível de glicose (açúcar) no sangue, sudorese excessiva.

Se qualquer uma dessas condições afetar você gravemente, fale com o seu médico.

Alguns pacientes apresentaram outras reações adversas enquanto tomavam Galvus® em monoterapia ou em combinação com outra medicação antidiabética:

• Prurido, descamação de áreas da pele ou bolhas, dor nas articulações.

Se alguma dessas condições afetar você gravemente, fale com o seu médico.

Se você notar qualquer outra reação adversa não mencionada nessa bula, por favor, informe ao seu médico.

Atenção: este produto é um medicamento que possui nova indicação terapêutica no país e, embora as pesquisas tenham indicado eficácia e segurança aceitáveis, mesmo que indicado e utilizado corretamente, podem ocorrer eventos adversos imprevisíveis ou desconhecidos. Nesse caso, informe seu médico.', NULL, 'Siga cuidadosamente todas as instruções dadas a você pelo seu médico mesmo se forem diferentes das informações contidas nessa bula.

TOME CUIDADOS ESPECIAIS COM GALVUS®

• Se você está ou planeja ficar grávida;

• Se você está amamentando;

• Se você tem problemas no rim;

• Se você tem problemas no fígado;

• Se você sofre de insuficiência cardíaca, seu médico irá decidir se prescreverá Galvus® ou não, dependendo do grau da sua condição;

• Galvus® não é um substituto da insulina. Você não deve, portanto, receber Galvus® para o tratamento de diabetes mellitus tipo 1 (ou seja, se seu corpo não produz insulina) nem para o tratamento de uma condição chamada cetoacidose diabética.

Se qualquer uma das condições acima se aplicar a você, fale com o seu médico antes de tomar Galvus®.

Existe uma possibilidade de hipoglicemia quando o produto for administrado em combinação com sulfonilureia ou com insulina.

MONITORANDO SEU TRATAMENTO COM GALVUS®

Seu médico deve assegurar que os seguintes testes sejam realizados:

• Testar regularmente o açúcar no sangue e na urina;

• Verificar sua função hepática:

• no início do tratamento;

• a cada 3 meses durante o primeiro ano de tratamento e regularmente após este período;

• se seu médico o orientou a interromper o tratamento com Galvus® devido a problemas no fígado, você não deve recomeçar o tratamento com Galvus®.

TOMANDO GALVUS® DURANTE A REFEIÇÃO

Galvus® pode ser tomado junto ou não de uma refeição.

GALVUS® E IDOSOS

Galvus® pode ser utilizado por pacientes idosos.

GALVUS® E CRIANÇAS/ADOLESCENTES

Não há informações disponíveis sobre o uso de Galvus® em crianças e adolescentes (com idade menor de 18 anos). O uso de Galvus® nesses pacientes não é, portanto, recomendado.

MULHERES GRÁVIDAS

Avise o seu médico se você está grávida, se você acha que está grávida ou se você está planejando ficar grávida. Seu médico discutirá com você o risco potencial de tomar Galvus® durante a gravidez.

Peça auxílio ao seu médico antes de tomar qualquer medicamento durante a gravidez. Seu médico discutirá com você o risco potencial de tomar Galvus® durante a gravidez..

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.

MULHERES AMAMENTANDO

Não amamente durante o tratamento com Galvus®.

Peça auxílio ao seu médico antes de tomar qualquer medicamento durante a amamentação. Seu médico discutirá com você o risco potencial de tomar Galvus® durante a amamentação.

DIRIGINDO E OPERANDO MÁQUINAS

Pacientes que sentirem tontura devem evitar dirigir veículos ou operar máquinas.

INFORMAÇÕES IMPORTANTES SOBRE ALGUNS EXCIPIENTES DE GALVUS®

Galvus® contém um excipiente chamado lactose (açúcar do leite). Se você tem grave intolerância à lactose, galactose ou má absorção de glicose-galactose, avise ao seu médico antes de tomar Galvus®.

PANCREATITE AGUDA

Pare de tomar Galvus® e entre em contato com seu médico se você apresentar dores de estomago fortes e persistentes, com ou sem vômito, pois você pode estar com pancreatite.

Não existem dados conclusivos de redução de riscos micro e macrovasculares com o produto.

TOMANDO OUTROS MEDICAMENTOS

Avise ao seu médico se você está tomando ou tomou qualquer outro medicamento recentemente. Por favor, lembre-se também daqueles medicamentos que não foram prescritos por um médico.

A vildagliptina tem baixo potencial para interações com outros medicamentos. Uma vez que a vildagliptina não é um substrato das enzimas do citocromo P (CYP) 450 nem inibe ou induz as enzimas CYP 450 (complexo de enzimas do fígado responsável pela metabolização de um grande número de medicamentos), não é comum a interação com comedicações que são substratos, inibidores ou indutores dessas enzimas.

Além disso, a vildagliptina não afeta a depuração metabólica de comedicações metabolizadas pela CYP 1A2, CYP 2C8, CYP 2C9, CYP 2C19, CYP 2D6, CYP 2E1, e CYP 3A4/5 (complexo de enzimas responsável pela metabolização de medicamentos).

Estudos de interação medicamentosa foram conduzidos com medicações comumente prescritas simultaneamente para pacientes com diabetes mellitus tipo 2 ou medicações com uma janela terapêutica estreita (isto é, medicamentos que apresentam a dose terapêutica próxima da dose tóxica). Como resultado desses estudos, não foi observada nenhuma interação de importância clínica com a administração simultânea da vildagliptina com outros antidiabéticos orais (glibenclamida, pioglitazona, metformina), anlodipino, digoxina, ramipril, sinvastatina, valsartana ou varfarina.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use este medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/galvus', 'Vermelha', '2026-11-22T23:23:56.485Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/galvus');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Empagliflozina', 'Empagliflozina', 'A empagliflozina é um inibidor competidor reversível, altamente potente e seletivo do SGLT-2 (cotransportador de sódio e glicose 2) com um IC50 de 1,3 nM, que tem uma seletividade 5.000 vezes maior em relação ao SGLT1 (cotransportador de sódio e glicose 1) humano (IC50 de 6.278 nM), responsável pela absorção da glicose no intestino. Além disso, a seletividade elevada pode ser demonstrada para outros transportadores de glicose (GLUTs) responsáveis pela homeostase da glicose em diferentes tecidos.', 'JARDIANCE é indicado para o tratamento do diabetes mellitus tipo 2 (DM2) para melhorar o controle glicêmico em conjunto com dieta e exercícios. Pode ser utilizado como monoterapia ou em associação com metformina, tiazolidinedionas, metformina mais sulfonilureia, ou insulina com ou sem metformina com ou sem sulfonilureia.', 'O comprimido de JARDIANCE deve ser ingerido por via oral, com ou sem alimentos. A dose inicial recomendada é de 10 mg uma vez ao dia. O tratamento da hiperglicemia em pacientes com diabetes mellitus tipo 2 deve ser individualizado com base na eficácia e tolerabilidade. A dose máxima de 25 mg ao dia pode ser utilizada, porém não deve ser excedida.

Pacientes com comprometimento renal:

Não há necessidade de ajuste de dose em pacientes com taxa de filtração glomerular ≥ 30 mL/min/1,73 m2.

Pacientes com comprometimento hepático:

Não se recomenda ajuste de dose em pacientes com insuficiência hepática.

Pacientes idosos:

Não se recomenda ajuste de dose em pacientes idosos. A experiência terapêutica em pacientes com 85 anos de idade ou mais é limitada. O início da terapia com empagliflozina nesta população não é recomendada.

Pacientes pediátricos:

Em pacientes pediátricos e adolescentes abaixo de 18 anos, a segurança e a eficácia de JARDIANCE não foram estabelecidas.

Terapia de combinação:

Quando JARDIANCE é utilizado em combinação com uma sulfonilureia ou com insulina, pode-se considerar a dose mais baixa destas para reduzir o risco de hipoglicemia.

ESQUECIMENTO DE DOSE

Se uma dose for esquecida, deve ser tomada assim que o paciente se recordar. Não se deve tomar uma dose duplicada no mesmo dia.

Este medicamento não deve ser partido ou mastigado.', 'JARDIANCE é contraindicado para pacientes com hipersensibilidade à empagliflozina, ou aos excipientes da fórmula, ou em caso de doenças hereditárias raras que podem ser incompatíveis com os excipientes da fórmula.', 'Um total de 15.582 pacientes com diabetes tipo 2 foram tratados em estudos clínicos para avaliar a segurança de empagliflozina, dos quais 10.004 pacientes foram tratados com empagliflozina, sozinha ou em combinação com metformina, sulfonilureia, agonista de PPARγ (receptor ativado por proliferadores de peroxissoma-gama), inibidores de DPP4 ou insulina.

Este conjunto inclui o estudo EMPA-REG OUTCOME® envolvendo 7.020 pacientes com alto risco cardiovascular (idade média de 63,1 anos; 9,3% dos pacientes com pelo menos 75 anos de idade; 28,5% mulheres) tratados com JARDIANCE 10 mg ao dia (n = 2.345), JARDIANCE 25 mg ao dia (n = 2.342), ou placebo (n = 2.333) por até 4,5 anos. O perfil de segurança global da empagliflozina neste estudo foi comparável ao perfil de segurança previamente conhecido.

Nos estudos descritos acima, a frequência de eventos adversos que conduziu à descontinuação foi semelhante em grupos tratados com placebo, JARDIANCE 10 mg e JARDIANCE 25 mg.

Os estudos controlados por placebo, duplo-cego, de 18 a 24 semanas de exposição incluíram 3.534 pacientes, dos quais 1.183 foram tratados com placebo, 1.185 foram tratados com JARDIANCE 10 mg e 1.166 foram tratados com JARDIANCE 25 mg.

A reação adversa mais frequente foi a hipoglicemia, que depende do tipo de terapia de base usada nos respectivos estudos (vide “Descrição de reações adversas selecionadas” a seguir).

As reações adversas relatadas em pacientes que receberam JARDIANCE em estudos controlados por placebo, duplo-cegos, de 18 até 24 semanas e reações adversas derivadas de experiência pós-comercialização estão classificadas por frequência:

Reação muito comum (≥ 1/10): hipoglicemia (quando utilizado com sulfonilureia ou insulina)a.

Reações comuns (≥ 1/100 e < 1/10): monilíase vaginal, vulvovaginite, balanite e outras infecções genitaisa, micção aumentadaa, prurido, reações alérgicas de pele (ex. rash, urticária)b, infecções do trato urinárioa (incluindo pielonefrite e urosepse)b, sede, aumento dos lipídios séricosc.

Reações incomuns (≥ 1/1.000 e < 1/100): hipovolemiaa, disúria, aumento da creatinina séricaa, cetoacidoseb, taxa de filtração glomerular diminuídaa, aumento do hematócritoc (para JARDIANCE 25 mg).

Reação rara (≥ 1/10.000 e < 1/1000): aumento do hematócritoc (para JARDIANCE 10 mg).

Reação com frequência desconhecida: angioedemab.

a vide subitens a seguir para informações adicionais.

b derivadas de experiência pós-comercialização.

c vide “2.RESULTADOS DE EFICÁCIA” para informações adicionais.

DESCRIÇÃO DE REAÇÕES ADVERSAS SELECIONADAS

As frequências das reações adversas abaixo foram calculadas independentemente de suas causalidades.

HIPOGLICEMIA

A frequência de hipoglicemia dependeu da terapia de base que foi utilizada nos respectivos estudos e foi semelhante na monoterapia com JARDIANCE e com o placebo, em combinação com metformina, em combinação com pioglitazona com ou sem metformina e em combinação com linagliptina mais metformina. A frequência de pacientes com hipoglicemia foi maior em pacientes tratados com JARDIANCE em comparação ao placebo, quando administrado em combinação com metformina mais sulfonilureia, e em combinação com insulina com ou sem metformina e com ou sem sulfonilureia.

HIPOGLICEMIA GRAVE (EVENTOS QUE NECESSITAM DE ASSISTÊNCIA)

A frequência de pacientes com eventos hipoglicêmicos graves foi baixa (<1%) e semelhante na monoterapia com JARDIANCE e com o placebo, em combinação com metformina com ou sem sulfonilureia, em combinação com pioglitazona com ou sem metformina e em combinação com linagliptina mais metformina.

A frequência de pacientes com eventos hipoglicêmicos graves foi maior em pacientes tratados com JARDIANCE em comparação ao placebo, quando administrado em combinação com insulina com ou sem metformina e com ou sem sulfonilureia.

Tabela 18 – Frequência de pacientes com eventos de hipoglicemia confirmados por estudo (1245.19; 1245.20; 1245.23(met); 1245.23(met+SU); 1245.33, 1245.49; 1275.9(lina+met) e 1245.25 Conjunto Tratado1).

Grupo de Tratamento

Placebo

JARDIANCE 10 mg

JARDIANCE 25 mg

Monoterapia (1245.20) (24 semanas)

n

229

224

223

Geral confirmado (%)

0,4%

0,4%

0,4%

Grave (%)

0%

0%

0%

Em combinação com metformina (1245.23 (met)) (24 semanas)

n

206

217

214

Geral confirmado (%)

0,5%

1,8%

1,4%

Grave (%)

0%

0%

0%

Em combinação com metformina + sulfonilureia (1245.23 (met + SU)) (24 semanas)

n

225

224

217

Geral confirmado (%)

8,4%

16,1%

11,5%

Grave (%)

0%

0%

0%

Em combinação com pioglitazona com ou sem metformina (1245.19) (24 semanas)

n

165

165

168

Geral confirmado (%)

1,8%

1,2%

2,4%

Grave (%)

0%

0%

0%

Em combinação com insulina basal (1245.33) (18 semanas / 78 semanas)

n

170

169

155

Geral confirmado (%)

20,6%/ 35,3%

19,5%/ 36,1%

28,4%/ 36,1%

Grave (%)

0%/ 0%

0%/ 0%

1,3%/ 1,3%

Em combinação com insulina em múltiplas doses com ou sem metformina (1245.49) (18 semanas / 52 semanas)

n

188

186

189

Geral confirmado (%)

37,2%/ 58,0%

39,8%/ 51,1%

41,3%/ 57,7%

Grave (%)

1,6%/ 1,6%

1,6%/ 1,6%

0,5%/ 0,5%

Em combinação com metformina e linagliptina (1275.9) (24 semanas)

n

n=110

n=112

n=110

Geral confirmado (%)

0,9%

0.0%

2.7%

Grave (%)

0%

0%

0%

EMPA-REG OUTCOME® (1245.25)

n

2333

2345

2342

Geral confirmado (%)

27,9%

28%

27,6%

Grave (%)

1,5%

1,4%

1,3%

Confirmado: glicemia ≤70 mL/dL ou assistência necessária.

Grave: assistência necessária.

ou seja pacientes que receberam pelo menos uma dose do fármaco no estudo

A dose de insulina como medicação de base deveria ser estável durante as primeiras 18 semanas

Esta foi uma combinação em dose fixa de empagliflozina com linagliptina 5 mg com um tratamento de base com metformina (vide “2. RESULTADOS DE EFICÁCIA”)

Dados Fonte: 1245.19 [U12-1516, Tabela 15.3.2.3: 3], 1245.20 [U12-1517, Tabela 15.3.2.3: 2], 1245.23 [U12-1518, Tabelas 15.1.3.2.3: 3 e 15.2.3.2.3: 3], 1245.33 [U12-3817, Tabelas 15.3.2.3: 3 e 15.4.5: 3], 1245.49 [U13-2122, Tabelas 15.3.2.4: 3 e 15.3.2.5: 3], 1275.9 [c02820144-01 Tabela 15.4.2: 12]

INFECÇÃO DO TRATO URINÁRIO

A frequência global de eventos adversos de infecção do trato urinário foi semelhante nos pacientes tratados com JARDIANCE 25 mg e placebo (7,0% e 7,2%) e superior nos pacientes tratados com JARDIANCE 10 mg (8,8 %). Semelhante ao placebo, a infecção do trato urinário foi relatada com mais frequência para JARDIANCE em pacientes com histórico de infecções do trato urinário crônicas ou recorrentes. A intensidade de infecções do trato urinário foi semelhante à do placebo para relatos de intensidade leve, moderada e grave. Eventos de infecções do trato urinário foram relatados mais frequentemente para empagliflozina em comparação com placebo em pacientes do sexo feminino, mas não em pacientes do sexo masculino.

MONILÍASE VAGINAL, VULVOVAGINITES, BALANITE E OUTRAS INFECÇÕES GENITAIS

Monilíase vaginal, vulvovaginites, balanite e outras infecções genitais foram relatadas mais frequentemente para JARDIANCE 10 mg (4,0%) e JARDIANCE 25 mg (3,9%) em comparação com placebo (1,0%) e foram mais frequentes para empagliflozina comparado ao placebo em pacientes do sexo feminino. A diferença de frequência foi menos pronunciada em pacientes do sexo masculino. As infecções do trato genital foram de intensidade leve ou moderada, e não houve eventos de intensidade grave.

AUMENTO DA MICÇÃO

Como esperado, devido ao seu mecanismo de ação, observou-se em frequências mais elevadas aumento da micção (tal como avaliado pela pesquisa de termo preferencial incluindo polaciúria, poliúria, noctúria) em pacientes tratados com JARDIANCE 10 mg (3,5%) e JARDIANCE 25 mg (3,3%) em comparação ao placebo (1,4%). O aumento da micção foi em sua maioria de intensidade leve ou moderada. A frequência de noctúria relatada foi comparável entre placebo e JARDIANCE (<1%).

HIPOVOLEMIA

A frequência global de hipovolemia (incluindo os termos predefinidos, queda da pressão arterial (ambulatorial), queda da pressão arterial sistólica, desidratação, hipotensão, hipovolemia, hipotensão ortostática e síncope) foi semelhante ao placebo (JARDIANCE 10 mg = 0,6 %, JARDIANCE 25 mg = 0,4% e placebo = 0,3%). O efeito da empagliflozina na excreção urinária de glicose está associado à diurese osmótica, o que poderia afetar o estado de hidratação de pacientes com 75 anos de idade ou mais. Em pacientes com 75 anos ou mais de idade (agrupamento de todos os pacientes com diabetes, n = 13.402), a frequência de eventos de hipovolemia foi semelhante para JARDIANCE 10 mg (2,3%) em comparação com o placebo (2,1%), mas aumenta com JARDIANCE 25 mg (4,3%).

AUMENTO DA CREATININA SÉRICA E TAXA DE FILTRAÇÃO GLOMERULAR DIMINUÍDA

A frequência global de pacientes com aumento da creatinina sérica e diminuição da taxa de filtração glomerular foi semelhante entre empagliflozina e placebo (aumento da creatinina sérica: empagliflozina 10 mg= 0,6%, empagliflozina 25 mg= 0,1%, placebo= 0,5%; taxa de filtração glomerular diminuída: empagliflozina 10 mg= 0,1 %, empagliflozina 25 mg= 0%, placebo= 0,3%).

Em estudos duplo-cegos, controlados por placebo e de duração de 76 semanas, foram observados aumentos transitórios iniciais da creatinina (alteração média em relação ao basal após 12 semanas: empagliflozina 10 mg= 0,02 mg/dL, empagliflozina 25 mg= 0,01 mg/dL) e diminuição transitória inicial nas taxas de filtração glomerular estimada (alteração média em relação ao basal após 12 semanas: empagliflozina 10 mg= -1,34 mL/min/1,73m2, empagliflozina 25 mg= -1,37 mL/min/1,73m2). Estas alterações foram geralmente reversíveis durante o tratamento contínuo ou após a descontinuação do medicamento (vide “2. RESULTADOS DE EFICÁCIA”, figura 6: TFGe ao longo do tempo, do estudo EMPA-REG OUTCOME®).

Atenção: este produto é um medicamento novo e, embora as pesquisas tenham indicado eficácia e segurança aceitáveis, mesmo que indicado e utilizado corretamente, podem ocorrer eventos adversos imprevisíveis ou desconhecidos.

Nesse caso, notifique os eventos adversos pelo Sistema de Notificações em Vigilância Sanitária - NOTIVISA ou a Vigilância Sanitária Estadual ou Municipal.', 'INTERAÇÕES FARMACODINÂMICAS

Diuréticos: a empagliflozina pode aumentar o efeito diurético de tiazídicos e diuréticos de alça e pode aumentar o risco de desidratação e hipotensão.

Insulina e secretagogos de insulina tais como as sulfonilureias, podem aumentar o risco de hipoglicemia. Portanto, uma dose mais baixa de insulina ou um secretagogo de insulina pode ser necessário para reduzir o risco de hipoglicemia, quando usados em combinação com empagliflozina.

Interferência com teste de 1,5-anidroglucitol (1,5-AG): o monitoramento do controle da glicemia com teste de 1,5-AG não é recomendado, pois as medidas de 1,5-AG não são confiáveis na avaliação do controle glicêmico nos pacientes em uso de inibidores de SGLT-2. Utilizar métodos alternativos para monitorar o controle glicêmico.

INTERAÇÕES FARMACOCINÉTICAS

Avaliação in vitro de interações farmacológicas: a empagliflozina não inibe, inativa ou induz as isoformas do CYP450. Os dados in vitro sugerem que a via principal de metabolismo da empagliflozina em humanos é a glicuronidação pela uridina 5''-difosfo-glicuronosiltransferases UGT2B7, UGT1A3, UGT1A8 e UGT1A9. A empagliflozina não inibe a UGT1A1, UGT1A3, UGT1A8, UGT1A9 ou UGT2B7. Em doses terapêuticas, o potencial para a empagliflozina inibir ou inativar reversivelmente as principais isoformas do CYP450 e UGT é remota. Interações medicamentosas envolvendo as principais isoformas do CYP450 e UGT com empagliflozina e substratos administrados concomitantemente destas enzimas são, portanto, consideradas improváveis.

A empagliflozina é um substrato da glicoproteína P (P-gp) e da proteína de resistência ao câncer de mama (BCRP), mas não inibe estes transportadores de efluxo em doses terapêuticas. Com base nos estudos in vitro, considera-se improvável que a empagliflozina cause interações com medicamentos que são substratos da glicoproteína P. A empagliflozina é um substrato dos transportadores humanos de captação de OAT3, OATP1B1 e OATP1B3, mas não de OAT1 e OCT2. A empagliflozina não inibe nenhum desses transportadores humanos de captação em concentrações no plasma clinicamente relevantes e, como tal, considera-se improvável as interações medicamentosas com os substratos destes transportadores de captação.

Avaliação in vivo de interações farmacológicas: não se observou interações farmacocinéticas clinicamente significativas quando a empagliflozina foi coadministrada com outros medicamentos habitualmente utilizados. Com base nos resultados dos estudos farmacocinéticos, não se recomenda ajuste na dose de JARDIANCE quando coadministrado com outros medicamentos prescritos com frequência.

A farmacocinética da empagliflozina foi semelhante com e sem a coadministração de metformina, glimepirida, pioglitazona, sitagliptina, linagliptina, varfarina, verapamil, ramipril e sinvastatina em voluntários sadios e com ou sem coadministração de torasemida e hidroclorotiazida em pacientes com diabetes mellitus tipo 2. Observou-se aumentos na exposição total (AUC) da empagliflozina após coadministração com genfibrozila (59%), rifampicina (35%), ou probenecida (53%). Estas alterações não foram consideradas clinicamente significativas.

A empagliflozina não teve efeito clinicamente relevante sobre a farmacocinética da metformina, glimepirida, pioglitazona, sitagliptina, linagliptina, varfarina, digoxina, ramipril, sinvastatina, hidroclorotiazida, torasemida e contraceptivos orais, quando coadministradas em voluntários sadios.', 'JARDIANCE não deve ser usado em pacientes com diabetes mellitus do tipo 1.

CETOACIDOSE DIABÉTICA

Casos de cetoacidose diabética, uma condição grave com risco de vida e com necessidade de hospitalização urgente, foram relatados em pacientes tratados com empagliflozina, incluindo casos fatais. Em um número de casos relatados, a apresentação desta condição foi atípica com valores de glicemia discretamente aumentada, abaixo de 250 mg/dL.

O risco de cetoacidose diabética deve ser considerado no caso de sintomas não específicos como náusea, vômito, anorexia, dor abdominal, sede excessiva, dificuldade de respiração, confusão, cansaço anormal e sonolência.

Os pacientes devem ser avaliados quanto à cetoacidose imediatamente se estes sintomas ocorrerem, independentemente

do nível de glicemia. Se houver suspeita de cetoacidose, o tratamento com JARDIANCE deve ser interrompido, o paciente deve ser avaliado e tratamento imediato deve ser instituído.

JARDIANCE deve ser usado com cautela em pacientes que possam apresentar um maior risco de cetoacidose diabética enquanto usam JARDIANCE, incluindo aqueles sob uma dieta com restrição de carboidratos (visto que a combinação pode aumentar ainda mais a produção de corpos cetônicos), pacientes com doenças agudas, doenças pancreáticas sugerindo deficiência de insulina (por exemplo, diabetes tipo 1, história de pancreatite ou cirurgia de pâncreas), redução da dose de insulina (incluindo a falha da bomba de insulina), abuso de álcool, desidratação grave e pacientes com história de cetoacidose. Deve-se ter cautela ao reduzir a dose de insulina (vide “8. POSOLOGIA E MODO DE USAR”). Em pacientes tratados com JARDIANCE deve-se considerar monitoramento da cetoacidose e a interrupção temporária de JARDIANCE em situações clínicas conhecidas por predispor à cetoacidose (por exemplo, jejum prolongado devido à doença aguda ou cirurgia).

PACIENTES COM COMPROMETIMENTO RENAL

JARDIANCE não é recomendado para uso em pacientes com taxa de filtração glomerular <30 mL/min/1,73 m2.

MONITORAMENTO DA FUNÇÃO RENAL

Devido ao mecanismo de ação, a eficácia da empagliflozina depende da função renal. Assim, recomenda-se a avaliação da função renal antes do início do tratamento com JARDIANCE e periodicamente durante o tratamento, ou seja, pelo menos anualmente.

PACIENTES COM RISCO DE HIPOVOLEMIA

Com base no mecanismo de ação dos inibidores de SGLT-2, a diurese osmótica que acompanha a glicosúria terapêutica pode levar a uma modesta redução na pressão arterial. Portanto, recomenda-se precaução em pacientes para os quais uma queda da pressão arterial induzida pela empagliflozina pode representar um risco, como em pacientes com doença cardiovascular conhecida, pacientes em terapia anti-hipertensiva com histórico de hipotensão ou pacientes com 75 anos ou mais.

Em caso de condições que podem levar à perda de líquido (por exemplo, doença gastrintestinal), recomenda-se um cuidadoso monitoramento do volume (por exemplo, exame físico, medidas de pressão arterial, exames laboratoriais incluindo hematócrito) e eletrólitos para pacientes que fazem uso de empagliflozina. A interrupção temporária do tratamento com JARDIANCE a deve ser considerada até que a perda de líquido seja corrigida.

INFECÇÕES DO TRATO URINÁRIO

Nos estudos agrupados, duplo-cego, controlados por placebo de 18 a 24 semanas de duração, a frequência global de infecção do trato urinário reportado como evento adverso foi maior do que com o placebo em pacientes tratados com empagliflozina 10 mg e semelhante ao placebo em pacientes tratados com empagliflozina 25 mg (vide “9. REAÇÕES ADVERSAS”). Casos de infecções complicadas do trato urinário incluindo pielonefrite e urosepse foram relatados no pós-comercialização em pacientes tratados com empagliflozina. Deve-se considerar a interrupção temporária de JARDIANCE em pacientes com infecções complicadas do trato urinário.

PACIENTES IDOSOS

Pacientes com 75 anos de idade ou mais podem apresentar risco elevado de hipovolemia, portanto, JARDIANCE deve ser prescrito com cautela a estes pacientes.

A experiência terapêutica em pacientes com 85 anos de idade é limitada. O início da terapia com JARDIANCE nesta população não é recomendada.

Os comprimidos de JARDIANCE 10 mg contém 162,5 mg de lactose e os de JARDIANCE 25 mg contém 113 mg de lactose por dose máxima diária recomendada. Pacientes que possuem condições hereditárias raras de intolerância à galactose (ex. galactosemia) não devem tomar este medicamento.

Os comprimidos de JARDIANCE 10 mg e JARDIANCE 25 mg contêm menos de 23 mg de sódio, sendo, portanto, considerados livres de sódio.

EFEITOS SOBRE A CAPACIDADE DE DIRIGIR E OPERAR MÁQUINAS

Não foram realizados estudos sobre os efeitos na capacidade de dirigir e operar máquinas.

GRAVIDEZ, LACTAÇÃO E FERTILIDADE

GRAVIDEZ

Existem poucos dados sobre a utilização de JARDIANCE em mulheres grávidas. Estudos pré-clínicos não indicaram

efeitos prejudiciais diretos ou indiretos com relação à toxicidade reprodutiva. Como medida de precaução, recomenda- se evitar o uso de JARDIANCE durante a gravidez a menos que seja claramente necessário.

LACTAÇÃO

Não há dados em humanos sobre a excreção da empagliflozina no leite. Dados pré-clínicos disponíveis em animais mostraram excreção de empagliflozina no leite. Não se pode excluir um risco para os recém-nascidos/bebês. Recomenda-se descontinuar a amamentação durante o tratamento com JARDIANCE.

FERTILIDADE

Nenhum estudo sobre o efeito na fertilidade humana foi realizado com JARDIANCE. Estudos pré-clínicos em animais não indicaram efeitos prejudiciais diretos ou indiretos sobre a fertilidade.

JARDIANCE está classificado na categoria B de risco na gravidez.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.', 'https://www.bulas.med.br/bula/jardiance-profissional', 'Vermelha', '2026-11-22T23:23:56.489Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/jardiance-profissional');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Fumarato de Formoterol', 'Fumarato de Formoterol', 'Foradil® contém uma substância ativa chamada fumarato de formoterol di-hidratado. Ele pertence a um grupo de medicamentos chamado “broncodilatadores” ou “beta-agonistas de longa duração”. Cada cápsula de Foradil® pó para inalação contém 12 mcg de fumarato de formoterol di-hidratado e é para ser usado no inalador Aerolizer® fornecido.', 'Foradil® é utilizado no tratamento de problemas respiratórios em asma e outras doenças que envolvam as vias aéreas, como bronquite crônica e enfisema, que pode ser chamado de Doença Pulmonar Obstrutiva Crônica (DPOC).', 'Siga cuidadosamente a orientação do seu médico.

Sempre utilize Foradil® exatamente como orientado pelo médico ou farmacêutico. Solicite a ajuda de um deles, caso não esteja certo de como utilizar o inalador Aerolizer®.

Não exceda a dose recomendada.

Não engula as cápsulas – As cápsulas devem ser utilizadas para inalação do conteúdo com o inalador Aerolizer®.

Antes de inalar o medicamento, leia atentamente estas instruções, pois elas contêm informações importantes sobre o produto. Em caso de dúvida, peça orientação ao seu médico.

Seu médico sempre lhe dirá por quanto tempo você deve tomar Foradil® e quanto tomar, dependendo das suas necessidades.

COMO USAR AS CÁPSULAS DE FORADIL® COM O INALADOR

Atenção: Não engolir as cápsulas. Usar exclusivamente para inalação.

Siga as instruções para aprender a utilizar as cápsulas de Foradil® com o inalador.

Use as cápsulas de Foradil® apenas com o inalador fornecido na embalagem. Ele foi desenvolvido especialmente para o uso das cápsulas de Foradil®.

Remova as cápsulas do blíster apenas imediatamente antes de seu uso. Certifique-se que seus dedos estejam completamente secos para não molhar a cápsula.

Não engula a cápsula. O pó dentro da cápsula é para inalação apenas.

Foradil® é utilizado no tratamento de doenças respiratórias; portanto, sua administração incorreta não produzirá o efeito desejado. O inalador é composto pelas seguintes partes:

• uma capa azul para proteger o bocal da base;

• a base que permite uma liberação adequada do medicamento da cápsula;

A base é composta de:

• bocal;

• compartimento para a cápsula;

• botões azuis com “asas” (peças laterais projetadas) e pinos em cada lado;

• um canal de entrada do ar.

Instruções para o uso correto

• Retire a tampa do inalador.

• Abra o compartimento da cápsula, segurando firmemente a base do inalador e girando o bocal na direção indicada pela seta.

• Assegure que seus dedos estejam completamente secos. Retire uma cápsula do blíster apenas imediatamente antes do seu uso e coloque-a horizontalmente no fundo do compartimento da cápsula.

IMPORTANTE: Não coloque a cápsula no bocal!

• Feche o compartimento da cápsula, voltando o bocal até que você escute um “click”.

• Para liberar o pó da cápsula:

• Segure o inalador na posição vertical com o bocal para cima;

• Pressione firme e simultaneamente os botões azuis, para romper a cápsula. Em seguida, solte os botões. Faça isso apenas uma vez.

Obs: Neste passo, a cápsula pode partir-se em pequenos fragmentos de gelatina que podem atingir sua boca ou a garganta, no entanto, a gelatina é comestível e, portanto, não é prejudicial.

• Expire o máximo possível.

• Para inalar seu medicamento profundamente para suas vias aéreas:

• Coloque o bocal do inalador na boca e incline levemente sua cabeça para trás;

• Feche firmemente os lábios ao redor dele;

• Inspire, pela boca, de maneira rápida, constantemente, e o mais profundamente possível.

Obs: Você deve ouvir um som de vibração, como se a cápsula girasse no espaço superior ao compartimento da cápsula. Se não ouvir esse ruído, abra o compartimento da cápsula e verifique se a cápsula está solta. Em seguida, repita o passo 7.

• NÃO TENTE DESPRENDER A CÁPSULA apertando repetidamente os botões.

• Após inspirar através do inalador, segure sua respiração pelo maior tempo que você confortavelmente conseguir; enquanto isso retire o inalador da boca. Em seguida, expire pelo nariz. Você sentirá um sabor doce da lactose em sua boca. Abra o compartimento da cápsula e verifique se ainda há resíduo de pó na cápsula. Se ainda restar pó na cápsula, repita os passos de 6 a 8.

• Após o uso de todo o pó, abra o compartimento da cápsula (vide passo 2). Remova a cápsula vazia e utilize um pano seco ou uma escova macia para remover qualquer pó que restou.

Obs: NÃO UTILIZE ÁGUA para limpar o inalador.

• Feche o bocal e recoloque a tampa.

Por quanto tempo o inalador pode ser utilizado: a dose exata liberada de medicamento pelo inalador foi estudada para o uso de até 360 cápsulas. Você precisará de um novo inalador após o uso de 360 cápsulas com o seu inalador atual para garantir a dose correta do medicamento.

Portanto, após o uso de 2 embalagens de Foradil® Refil de 60 cápsulas por medicamento, você precisará adquirir uma embalagem de Foradil® contendo um inalador.

Você não deve usar o inalador após tê-lo usado com 360 cápsulas, mesmo que aparentemente esteja funcionando, porque você pode não receber a dose correta do medicamento.

Se você utiliza um total de 4 cápsulas por dia, 360 cápsulas serão consumidas em 90 dias (aproximadamente 3 meses).

Quanto usar de Foradil®

Seu médico sempre lhe dirá por quanto tempo você deve tomar Foradil® e quanto tomar, dependendo das suas necessidades.

Para tratamento da asma, sempre será prescrito Foradil® em adição a um corticosteroide inalatório.

A dose regular de Foradil® em adultos é de 1 a 2 cápsulas duas vezes ao dia. A dose máxima recomendada por dia para adultos é 4 cápsulas. Se a sua dose diária é de 1 cápsula duas vezes ao dia, você pode usar 1 ou 2 cápsulas a mais durante o dia em adição a dosagem regular para o alívio de sintomas comuns, mas só se for necessário. Entretanto, se você precisar destas cápsulas extras mais de 2 dias por semana, você deve informar seu médico assim que possível, porque pode ser que sua condição esteja piorando. Sempre tenha com você um medicamento beta2-agonista de curta duração (um medicamento inalatório de resgate, precrito por seu médico) para tratar os sintomas repentinos da asma.

A dose recomendada para crianças com 5 anos de idade ou mais é de 1 cápsula duas vezes ao dia.

A dose máxima recomendada por dia para crianças é de 2 cápsulas.

Para prevenir crises de asma causadas por exercício, ar frio ou alergia, use 1 cápsula pelo menos 15 minutos antes do exercício ou exposição. Esta é a dose recomendada para adultos e crianças com 5 anos de idade ou mais. Em alguns casos, se você é adulto, seu médico poderá recomendar que você use 2 cápsulas para prevenir a falta de ar e o broncoespasmo. Se você estiver recebendo tratamento regular de asma, seu tratamento sempre deverá incluir um corticosteroide inalatório para o tratamento da asma.

Para doença pulmonar obstrutiva crônica, a dose regular para manutenção em adultos é de 1 a 2 cápsulas duas vezes ao dia, para serem utilizadas com o inalador, conforme descrito acima.

DÚVIDAS/COMO EVITAR DIFICULDADES

1. Como eu evito que a cápsula se quebre em pequenos fragmentos?

As cápsulas podem quebrar quando você pressiona os botões azuis (passo 5), e pequenos fragmentos podem entrar em sua boca ou garganta quando você inala. Você pode ajudar a prevenir isto por:

• Pressionando os botões azuis uma única vez.

• Mantendo as cápsulas em sua embalagem original (blíster) antes do uso.

• Armazenando as cápsulas em temperatura ambiente entre 15 e 30°C.

• Protegendo as cápsulas da umidade.

2. Os fragmentos da cápsula são prejudiciais?

Não. A cápsula é feita de gelatina comestível, a qual não é prejudicial. Qualquer fragmento de gelatina que entrar em sua boca ou garganta pode ser engolido.

3. Como faço para soltar a cápsula se ela ficar presa na câmara da cápsula?

Abra o inalador, vire-o de cabeça para baixo e bata suavemente na parte inferior.

4. O que devo fazer se os botões azuis ficarem presos?

Com cuidado, puxe os botões azuis de volta para sua posição inicial, com a ajuda das “asas”.

5. Como posso saber que eu realmente tenha tomado a dose?

• Você vai ouvir um zumbido quando você inspira através do inalador.

• Você vai ter um gosto doce na boca, de lactose. Você pode sentir pó na parte de trás de sua garganta. Isto é normal.

• A cápsula estará vazia.

6. Como faço para remover o pó do interior do inalador?

• Use um pano seco ou uma escova macia.

• Lembre-se que você nunca deve lavar o inalador.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento. Não interrompa o tratamento sem o conhecimento do seu médico.

Este medicamento não deve ser partido, aberto ou mastigado.', 'Não use Foradil® se você é alérgico (hipersensível) ao formoterol ou a qualquer outro componente da formulação listado no início desta bula.

Se isto se aplica a você, avise seu médico sem utilizar Foradil®.

Se você acha que pode ser alérgico, consulte seu médico.', 'Como todos os medicamentos, Foradil® pode causar alguns efeitos adversos em algumas pessoas.

Em alguns estudos clínicos com Foradil®, graves crises de asma foram observadas (aumento grave na falta de ar, tosse, chiado ou aperto no peito, que podem resultar em hospitalização).

Se sentir quaisquer efeitos adversos graves, pare de usar este medicamento e informe o seu médico imediatamente.

Incomuns: pode afetar 1 a cada 100 pessoas

• Broncoespasmo com chiado ou tosse e dificuldade de respirar;

Muito raro: pode afetar 1 a cada 10.000 pessoas

Reações alérgicas, como, por exemplo, se você sentir que vai desmaiar (tem pressão baixa), desenvolver uma erupção cutânea (rash), coceira ou inchaço da face;

Frequência não conhecida: a frequência não pode ser estimada através dos dados disponíveis

• Fraqueza muscular, espasmo muscular ou ritmo cardíaco anormal (este sintoma pode significar que você tem um baixo nível de potássio no sangue);

• Batimento cardíaco irregular (incluindo batimento cardíaco acelerado).

• Forte dor no peito (sintomas de angina pectoris).

Outros efeitos adversos

Outros efeitos adversos incluem os listados abaixo. Se estes efeitos adversos se agravarem, informe o seu médico, farmacêutico ou profissional de saúde.

A maioria dos efeitos adversos são leves a moderados e geralmente desaparecem após alguns dias a uma semana de tratamento.

Comuns: Podem afetar entre 1 a cada 10 pessoas:

• Dor de cabeça;

• Movimento de tremedeira involuntário (Tremor);

• Batimento cardíaco incomum ou batimento cardíaco irregular (Palpitações).

Incomuns: Podem afetar entre 1 a cada 100 pessoas:

• Agitação;

• Ansiedade;

• Nervosismo;

• Dificuldade para dormir;

• Tontura;

• Aceleração do batimento do coração;

• Irritação da garganta;

• Boca seca;

• Cãibras musculares;

• Dores musculares.

Muito raros: Podem afetar menos de 1 a cada 10.000 pessoas:

• Náusea;

• Alteração do paladar;

• Inchaço das mãos, tornozelos ou pés;

Frequência não conhecida: a frequência não pode ser estimada através dos dados disponíveis

• Sede excessiva, eliminação frequente da urina e cansaço por um longo período (uma possível indicação de alta concentração de açúcar no sangue).

• Tosse;

• Erupções cutâneas (rash);

• Dor de cabeça e tontura (possíveis sinais de pressão alta).

Se você notar qualquer outro efeito adverso não mencionado nesta bula, por favor, informe seu médico ou farmacêutico.

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também a empresa através do seu serviço de atendimento.', 'Informe seu médico ou farmacêutico se você está tomando ou tomou recentemente algum outro medicamento. Isto se aplica tanto a medicamentos de venda sob prescrição médica quanto aos de uso sem prescrição. Isto é particularmente importante caso você esteja fazendo uso de algum dos seguintes medicamentos:

• Inibidores da monoaminoxidase (inibidores da MAO) ou antidepressivos tricíclicos, os quais são medicamentos usados no tratamento da depressão e distúrbios de humor;

• Agentes simpatomiméticos, os quais são medicamentos como a adrenalina, usados no tratamento da asma e da congestão nasal;

• Anti-histamínicos, que são os medicamentos antialérgicos comuns usados na prevenção ou no tratamento dos sintomas da resposta alérgica;

• Esteroides, que são frequentemente usados para tratar a asma e outras doenças inflamatórias;

• Diuréticos, que são usados para aumentar a quantidade de urina produzida, utilizados no tratamento de edema (retenção de líquido), insuficiência cardíaca e pressão alta;

• Betabloqueadores, medicamentos usados para o tratamento de pressão alta, insuficiência cardíaca, angina, ansiedade e ritmo cardíaco anormal. Certos colírios usados para o tratamento de glaucoma podem conter betabloqueadores;

• Quinidina, disopiramida e procainamida, medicamentos utilizados no tratamento do ritmo cardíaco anormal;

• Derivados de fenotiazinas, os quais são um grupo de medicamentos que controlam desordens mentais como esquizofrenia, mania, condições psicóticas e ansiedade;

• Digitálicos, medicamentos utilizados no tratamento de insuficiência cardíaca e ritmo cardíaco anormal;

• Derivados de xantina, uma classe de medicamentos utilizados no tratamento da asma e doenças obstrutivas crônicas das vias aéreas;

• Antibióticos macrolídeos (por ex.: eritromicina, azitromicina).

• Anestésicos inalados tais como hidrocarbonos halogenados (por ex.: halotano), utilizados durante cirurgias. Informe seu médico que você utiliza Foradil® se você irá se submeter à cirurgia sob anestesia. Seu médico pode precisar alterar a dose ou interromper o uso de um dos seus medicamentos.

O seu médico pode lhe receitar outros medicamentos de uso regular pela condição de seu pulmão. Se isso ocorrer, é importante você continuar a tomá-los regularmente. Não pare o tratamento nem reduza a dose, mesmo que você se sinta melhor.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento.

Não use este medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'Siga cuidadosamente todas as instruções do seu médico ou farmacêutico. Elas podem diferir das informações contidas nesta bula.

ADVERTÊNCIAS E PRECAUÇÕES

• Se você tem alguma doença do coração;

• Se você tem pressão alta;

• Se você tem hipertireoidismo;

• Se você tem aneurisma (área onde uma artéria está inchada como uma bolsa, porque a parede da artéria é fraca);

• Se você tem distúrbio do coração, como um sinal elétrico anormal chamado “prolongamento do intervalo QT”;

• Se você tem diabetes;

• Se você tem feocromocitoma (um tumor na glândula adrenal que pode afetar a pressão sanguínea).

Informe seu médico ou farmacêutico imediatamente se sentir algum desses sintomas durante o tratamento com Foradil®.

• Se você sentir que está ficando com falta de ar ou chiado no peito enquanto usar Foradil®, você deverá continuar usando, mas consulte seu médico o quanto antes, caso você precise de outro medicamento.

INFORMAÇÃO IMPORTANTE

Não engula as cápsulas– elas devem apenas ser utilizadas para inalação do conteúdo da cápsula com o inalador Aerolizer®.

Se tiver asma: não use Foradil® como seu único medicamento para asma. Use Foradil® apenas com um corticosteroide inalatório (CI).

Quando fizer uso de Foradil®, não utilize outra medicação que contenha beta2-agonista de longa duração, como o salmeterol.

Tenha cuidado com Foradil® se você:

• Está bem controlado com um corticosteroide inalatório;

• Precisa apenas de medicamentos beta2-agonistas de curta duração de vez em quando.

Em alguns estudos clínicos com Foradil®, graves crises de asma foram observadas (vide “Quais os males que este medicamento pode me causar”).

Não inicie o tratamento com Foradil® ou aumente a dose recomendada pelo seu médico durante uma crise de asma.

O início ou aumento da dose da medicação não deverão ser utilizados para o tratamento de uma exacerbação de asma, estando somente indicados como doses adicionais ao tratamento de manutenção.

Não altere ou pare com suas medicações para controlar ou tratar seus problemas respiratórios, incluindo corticosteroides inalatórios. Seu médico ajustará seus medicamentos conforme necessário.

Se você tem asma, não use Foradil® para o alívio de chiado repentino. Sempre tenha um medicamento beta2-agonista de curta duração (um inalador de resgate prescrito pelo seu médico) para tratar os sintomas repentinos da asma.

INFORMAÇÕES IMPORTANTES SOBRE UM PRODUTO SIMILAR

Foradil® pertence à classe dos medicamentos chamados beta2-agonistas de longa duração (LABAs). Um grande estudo realizado com um LABA diferente (salmeterol) demonstrou um aumento no risco de morte por asma. Não foi realizado um estudo para saber se este efeito também se aplica ao Foradil®. Fale com seu médico sobre esse risco e os benefícios do tratamento da asma com Foradil®.

MONITORAMENTO DURANTE SEU TRATAMENTO COM FORADIL®

O tratamento com Foradil® pode levar a um aumento dos níveis de açúcar no sangue. Portanto, pode ser necessário monitorar seus níveis de açúcar no sangue, se você for diabético.

O tratamento com Foradil® pode levar a redução do nível de potássio no sangue, tornando-o mais susceptível a anormalidades no ritmo cardíaco. Portanto, seu médico deve monitorar o nível de potássio sanguíneo, especialmente se você tem asma grave.

Se você tiver alguma dúvida sobre como Foradil® funciona ou por que este medicamento foi prescrito a você, converse com seu médico.

POPULAÇÕES ESPECIAIS

Idosos (65 anos ou mais): Se você tem 65 anos de idade ou mais, você pode usar Foradil® na mesma dose de outros adultos.

Crianças e adolescentes (5 anos ou mais): Foradil® está indicado para crianças com 5 anos ou mais. Crianças somente devem usar Foradil® se conseguirem utilizar o inalador corretamente (vide “Como usar as cápsulas com o inalador”) e com a ajuda de um adulto.

GRAVIDEZ E LACTAÇÃO

Informe seu médico se você está grávida ou se pretende engravidar.

Você não deve usar Foradil® durante a gravidez, a menos que seu médico o indique. Ele informará sobre o potencial risco de se administrar este medicamento durante a gravidez.

Se você está amamentando, consulte o seu médico antes de usar Foradil®.

Converse com seu médico antes de tomar qualquer medicamento.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.

EFEITOS NA HABILIDADE DE DIRIGIR E OPERAR MÁQUINAS

Em alguns pacientes, Foradil® pode causar tontura. Caso isto ocorra, você não deve dirigir, operar máquinas ou realizar qualquer atividade que requeira atenção.

INFORMAÇÕES IMPORTANTES SOBRE UM DOS COMPONENTES DO MEDICAMENTO

Foradil® contém LACTOSE (açúcar do leite). Se você tiver intolerância grave à lactose, avise seu médico antes de usar Foradil®.

Este medicamento pode causar doping.

INTERAÇÕES MEDICAMENTOSAS

Informe seu médico ou farmacêutico se você está tomando ou tomou recentemente algum outro medicamento. Isto se aplica tanto a medicamentos de venda sob prescrição médica quanto aos de uso sem prescrição. Isto é particularmente importante caso você esteja fazendo uso de algum dos seguintes medicamentos:

• Inibidores da monoaminoxidase (inibidores da MAO) ou antidepressivos tricíclicos, os quais são medicamentos usados no tratamento da depressão e distúrbios de humor;

• Agentes simpatomiméticos, os quais são medicamentos como a adrenalina, usados no tratamento da asma e da congestão nasal;

• Anti-histamínicos, que são os medicamentos antialérgicos comuns usados na prevenção ou no tratamento dos sintomas da resposta alérgica;

• Esteroides, que são frequentemente usados para tratar a asma e outras doenças inflamatórias;

• Diuréticos, que são usados para aumentar a quantidade de urina produzida, utilizados no tratamento de edema (retenção de líquido), insuficiência cardíaca e pressão alta;

• Betabloqueadores, medicamentos usados para o tratamento de pressão alta, insuficiência cardíaca, angina, ansiedade e ritmo cardíaco anormal. Certos colírios usados para o tratamento de glaucoma podem conter betabloqueadores;

• Quinidina, disopiramida e procainamida, medicamentos utilizados no tratamento do ritmo cardíaco anormal;

• Derivados de fenotiazinas, os quais são um grupo de medicamentos que controlam desordens mentais como esquizofrenia, mania, condições psicóticas e ansiedade;

• Digitálicos, medicamentos utilizados no tratamento de insuficiência cardíaca e ritmo cardíaco anormal;

• Derivados de xantina, uma classe de medicamentos utilizados no tratamento da asma e doenças obstrutivas crônicas das vias aéreas;

• Antibióticos macrolídeos (por ex.: eritromicina, azitromicina).

• Anestésicos inalados tais como hidrocarbonos halogenados (por ex.: halotano), utilizados durante cirurgias. Informe seu médico que você utiliza Foradil® se você irá se submeter à cirurgia sob anestesia. Seu médico pode precisar alterar a dose ou interromper o uso de um dos seus medicamentos.

O seu médico pode lhe receitar outros medicamentos de uso regular pela condição de seu pulmão. Se isso ocorrer, é importante você continuar a tomá-los regularmente. Não pare o tratamento nem reduza a dose, mesmo que você se sinta melhor.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento.

Não use este medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/foradil', 'Vermelha', '2026-11-22T23:23:56.491Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/foradil');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Brometo de Tiotrópio', 'Brometo de Tiotrópio', 'Spiriva Respimat atua dilatando os brônquios (broncodilatador), facilitando assim a passagem de ar pelos pulmões. O efeito se inicia em até 30 minutos após a inalação e dura 24 horas.', 'Spiriva Respimat é indicado para o tratamento de manutenção de pacientes com Doença Pulmonar Obstrutiva Crônica (DPOC) que, caracterizada por tosse, catarro e falta de ar, ocorre principalmente em decorrência da inalação da fumaça de cigarro, incluindo também bronquite crônica e enfisema pulmonar (irritação respiratória crônica), associada à dispneia (falta de ar), melhora da qualidade de vida e redução dos episódios de exacerbação (crises de agravamento) da doença.

Spiriva Respimat é indicado também como tratamento adicional de manutenção para melhora dos sintomas da asma (cansaço, falta de ar, chiado no peito e tosse), qualidade de vida e redução das exacerbações em pacientes a partir de 6 anos de idade com asma moderada tratados com pelo menos corticosteroides inalatórios ou asma grave tratados com corticosteroides inalatórios e agonistas beta-adrenérgicos de longa duração que permaneçam sintomáticos.', 'Vide instruções de uso no final da bula.

POSOLOGIA

1 dose liberada (puff) = conteúdo expelido após apertar 1 vez o botão do inalador Respimat.

Dose terapêutica diária = 5 mcg, ou seja, 2 doses liberadas (2 puffs consecutivos).

DOENÇA PULMONAR OBSTRUTIVA CRÔNICA (DPOC)

População adulta:

A dose terapêutica diária recomendada de Spiriva Respimat é a inalação de 5 mcg, ou seja, 2 doses liberadas (2 puffs) consecutivas uma vez ao dia pelo inalador Respimat, no mesmo horário do dia.

*DPOC não ocorre normalmente em crianças.

ASMA

População adulta e pediátrica a partir de 6 anos de idade:

A dose terapêutica diária recomendada de Spiriva Respimat é a inalação de 5 mcg, ou seja, 2 doses liberadas (2 puffs) consecutivas, uma vez ao dia pelo inalador Respimat, no mesmo horário do dia. No tratamento da asma, os benefícios aparecem após diversas doses de Spiriva Respimat.

*a eficácia e segurança de Spiriva Respimat em crianças com idade inferior a 6 anos de idade com asma não foram estabelecidas.

POPULAÇÃO ESPECIAL:

Pacientes idosos ou com alterações da função do fígado podem utilizar Spiriva Respimat na dose recomendada.

Pacientes com comprometimento moderado a grave da função dos rins podem utilizar Spiriva Respimat na dose recomendada, mas devem ser acompanhados de perto.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e duração do tratamento. Não interrompa o tratamento sem o conhecimento do seu médico.', 'Você não deve usar Spiriva Respimat se tiver alergia à atropina ou seus derivados (como ipratrópio ou oxitrópio) ou a qualquer um dos componentes da fórmula.', 'Reações incomuns: tontura, insônia, palpitações, tosse, faringite, disfonia (rouquidão), broncoespasmo (estreitamento dos brônquios), boca seca, candidíase orofaríngea (sapinho), erupção cutânea (rash).

Reações raras: epistaxe (sangramento nasal), constipação, gengivite (inflamação da gengiva), estomatite, prurido (coceira), edema angioneurótico (alergia com inchaço da língua, lábios e garganta), urticária, hipersensibilidade (inclusive reações imediatas), infecção do trato urinário.

Reações com frequência desconhecida: desidratação, glaucoma, aumento da pressão intraocular, visão borrada, alterações nos batimentos do coração (como fibrilação atrial, taquicardia supraventricular, taquicardia), laringite (inflamação da laringe), sinusite, disfagia (dificuldade para engolir), refluxo gastroesofágico (doença do refluxo), glossite (inflamação da língua), obstrução intestinal inclusive íleo paralítico (obstrução do intestino interrompendo a passagem das fezes), infecção e úlcera cutânea (na pele), pele seca, inchaço das articulações, retenção urinária (geralmente em homens com fatores de predisposição), disúria (dificuldade e dor para urinar).

A frequência, tipo e gravidade das reações adversas na população pediátrica são similares às de adultos.

Atenção: este produto é um medicamento que possui uma nova indicação terapêutica no país e, embora as pesquisas tenham indicado eficácia e segurança aceitáveis, mesmo que indicado e utilizado corretamente, podem ocorrer eventos adversos imprevisíveis ou desconhecidos. Nesse caso, informe seu médico.', 'A administração crônica de outros medicamentos anticolinérgicos (como atropina, biperideno, ipratrópio, glicopirrônio, umeclidínio) com Spiriva Respimat não é recomendada.

Corticosteroides inalatórios (como budesonida, fluticasona, mometasona) e suas combinações com LABA (por exemplo, formoterol, salmeterol); as quais são medicações concomitantes comumente usadas por pacientes com DPOC, não demonstraram alterar a exposição ao tiotrópio.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem conhecimento do seu médico. Pode ser perigoso para sua saúde.', 'Spiriva Respimat é um broncodilatador para tratamento crônico, com dose única diária, e não se destina ao tratamento inicial de casos agudos de estreitamento dos brônquios (episódios de falta de ar) ou para o alívio de sintomas agudos. No caso de um ataque agudo, um beta-2-agonista de ação rápida (como fenoterol ou salbutamol) deve ser usado sob orientação médica. Caso não observe melhora do ataque agudo ou em caso de dúvidas, procure um médico ou serviço de emergência imediatamente.

Spiriva Respimat não deve ser usado como primeira linha para o tratamento da asma.

Pacientes asmáticos devem ser orientados a continuar com o tratamento usual, por exemplo, corticosteroides inalatórios sem necessidade de alterá-lo após a introdução de Spiriva Respimat, mesmo após os sintomas da asma terem melhorado.

Após administração de Spiriva Respimat, podem ocorrer reações alérgicas imediatas.

Spiriva Respimat deve ser usado com cuidado se você tiver glaucoma de ângulo fechado (aumento da pressão dentro do olho), aumento da próstata ou obstrução do colo da bexiga. Caso você apresente uma destas condições, avise o seu médico.

Pode ocorrer broncoespasmo (estreitamento dos brônquios) induzido pela inalação.

Spiriva Respimat é eliminado predominantemente pelos rins. Se você tiver alguma doença que afete a função dos rins, é necessário o acompanhamento cuidadoso do médico. Portanto, informe seu médico se tiver algum problema nos rins.

Evite contato do medicamento com os olhos. Dor nos olhos, visão embaçada, visão de halos ou imagens coloridas com olhos avermelhados podem ser sinais de glaucoma. Nestes casos, procure um oftalmologista imediatamente. Colírios para contrair a pupila não são considerados um tratamento eficaz.

A dose recomendada de Spiriva Respimat é 2 doses liberadas (2 puffs) consecutivas, uma vez ao dia. Você não deve usar Spiriva Respimat mais de uma vez ao dia. O frasco de Spiriva só deve ser utilizado com o inalador Respimat que acompanha o produto.

A ocorrência de tontura ou visão embaçada pode prejudicar a habilidade de dirigir e operar máquinas.

FERTILIDADE, GRAVIDEZ E AMAMENTAÇÃO

O uso de Spiriva Respimat não é recomendado durante a gravidez e amamentação. Existem dados limitados do uso deste medicamento durante a gravidez. Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.

INTERAÇÕES MEDICAMENTOSAS

A administração crônica de outros medicamentos anticolinérgicos (como atropina, biperideno, ipratrópio, glicopirrônio, umeclidínio) com Spiriva Respimat não é recomendada.

Corticosteroides inalatórios (como budesonida, fluticasona, mometasona) e suas combinações com LABA (por exemplo, formoterol, salmeterol); as quais são medicações concomitantes comumente usadas por pacientes com DPOC, não demonstraram alterar a exposição ao tiotrópio.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem conhecimento do seu médico. Pode ser perigoso para sua saúde.', 'https://www.bulas.med.br/bula/spiriva-respimat', 'Vermelha', '2026-11-22T23:23:56.493Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/spiriva-respimat');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Propionato de Fluticasona', 'Propionato de Fluticasona', 'Flixotide Spray contém uma substância chamada propionato de fluticasona, que nas doses recomendadas tem potente ação anti-inflamatória, ou seja, age contra a inflamação dos pulmões. Isso resulta na redução dos sintomas e das crises de asma, na diminuição dos sintomas de DPOC e no aumento da função pulmonar. Esses benefícios proporcionam melhora significativa da sua qualidade de vida.', 'Flixotide Spray é indicado na prevenção da asma em adultos e crianças e no tratamento dos sintomas da doença pulmonar obstrutiva crônica (DPOC) quando utilizado em combinação com broncodilatadores de longa duração.', 'MODO DE USAR

Antes de usar Flixotide Spray, leia atentamente as instruções abaixo.

Teste do inalador

Antes de utilizá-lo pela primeira vez ou caso você não use o inalador há uma semana ou mais, remova o protetor do bocal, apertando delicadamente suas laterais. Agite bem o inalador e libere um jato de ar para verificar se o aparelho funciona bem.

Uso do inalador

• Remova a tampa do bocal, apertando suavemente suas laterais. Verifique se há partículas estranhas no interior e no exterior do inalador, inclusive no bocal.

Agite bem o inalador para garantir a remoção de qualquer partícula estranha e para misturar seu conteúdo de maneira uniforme.

• Segure o inalador na posição vertical, entre o indicador e o polegar, e mantenha o polegar na base, abaixo do bocal. Expire (sopre o ar pela boca) lentamente até expelir todo o ar dos pulmões.

• Coloque o bocal do inalador entre os lábios (ou no espaçador, conforme instrução do seu médico) e ajuste-o bem, sem mordê-lo.

Em seguida, comece a inspirar (puxar o ar para dentro dos pulmões) pela boca e pressione firmemente o inalador entre o indicador e o polegar para liberar o aerossol. Inspire regular e profundamente.

• Prenda a respiração enquanto retira o inalador da boca. Continue a prender a respiração por tanto tempo quanto for confortável para você.

• Para liberar o segundo jato, mantenha o inalador na posição vertical e espere cerca de meio minuto (30 segundos) antes de repetir os passos 2 a 4. Lavar a boca com água e não engolir. Após o uso, recoloque a tampa do bocal, empurrando-a firmemente e prendendo-a na posição correta.

IMPORTANTE

Não apresse a execução dos passos 3 e 4. É importante que você comece a inspirar o mais lentamente possível um pouco antes de acionar o inalador. Pratique em frente ao espelho nas primeiras vezes. Se perceber que uma névoa sai do topo do inalador ou dos cantos de sua boca, você deve começar novamente a partir do passo 2.

Se seu médico lhe deu instruções diferentes de uso do inalador (se, por exemplo, recomendou a utilização de espaçadores), siga cuidadosamente essas instruções. Informe seu médico sobre qualquer dificuldade que você sentir.

INSTRUÇÕES DE LIMPEZA

• Você deve limpar o inalador pelo menos uma vez por semana.

• Remova a lata de metal do dispositivo de plástico e retire a proteção do bocal.

• Enxágue o dispositivo de plástico e o protetor do bocal com água morna.

• Deixe secar, evitando aquecimento excessivo.

• Recoloque a lata e o protetor do bocal no dispositivo de plástico.

Não coloque a lata de metal na água.

Flixotide Spray só deve ser usado por inalação oral (pela boca).

Pode demorar alguns dias até meses para que o medicamento faça efeito. É muito importante que você tome o medicamento regularmente. Não interrompa o tratamento mesmo que esteja melhor, a menos que seu médico recomende.

Não use este medicamento para tratar um ataque repentino de falta de ar, porque isso não vai ajudá-lo. Você vai precisar de outro tipo de tratamento. Caso tome mais de um medicamento, tenha cuidado para não confundir as formas de uso.

Avise seu médico caso diminua o alívio proporcionado pelos outros medicamentos que você toma para tratar a asma ou se for necessário usar um número de inalações maior do que o indicado na receita.

Seu médico deverá recomendar a administração de pelo menos duas inalações por dose.

Se você sentir dificuldade de usar o inalador, seu médico pode indicar o auxílio de um espaçador para facilitar a administração de Flixotide Spray.

POSOLOGIA

Na asma

• Adultos e adolescentes maiores de 16 anos: 100 a 1.000 mcg duas vezes ao dia.

• Crianças e adolescentes de 4 a 16 anos: 50 a 200 mcg duas vezes ao dia.

• Crianças de 1 a 4 anos: 100 mcg duas vezes ao dia.

Na doença pulmonar obstrutiva crônica (DPOC): A dose para adultos é de 500 mcg duas vezes ao dia em tratamento combinado com um broncodilatador de longa duração, como por exemplo o salmeterol (Nota: apenas o inalador de 250 microgramas é adequado para o tratamento da DPOC).

PACIENTES ESPECIAIS

Não há necessidade de ajuste de dose para pacientes idosos nem para os que têm alguma doença no fígado ou nos rins.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento. Não interrompa o tratamento sem o conhecimento de seu médico.', 'Flixotide Spray é contraindicado a pacientes com alergia ao propionato de fluticasona ou a qualquer outro componente da formulação (ver o item Composição).', 'Reações muito comuns (ocorrem em mais de 10% dos pacientes que utilizam este medicamento):

• candidíase da boca e da garganta (feridas branco-amareladas, manchas em relevo, causadas por fungo). Você pode evitar o aparecimento dessas lesões lavando a boca com água após o uso do produto, ou seu médico pode prescrever uma medicação antifúngica para tratá-las, para que você não precise parar de usar Flixotide Spray.

Reações comuns (ocorrem entre 1% e 10% dos pacientes que utilizam este medicamento):

• pneumonia (em pacientes com DPOC);

• rouquidão (em alguns pacientes, o propionato de fluticasona inalatório pode causar rouquidão, controlada com a lavagem da boca com água imediatamente após a inalação);

• manchas roxas na pele.

Reações incomuns (ocorrem entre 0,1% e 1% dos pacientes que utilizam este medicamento):

• reações alérgicas na pele.

Reações raras (ocorrem entre 0,01% e 0,1% dos pacientes que utilizam este medicamento ):

• candidíase no esôfago (infecção causada por fungo).

Reações muito raras (ocorrem em menos de 0,01% dos pacientes que utilizam este medicamento ):

• inchaço da face e da garganta (angioedema);

• dificuldades para respirar (dispneia e/ou broncoespasmo);

• reações alérgicas graves (reações anafiláticas);

• arredondamento da face (síndrome de Cushing);

• supressão dos hormônios produzidos pela glândula adrenal;

• desaceleração do crescimento (em crianças);

• enfraquecimento dos ossos (redução da densidade mineral óssea);

• problemas nos olhos, como catarata e glaucoma;

• aumento dos níveis de glicose (açúcar) do sangue (se você tem diabetes, seu médico pode indicar exames mais frequentes para medir os níveis de açúcar do seu sangue e ajustar a dose do tratamento do diabetes);

• ansiedade;

• distúrbios do sono;

• mudanças de comportamento, inclusive hiperatividade e irritabilidade (principalmente em crianças);

• broncoespasmo paradoxal (aparecimento ou piora do chiado no peito logo após a utilização do seu inalador).

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também à empresa através do seu serviço de atendimento.', 'Em certos casos, não é adequado usar Flixotide Spray com outros medicamentos como eritromicina, cetoconazol e os medicamentos utilizados no tratamento da AIDS.

Você não deve usar Flixotide Spray se estiver tomando ritonavir, medicamento para tratamento da AIDS, sem o conhecimento de seu médico.

Verifique se seu médico tem conhecimento de outros medicamentos que você esteja tomando.

Informe seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento de seu médico. Pode ser perigoso para sua saúde.', 'Se você responder SIM a alguma das perguntas seguintes, converse com seu médico antes de usar Flixotide Spray.

• Você está grávida ou pretende engravidar em breve?

• Você está amamentando?

• Já lhe disseram que você é alérgico a Flixotide Spray, ao propionato de fluticasona ou qualquer outro ingrediente da formulação?

• Você já teve aftas na boca?

• Você está (ou já esteve) em tratamento para tuberculose?

O médico deverá avaliar regularmente a altura de crianças que estejam em tratamento de longa duração com medicamentos como Flixotide Spray.

Se sua visão ficar embaçada, se você apresentar dificuldade para enxergar ou qualquer outra alteração visual, informe seu médico.

EFEITOS NA HABILIDADE DE DIRIGIR E OPERAR MÁQUINAS

É improvável que Flixotide Spray provoque algum efeito sobre a capacidade de dirigir veículos ou de operar máquinas.

GRAVIDEZ E LACTAÇÃO

Deve-se considerar a administração de propionato de fluticasona durante a gravidez somente se o benefício esperado para a mãe for maior do que qualquer possibilidade de risco para o feto. A administração durante a lactação deve ser considerada somente se o benefício esperado para a mãe justificar o possível risco para a criança.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.

INTERAÇÕES MEDICAMENTOSAS

Em certos casos, não é adequado usar Flixotide Spray com outros medicamentos como eritromicina, cetoconazol e os medicamentos utilizados no tratamento da AIDS.

Você não deve usar Flixotide Spray se estiver tomando ritonavir, medicamento para tratamento da AIDS, sem o conhecimento de seu médico.

Verifique se seu médico tem conhecimento de outros medicamentos que você esteja tomando.

Informe seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento de seu médico. Pode ser perigoso para sua saúde.', 'https://www.bulas.med.br/bula/flixotide-spray', 'Vermelha', '2026-11-22T23:23:57.457Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/flixotide-spray');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Esomeprazol Magnésico', 'Esomeprazol Magnésico', 'O esomeprazol magnésico tri-hidratado reduz a produção de ácido no seu estômago, através de um mecanismo de ação específico de inibição da bomba de prótons. Após a dose oral, o início do efeito ocorre dentro de uma hora.', 'O esomeprazol magnésico tri-hidratado é indicado para o tratamento de doenças ácido-pépticas e alívio dos sintomas de azia, regurgitação ácida e dor epigástrica.

• Doença do refluxo gastroesofágico (refluxo do estômago para o esôfago) (DRGE):

• Tratamento da esofagite (inflamação do esôfago) de refluxo erosiva.

• Tratamento de manutenção para prevenir a recidiva de esofagite.

• Tratamento dos sintomas da DRGE, tais como: pirose/azia (queimação retroesternal), regurgitação ácida e dor epigástrica.

• Pacientes que precisam de terapia contínua com anti-inflamatórios não-esteroidais (AINE):

• Tratamento dos sintomas gastrointestinais altos associados à terapia com ao tratamento com anti- inflamatórios.

• Cicatrização de úlceras gástricas associadas ao tratamento com anti-inflamatórios, incluindo aqueles anti-inflamatórios mais novos, da classe “COX-2 seletivos”.

• Prevenção de úlceras gástricas e duodenais associadas ao tratamento com anti-inflamatórios, incluindo COX-2 seletivos, em pacientes com algum risco adicional.

• Tratamento da úlcera duodenal associada à bactéria Helicobacter pylori.

• Erradicação da bactéria Helicobacter pylori em associação com um tratamento antibacteriano adequado.

• Condições patológicas hipersecretoras incluindo síndrome de Zollinger-Ellison (excesso de produção de ácido clorídrico) e hipersecreção idiopática.', 'Os comprimidos de esomeprazol magnésico tri-hidratado devem ser administrados inteiros por via oral, com líquido.

O esomeprazol magnésico tri-hidratado pode ser administrado com ou sem alimentos.

Este medicamento não pode ser partido ou mastigado.

POSOLOGIA

ADULTOS

Doença do Refluxo Gastroesofágico (DRGE):

• Tratamento da esofagite de refluxo erosiva: 40 mg uma vez ao dia por 4 semanas. Um tratamento adicional de 4 semanas é recomendado para pacientes com esofagite não cicatrizada ou que apresentam sintomas persistentes.

• Tratamento de manutenção para prevenir a recidiva em pacientes com esofagite: 20 mg uma vez ao dia.

• Tratamento dos sintomas da DRGE, tais como, pirose/azia (queimação retroesternal), regurgitação ácida e dor epigástrica: 20 mg uma vez ao dia para os pacientes que não apresentam esofagite. Se o controle dos sintomas não for obtido após 4 semanas, o paciente deve ser investigado. Uma vez resolvidos os sintomas da DRGE, o controle dos sintomas pode ser obtido usando-se esomeprazol magnésico tri-hidratado na dose de 20mg/dia, quando necessário.

Em pacientes de risco tratados com anti-inflamatórios, o controle dos sintomas utilizando-se um tratamento sob demanda, não é recomendado.

Pacientes que precisam de terapia contínua com anti-inflamatórios:

• Tratamento dos sintomas gastrointestinais altos associados ao tratamento com anti-inflamatórios: 20 mg uma vez ao dia em pacientes que continuam precisando tomar anti-inflamatórios. Se os sintomas não forem controlados após 4 semanas, o paciente deve ser investigado.

• Cicatrização de úlceras gástricas associadas ao tratamento com anti-inflamatórios: a dose usual é de 20 mg uma vez ao dia por 4 a 8 semanas. Alguns pacientes podem precisar da dose de 40 mg, uma vez ao dia, por 4 a 8 semanas.

• Prevenção de úlceras gástricas e duodenais associadas ao tratamento com anti-inflamatórios em pacientes de risco: 20 mg uma vez ao dia.

Tratamento da úlcera duodenal associada ao Helicobacter pylori/erradicação do Helicobacter pylori: 20 mg de esomeprazol magnésico tri-hidratado com 1 g de amoxicilina e 500 mg de claritromicina, todos duas vezes ao dia, por 7 dias. Não há necessidade da continuidade do tratamento com fármacos antissecretores para a cicatrização e resolução dos sintomas de úlcera.

Condições patológicas hipersecretoras incluindo síndrome de Zollinger-Ellison e hipersecreção idiopática: a dose inicial recomendada é de 40 mg de esomeprazol magnésico tri-hidratado duas vezes ao dia. O ajuste de dose deve ser individualizado e o tratamento continuado pelo tempo clinicamente indicado. Doses de até 120 mg foram administradas duas vezes ao dia.

Manutenção da hemostasia e prevenção de ressangramento de úlceras gástricas e duodenais após tratamento com esomeprazol sódico iv: 40 mg uma vez ao dia por 4 semanas. O período do tratamento oral deve ser precedido por terapia de supressão ácida com esomeprazol sódico iv 80 mg administrado por infusão em bolus por 30 minutos, seguido por uma infusão intravenosa contínua de 8 mg/h administrada durante 3 dias.

CRIANÇAS 12-18 ANOS

Doença do refluxo gastroesofágico (DRGE):

• Tratamento da esofagite de refluxo erosiva: 40 mg uma vez ao dia por 4 semanas. Um tratamento adicional de 4 semanas é recomendado para os pacientes com esofagite não cicatrizada ou aqueles que apresentam sintomas persistentes.

• Tratamento dos sintomas da DRGE: 20 mg uma vez ao dia para os pacientes que não apresentam esofagite. Se o controle dos sintomas não for obtido após 4 semanas, o paciente deve ser investigado. Uma vez resolvidos os sintomas da DRGE, o esomeprazol magnésico tri-hidratado pode ser usado na dose de 20 mg/dia e sob supervisão médica.

• O tratamento com esomeprazol magnésico tri-hidratado para crianças (12 – 18 anos) deve ser limitado a 8 semanas.

Crianças: esomeprazol magnésico tri-hidratado não deve ser usado em crianças menores de 12 anos, pois não há dados disponíveis.

Insuficiência renal: não é necessário ajuste de dose para os pacientes com insuficiência renal. Devido à experiência limitada em pacientes com insuficiência renal grave, esses pacientes devem ser tratados com precaução.

Insuficiência hepática: não é necessário ajuste de dose para os pacientes com insuficiência hepática de leve a moderada. Para os pacientes com insuficiência hepática grave, uma dose máxima diária de 20 mg de esomeprazol magnésico tri-hidratado não deve ser excedida.

Idosos: não é necessário ajuste de dose para idosos.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento.

Não interrompa o tratamento sem o conhecimento do seu médico.', 'Você não deve utilizar o esomeprazol magnésico tri-hidratado se tiver alergia ao esomeprazol, a outros benzimidazóis (medicamentos anti-helmínticos benzimidazólicos – medicamentos para tratar infestação por parasitas) ou a qualquer um dos componentes da fórmula.', 'Reação comum (ocorre entre 1% e 10% dos pacientes que utilizam este medicamento): dor de cabeça, dor na barriga, diarreia, gases, enjoo, vômito e prisão de ventre.

Reação incomum (ocorre entre 0,1% e 1% dos pacientes que utilizam este medicamento): inchaço periférico, dificuldade para dormir, tontura, sensação de queimação/dormência na pele, sonolência, vertigem, boca seca, aumento da quantidade das enzimas do fígado (este efeito só pode ser visto quando um exame de sangue é realizado) e reações na pele (dermatite, coceira, urticária e erupções na pele).

Reação rara (ocorre entre 0,01% e 0,1% dos pacientes que utilizam este medicamento): diminuição dos glóbulos brancos do sangue (leucopenia), diminuição das células de coagulação no sangue (trombocitopenia), reações de hipersensibilidade (alergia) ao medicamento (inchaço, reação/choque anafilático), diminuição de sódio no sangue (hiponatremia), agitação, confusão, depressão, desordens do paladar, visão turva, broncoespasmo, inflamação na mucosa da boca (estomatite), infecção gastrointestinal fúngica (candidíase gastrointestinal), inflamação do fígado (hepatite) com ou sem icterícia (presença de coloração amarela na pele e nos olhos), queda de cabelo, sensibilidade da pele à luz (fotossensibilidade), dores nas articulações, dor muscular, mal-estar, aumento da transpiração e febre.

Reação muito rara (ocorre em menos de 0,01% dos pacientes que utilizam este medicamento): ausência ou número insuficiente de glóbulos brancos granulócitos no sangue (agranulocitose), diminuição de células do sangue (pancitopenia), agressividade, alucinações, comprometimento da função do fígado, encefalopatia hepática, desordens graves na pele (eritema multiforme, síndrome de Stevens-Johnson e necrólise epidérmica tóxica), fraqueza muscular, inflamação dos rins, aumento das mamas em homens, diminuição de magnésio no sangue (hipomagnesemia), hipomagnesemia grave pode resultar em redução de cálcio no sangue (hipocalcemia), a hipomagnesemia também pode causar hipocalemia (baixa concentração de potássio no sangue) e inflamação intestinal (colite microscópica).

Atenção: este produto é um medicamento que possui uma nova indicação no país e, embora as pesquisas tenham indicado eficácia e segurança aceitáveis, mesmo que indicado e utilizado corretamente, podem ocorrer eventos adversos imprevisíveis ou desconhecidos. Neste caso, informe seu médico.', NULL, 'Informe ao seu médico se durante o tratamento com esomeprazol magnésico tri-hidratado você apresentar perda de peso sem dieta, vômitos, dificuldade para engolir alimentos, evacuar sangue vivo ou fezes escuras (tipo borra de café), e se houver suspeita ou presença de úlcera, pois o tratamento com esomeprazol magnésico tri-hidratado pode aliviar esses sintomas e retardar o diagnóstico.

Informe também se estiver fazendo uso de algum medicamento antirretroviral (contra um tipo específico de vírus), como o atazanavir e o nelfinavir.

Se você tem problemas hereditários raros de intolerância à frutose, má absorção de glicose-galactose ou insuficiência de sacarase-isomaltase, você não deve tomar este medicamento.

O uso concomitante de esomeprazol e clopidogrel deve ser evitado.

Alguns estudos sugerem que o tratamento com medicamentos da classe de esomeprazol pode estar associado a um pequeno aumento do risco de fraturas relacionadas com a osteoporose (doença que reduz a densidade e a massa dos ossos). No entanto, em outros estudos semelhantes, nenhum aumento do risco foi evidenciado.

Aconselha-se que os pacientes de risco para o desenvolvimento da osteoporose ou fraturas relacionadas à osteoporose tenham um acompanhamento médico adequado.

O esomeprazol magnésico tri-hidratado deve ser utilizado com cuidado em pacientes com problemas graves no fígado ou nos rins.

Não se espera que o esomeprazol magnésico tri-hidratado afete a capacidade de dirigir veículos e operar máquinas.

A experiência clínica em crianças abaixo de 12 anos de idade é limitada. Deverá ser utilizado nesta faixa etária somente a critério médico.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião- dentista.

Como não há dados disponíveis quanto à excreção de esomeprazol no leite materno, não deve ser usado durante a amamentação.

Você deve utilizar o esomeprazol magnésico tri-hidratado com cuidado se estiver tomando os seguintes medicamentos: para o tratamento de infecções por fungos (cetoconazol, itraconazol e erlotinibe), digoxina, ansiedade (diazepam), epilepsia (fenitoína), coagulação do sangue (varfarina ou clopidogrel), acelerar a motilidade do estômago (cisaprida), metotrexato, tratamento da AIDS (atazanavir, nelfinavir e saquinavir), tratamentos com amoxicilina, quinidina, naproxeno, rofecoxibe, claritomicina, voriconazol, rifampicina e erva de São João (Hypericum perforatum), pois estes medicamentos podem ter seu efeito alterado pelo uso concomitante de esomeprazol magnésico tri-hidratado.

A administração concomitante de esomeprazol tem sido relacionada ao aumento da concentração de tacrolimo no sangue.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/esomeprazol-magnesio-comprimidos-20-mg-e-40-mg', 'Vermelha', '2026-11-22T23:23:57.460Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/esomeprazol-magnesio-comprimidos-20-mg-e-40-mg');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Lactulose', 'Lactulose', 'A ação esperada da LACTULONA® é a de restabelecer a função regular do intestino, pois intensifica o acúmulo de água no bolo fecal. Os primeiros efeitos serão obtidos após a sua utilização por alguns dias seguidos (até quatro dias), especialmente em quem está substituindo um laxante pela LACTULONA®. Seu efeito pode ser desejável também em pessoas com mau funcionamento do fígado, numa condição específica chamada “encefalopatia hepática”, melhorando seu nível de consciência.', 'LACTULONA® está indicada para tratar os sintomas da constipação intestinal (prisão de ventre) e para a prevenção e o tratamento de encefalopatia hepática, incluindo as etapas de pré-coma e coma hepático.', 'LACTULONA® pode ser administrada preferencialmente em uma única tomada pela manhã ou à noite, sozinha ou com alimentos, ou ainda misturada a suco de fruta, leite, iogurte, água ou qualquer líquido, ou conforme orientação médica.

POSOLOGIA

CONSTIPAÇÃO INTESTINAL CRÔNICA

Lactentes: 5 ml/dia

Crianças de 1 a 5 anos: 5 a 10 ml/dia

Crianças de 6 a 12 anos: 10 a 15 ml/dia

Acima de 12 anos e adultos: 15 a 30 ml/dia

A posologia pode ser ajustada para que se obtenham duas ou três evacuações por dia conforme orientação médica.

ENCEFALOPATIA HEPÁTICA, PRÉ-COMA E COMA HEPÁTICO

Iniciar com 60 ml ao dia, podendo chegar, em casos graves, a 150 ml ao dia.

Siga corretamente o modo de usar. Em caso de dúvidas sobre este medicamento, procure orientação do farmacêutico. Não desaparecendo os sintomas, procure orientação de seu médico ou cirurgião-dentista.', 'LACTULONA® é contraindicada em:

• Pessoas alérgicas ou sensíveis a qualquer um dos componentes da fórmula;

• Casos de intolerância a açúcares como lactose, galactose e frutose;

• Casos de gastrite, úlceras pépticas, apendicite, sangramento ou obstrução intestinal, diverticulite etc.;

• Preparo intestinal de pessoas que serão submetidas a exames proctológicos (colonoscopia, retosigmoidoscopia) com o uso de eletrocautério.', 'O uso de LACTULONA® em diabéticos pode alterar o controle da glicemia (açúcar), pois pode haver discreta absorção dos açúcares que a compõem. Além disso, o uso de LACTULONA® por períodos prolongados pode causar alteração dos sais do sangue, especialmente em idosos.

O uso de doses altas de LACTULONA® em encefalopatia hepática pode causar aumento dos gases intestinais, flatulência, ruídos, arrotos, aumento da sede, normalmente transitórios. Náuseas e vômito têm sido relatados com pouca frequência.

Caso ocorra diarreia em resposta à LACTULONA®, você deve consultar o seu médico para reavaliar o uso de LACTULONA®.

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também à empresa através do seu serviço de atendimento.', NULL, 'ADVERTÊNCIAS E PRECAUÇÕES

HIPERTENSÃO

Pessoas com pressão alta e com desidratação devem ter cuidado na administração de LACTULONA®, pois tais condições podem ser agravadas com o uso desse medicamento.

DIABETES

Além da lactulose, o medicamento contém pequenas quantidades de galactose e lactose. Isso deve ser levado em consideração especialmente ao se administrar a LACTULONA® a diabéticos, pelo fato de haver relatos de casos em que houve o aumento nos níveis sanguíneos de glicose (açúcar) com a administração do xarope. Os diabéticos devem consultar o médico-assistente para reavaliar o controle da glicemia.

Contém açúcar.

ENCEFALOPATIA HEPÁTICA

Esses pacientes devem estar sob cuidados médicos constantes, pelo risco de acidose quando a LACTULONA® for usada em altas doses.

CRIANÇAS E IDOSOS

O uso de LACTULONA® em crianças somente deve ser realizado com orientação médica, após a conclusão de que os sintomas intestinais não são decorrentes de outras doenças. Pacientes idosos debilitados em tratamento contínuo com LACTULONA® devem consultar periodicamente um médico para a avaliação dos sais do sangue (sódio, potássio, cloro e bicarbonato).

GRAVIDEZ E LACTAÇÃO

O uso de LACTULONA® em mulheres grávidas ou durante o aleitamento deve ser feito sob orientação médica.

Informe ao seu médico se estiver grávida ou amamentando.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.

EXAMES LABORATORIAIS

Informe o uso de LACTULONA® ao seu médico antes de se submeter a qualquer exame proctológico (colonoscopia, retosigmoidoscopia).

MEDICAMENTOS

Os antibióticos podem diminuir a ação da LACTULONA® no intestino, reduzindo seus efeitos esperados. LACTULONA® não deve ser administrada juntamente com outros laxantes ou antiácidos, pois pode ocorrer aumento ou diminuição dos seus efeitos.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento', 'https://www.bulas.med.br/bula/lactulona', 'Sem Tarja', '2026-11-22T23:23:57.460Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/lactulona');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Valerato de Estradiol', 'Valerato de Estradiol', 'Primogyna (valerato de estradiol) é um medicamento cuja substância ativa se destina à reposição hormonal feminina (reposição estrogênica), aliviando os sintomas associados à menopausa.', 'Este medicamento é indicado à terapia de reposição hormonal (TRH) para o tratamento dos sintomas da menopausa em mulheres com útero intacto ou histerectomizadas.', 'Siga rigorosamente o procedimento indicado, pois o não-cumprimento pode ocasionar falha na obtenção dos resultados.

Cada cartela contém drágeas para 28 dias de tratamento. Você deve tomar uma drágea diária e continuamente, isto é, após terminar a primeira cartela (28 drágeas) deve-se começar a próxima cartela no dia seguinte.

Ingira uma drágea bege (1 mg) ou uma drágea azul (2 mg) por dia, com ajuda de pequena quantidade de líquido, sem mastigar e, de preferência, sempre à mesma hora do dia. É indiferente o horário do dia em que a drágea seja tomada. Mas, uma vez escolhido o horário deve-se mantê-lo aproximadamente constante.

INÍCIO DO USO DE PRIMOGYNA (VALERATO DE ESTRADIOL):

Se você ainda estiver menstruando regularmente, deve começar o tratamento com Primogyna (valerato de estradiol) nos primeiros cinco dias de menstruação com um regime combinado de Primogyna (valerato de estradiol) e um progestógeno. O seu médico irá orientá-la como tomá-lo.

Caso suas menstruações sejam pouco frequentes ou já tenham cessado em razão da menopausa, o tratamento pode ser iniciado com um regime combinado em qualquer momento, desde que seja excluída a existência de gravidez.

Se você não possui mais útero (mulheres histerectomizadas) pode iniciar o uso de Primogyna (valerato de estradiol) a qualquer momento. Seu médico irá orientá-la.

MUDANDO DE OUTRA TERAPIA DE REPOSIÇÃO HORMONAL (CÍCLICA, SEQUENCIAL OU COMBINADA CONTÍNUA)

Se você estiver mudando de outra TRH, deve completar o ciclo com o medicamento em uso antes de iniciar a terapia com Primogyna (valerato de estradiol).

POPULAÇÕES ESPECIAIS

CRIANÇAS E ADOLESCENTES

Primogyna (valerato de estradiol) não é indicado para o uso em crianças e adolescentes.

PACIENTES IDOSAS

Não existem dados que sugiram a necessidade de ajuste de dose em pacientes idosas. Informe seu médico se você tem 65 anos ou mais (vide item “O que devo saber antes de usar este medicamento?”).

PACIENTES COM DISFUNÇÃO HEPÁTICA

Primogyna (valerato de estradiol) não foi especificamente estudado em pacientes com disfunção hepática. Primogyna (valerato de estradiol) é contraindicado em mulheres com doença hepática grave (vide item “Quando não devo usar este medicamento?”).

PACIENTES COM DISFUNÇÃO RENAL

Não foram conduzidos estudos em mulheres com insuficiência renal.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento. Não interrompa o tratamento sem o conhecimento do seu médico.

Este medicamento não deve ser partido, aberto ou mastigado.', 'Primogyna (valerato de estradiol) não deve ser utilizado na presença das condições listadas abaixo. Caso apresente qualquer uma destas condições, informe seu médico antes de iniciar o tratamento.

Em caso de gravidez ou lactação; sangramento vaginal de causa desconhecida; presença ou suspeita de câncer de mama ou de outras doenças malignas dependentes de hormônios sexuais; presença ou história de tumor hepático (benigno ou maligno); doença grave do fígado; história recente de ataque cardíaco e/ou derrame; presença ou história de trombose (formação de coágulo) nos vasos sanguíneos das pernas (trombose venosa profunda) ou dos pulmões (embolia pulmonar); alto risco de trombose venosa ou arterial (coágulo sanguíneo); níveis sanguíneos muito elevados de triglicérides (um tipo especial de gordura) no sangue; alergia a qualquer um dos componentes da fórmula do produto.

Se qualquer uma dessas condições surgir pela primeira vez durante o tratamento com Primogyna (valerato de estradiol) descontinue o uso imediatamente e consulte seu médico.', 'As reações adversas mais graves associadas à TRH estão listadas no item “O que devo saber antes de usar este medicamento?”.

As seguintes reações adversas foram observadas em usuárias de TRH classificadas por sistema corpóreo:

Reações adversas comuns (entre 1 e 10 pessoas a cada 100 usuárias podem apresentar estas reações): aumento ou diminuição de peso corporal, dor de cabeça, dor abdominal, náusea, erupção cutânea, coceira, sangramento uterino/vaginal incluindo gotejamento (sangramentos irregulares normalmente desaparecem com a continuação do tratamento).

Reações adversas incomuns (entre 1 e 10 pessoas a cada 1.000 usuárias podem apresentar estas reações): reação alérgica, estados depressivos, tontura, distúrbios visuais, palpitações (batimentos cardíacos rápidos, irregulares), má digestão, eritema nodoso (nódulos avermelhados doloridos), urticária, dor nas mamas, hipersensibilidade dolorosa nas mamas, edema.

Reações adversas raras (entre 1 e 10 pessoas a cada 10.000 usuárias podem apresentar estas reações): ansiedade, aumento ou diminuição do desejo sexual (libido), enxaqueca, intolerância a lentes de contato, distensão abdominal, vômito, crescimento excessivo de pelos, acne, cãibras musculares, dismenorreia, secreção vaginal, síndrome semelhante à pré-menstrual, aumento do tamanho das mamas, fadiga.

Sinônimos ou condições relacionadas não foram listados, mas também devem ser considerados.

Em mulheres com episódios de inchaço em algumas partes do corpo como as mãos, pés e vias aéreas, causados por angioedema hereditário, o hormônio valerato de estradiol contido em Primogyna (valerato de estradiol) pode induzir ou intensificar estes sinais e sintomas (veja item “O que devo saber antes de usar este medicamento?”).

Se ocorrer qualquer reação adversa não mencionada nesta bula ou caso você esteja insegura sobre o efeito deste medicamento, informe seu médico.

Em alguns estudos, a ocorrência de câncer de ovário foi observada com frequência ligeiramente maior em usuárias de TRH (TRH com apenas estrogênio ou com a combinação de estrogênio-progestógeno). O risco pode ser mais relevante com o uso prolongado (durante vários anos).

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também a empresa através do seu serviço de atendimento.', NULL, 'ADVERTÊNCIAS E PRECAUÇÕES

Antes de iniciar ou reiniciar o tratamento, seu médico poderá solicitar a realização de exames clínicos gerais e ginecológicos, incluindo mamas, pressão sanguínea, dentre outros.

Seu médico irá discutir com você os benefícios e os riscos do uso de Primogyna (valerato de estradiol). Ele irá verificar, por exemplo, se você tem risco mais elevado de trombose devido a uma combinação de fatores. Neste caso, o risco pode ser ainda maior. Se você apresenta alguma doença no fígado, pode ser necessário acompanhamento frequente da função hepática.

Em caso de presença de adenoma da parte anterior da hipófise, é necessário acompanhamento médico rigoroso, incluindo avaliação periódica dos níveis de prolactina.

Se você ainda possui a capacidade de engravidar é pouco provável que Primogyna (valerato de estradiol) altere esta condição. Se você está utilizando medidas contraceptivas (exceto o uso de contraceptivos orais ou outro método contraceptivo hormonal) ao iniciar Primogyna (valerato de estradiol), continue utilizando-as até que seu médico lhe diga que não é mais necessário. Se você estiver utilizando um contraceptivo oral (ou outro método contraceptivo hormonal), você deve mudá-lo para um método contraceptivo alternativo (não hormonal) antes de iniciar Primogyna (valerato de estradiol). Se seu médico já lhe orientou que você não precisa usar um método contraceptivo, não será necessário nenhuma destas medidas durante o uso de Primogyna (valerato de estradiol).

Converse com seu médico sobre os benefícios e os riscos da utilização de Primogyna (valerato de estradiol).

Dois grandes estudos clínicos realizados com estrogênios equinos conjugados (EEC) combinados com acetato de medroxiprogesterona (AMP), hormônios utilizados na terapia de reposição hormonal (TRH), sugerem que o risco de ocorrência de ataque cardíaco (infarto do miocárdio) pode aumentar discretamente no primeiro ano de uso destas substâncias. Este risco não foi observado em um grande estudo clínico realizado somente com estrogênios equinos conjugados (EEC). Em dois outros grandes estudos clínicos realizados com estes hormônios, o risco de ocorrer derrame aumentou em 30 a 40%.

Embora estes estudos não tenham sido feitos com Primogyna (valerato de estradiol), não se deve usar este medicamento para prevenir doença cardíaca e/ou derrame.

O uso de TRH requer cuidadosa supervisão médica na presença das condições descritas a seguir, as quais devem ser certificadas com o seu médico antes do início do uso de Primogyna (valerato de estradiol):

• No caso de risco aumentado de trombose (formação de um coágulo sanguíneo). O risco aumenta com a idade e pode ser ainda maior se você ou qualquer familiar direto teve trombose nos vasos das pernas ou pulmões; em caso de excesso de peso; se tem veias varicosas. Converse com seu médico antecipadamente em caso de hospitalização programada ou cirurgia. O risco de trombose venosa profunda pode aumentar temporariamente com cirurgias de grande porte, traumatismos graves ou imobilização prolongada;

• Mioma uterino;

• Diagnóstico ou histórico de endometriose (presença do tecido de revestimento do útero em locais do corpo onde normalmente não seria encontrado);

• Doença do fígado ou da vesícula biliar;

• Icterícia (amarelamento da pele) durante gravidez ou uso prévio de hormônios sexuais;

• Diabetes Mellitus;

• Níveis elevados de triglicérides (um tipo de gordura no sangue);

• Pressão alta;

• Presença ou histórico de cloasma (manchas marrom-amareladas na pele); neste caso, deve-se evitar exposição prolongada ao sol ou à radiação ultravioleta durante a terapia de reposição hormonal;

• Epilepsia;

• Mama com nódulo ou dolorida (doença benigna da mama);

• Asma;

• Enxaqueca;

• Doença hereditária chamada porfiria;

• Otosclerose (surdez adquirida);

• Lúpus eritematoso sistêmico (LES, doença imunológica crônica);

• Coreia menor (doença com movimentos involuntários e anormais do corpo);

• Episódios de inchaço em algumas partes do corpo, como as mãos, pés, face e vias aéreas, causadas por angioedema hereditário. O hormônio valerato de estradiol contido em Primogyna (valerato de estradiol) pode induzir ou intensificar estes sinais e sintomas de angioedema hereditário;

• Se você tem 65 anos de idade ou mais ao iniciar o tratamento, uma vez que existem evidências limitadas, a partir de estudos clínicos, de que o tratamento hormonal pode aumentar o risco de perda significativa de habilidades intelectuais, como memória (demência).

TRH E CÂNCER

CÂNCER ENDOMETRIAL

O risco de câncer endometrial aumenta quando estrogênios são utilizados isoladamente por períodos prolongados. Em mulheres não-histerectomizadas, é necessário o uso adicional de um hormônio progestógeno durante o tratamento com Primogyna (valerato de estradiol). O uso apropriado de um progestógeno elimina o aumento do risco de câncer endometrial.

Você deve informar ao seu médico em caso de sangramentos irregulares frequentes ou persistentes durante o uso de Primogyna (valerato de estradiol).

CÂNCER DE MAMA

Em alguns estudos, foi relatado um aumento discreto na frequência do diagnóstico de câncer de mama em mulheres que utilizaram TRH durante vários anos. O risco aumenta com a duração do tratamento, mas pode ser menor ou possivelmente neutro para produtos que contenham somente estrogênio. Quando a TRH é descontinuada, este risco aumentado desaparece em alguns anos. Aumentos similares no diagnóstico de câncer de mama são observados, por exemplo, nos casos de atraso da menopausa natural, consumo de bebidas alcoólicas e nos casos de adiposidade.

A TRH pode alterar a imagem da mamografia (aumenta a densidade das imagens mamográficas). Este fato pode dificultar a detecção mamográfica de câncer em alguns casos, portanto, pode ser necessária a utilização de outras técnicas.

CÂNCER DE OVÁRIO

O câncer de ovário é menos comum do que o câncer de mama.

Alguns estudos mostram um ligeiro aumento do risco global de desenvolver câncer de ovário em mulheres que usaram TRH quando comparado com as que nunca usaram TRH. Em mulheres que atualmente usam TRH, esse risco foi mais aumentado. Estas associações não foram mostradas em todos os estudos. Não há evidência consistente de que o risco de desenvolver câncer de ovário esteja relacionado com a duração da TRH. No entanto, o risco pode ser mais relevante com o uso prolongado (durante vários anos).

TUMOR NO FÍGADO

Após o uso de hormônios como o contido em Primogyna (valerato de estradiol) foram observados, em casos raros, tumores hepáticos benignos e, mais raramente, malignos que, em casos isolados, ocasionaram hemorragias intra-abdominais com risco para a vida da paciente. Apesar de estes eventos serem extremamente raros, você deve informar seu médico se ocorrer sintomas inesperados no abdômen superior que não desapareçam em um curto espaço de tempo.

O tratamento deve ser descontinuado imediatamente, e seu médico informado, em caso de presença de qualquer uma das condições listadas a seguir: aparecimento pela primeira vez de dores de cabeça do tipo enxaqueca (tipicamente dor de cabeça pulsante e náusea, precedida por distúrbios visuais); piora de enxaqueca preexistente, ou dores de cabeça com frequência e intensidade não habituais; perturbações auditivas ou visuais repentinas; inflamações venosas (flebites).

Se você apresentar ou suspeitar da ocorrência de um coágulo durante a utilização de Primogyna (valerato de estradiol), você deve interromper o tratamento imediatamente e consultar o seu médico. Atente-se aos sinais como: falta de ar e tosse com sangue, inchaço ou dores não-habituais nos braços ou pernas, respiração curta repentina e desmaio.

Primogyna (valerato de estradiol) deve ser descontinuado em caso de ocorrência de gravidez ou desenvolvimento de icterícia.

Atenção: este medicamento contém açúcar, portanto, deve ser usado com cautela em portadores de diabetes.

GRAVIDEZ E LACTAÇÃO

Primogyna (valerato de estradiol) não deve ser utilizado durante a gravidez (veja o item “Quando não devo usar este medicamento?”). Se ocorrer gravidez durante o uso de Primogyna (valerato de estradiol), o tratamento deve ser descontinuado imediatamente.

Estudos epidemiológicos extensivos realizados com hormônios esteroides utilizados para contracepção e para terapia de reposição hormonal não revelaram aumento no risco de malformação congênita em crianças cujas mães utilizaram hormônios sexuais antes da gravidez ou efeitos teratogênicos quando hormônios sexuais foram administrados de forma inadvertida durante a fase inicial da gestação.

O uso de TRH é contraindicado durante a amamentação, pois pequenas quantidades de hormônios sexuais podem ser excretadas com o leite materno.

INGESTÃO CONCOMITANTE COM OUTRAS SUBSTÂNCIAS

Não utilize contraceptivos hormonais. Consulte seu médico quanto a métodos contraceptivos, se necessário.

Alguns medicamentos podem ter influência nos níveis de Primogyna no sangue e interferir com a terapia de reposição hormonal (TRH) podendo causar um sangramento inesperado e / ou diminuir o efeito da TRH. Incluem-se aqueles usados para o tratamento de epilepsia (por exemplo, fenitoína, barbitúricos, primidona, carbamazepina e, possivelmente oxcarbazepina, topiramato e felbamato), de tuberculose (por exemplo, rifampicina), de infecção pelos vírus que causam hepatite C e HIV (conhecidos como inibidores de protease e inibidores não nucleosídios da transcriptase reversa), de infecções por fungos (griseofulvina, antifúngicos azólicos, como por exemplo, fluconazol, itraconazol, cetoconazol e voriconazol), de infecções bacterianas (antibióticos macrolídeos, por exemplo, claritromicina e eritromicina), de certas doenças do coração e pressão alta (bloqueadores de canal de cálcio, por exemplo, verapamil e diltiazem). Incluem-se também erva de São João e suco de toranja (grapefruit) e paracetamol.

INGESTÃO DE ÁLCOOL

A ingestão excessiva de bebidas alcoólicas durante a terapia de reposição hormonal pode interferir no tratamento.

EXAMES LABORATORIAIS

A TRH pode alterar os resultados de certos exames laboratoriais. Informe ao seu médico, ou a equipe do laboratório que você está em terapia de reposição hormonal.

EFEITOS SOBRE A CAPACIDADE DE DIRIGIR VEÍCULOS OU OPERAR MÁQUINAS

Não foram observados efeitos na capacidade de dirigir veículos ou operar máquinas em usuárias de Primogyna.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento.

Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/primogyna', 'Vermelha', '2026-11-22T23:23:57.462Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/primogyna');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Acetato de Medroxiprogesterona', 'Acetato de Medroxiprogesterona', 'Provera® é um medicamento sintético, semelhante ao hormônio progesterona que existe normalmente no organismo da mulher. Esse medicamento age regulando o sistema endócrino (hormonal) e, portanto, controlando o ciclo menstrual e/ou sangramentos resultantes deste.', 'Provera® (acetato de medroxiprogesterona) é indicado para o tratamento da amenorreia secundária (ausência de menstruação por um período maior do que 3 meses em mulher que anteriormente já apresentou ciclos menstruais); no tratamento do sangramento uterino disfuncional devido ao desequilíbrio hormonal (sangramento uterino anormal decorrente de um estímulo hormonal inadequado), na ausência de doenças orgânicas (doenças do organismo), como mioma (tumor benigno da parede do útero) ou carcinoma no útero (tumor maligno). É também indicado na terapia hormonal (tratamento em que se usam hormônios) em oposição aos efeitos endometriais (relativos ao endométrio – camada que recobre o útero internamente) do estrogênio (hormônio feminino) em mulheres na menopausa não histerectomizadas (sem retirada total ou parcial do útero), como complemento ao tratamento estrogênico (com estrógeno).', 'O uso combinado de estrogênio/progesterona na pós-menopausa deve se limitar à menor dose eficaz e na menor duração consistente com as metas do tratamento e os riscos individuais para cada paciente (veja questões 3 e 4) e deve ser periodicamente avaliado pelo seu médico. São recomendadas avaliações periódicas com frequência e natureza adaptadas para cada paciente. Não é recomendado administrar progesterona a uma paciente sem o útero intacto, a menos que haja um diagnóstico prévio de endometriose (doença caracterizada por presença de endométrio fora do útero).

Tratamento de amenorreia secundária: Recomenda-se a administração de Provera® por 5 a 10 dias, por 3 ciclos consecutivos.. Em pacientes com hipotrofia (atrofia / involução) do endométrio, estrógenos devem ser utilizados concomitantemente à terapia com Provera®.

Sangramento uterino disfuncional devido ao desequilíbrio hormonal, na ausência de patologias orgânicas (doenças do organismo): Provera® pode ser administrado por 5 a 10 dias, por 2 a 3 ciclos e então a terapia deve ser descontinuada para se verificar se o sangramento regrediu. Se o sangramento provém de um endométrio pouco proliferativo, estrógenos devem ser utilizados concomitantemente à terapia com Provera®.

Terapia hormonal em oposição aos efeitos endometriais (relativos ao endométrio) do estrogênio em mulheres na menopausa não histerectomizadas, como complemento à terapia estrogênica: Para mulheres recebendo 0,625 mg de estrógenos conjugados ou dose equivalente diárias de qualquer outro estrógeno, Provera® pode ser administrado no seguinte esquema:

• Administração sequencial: administrar doses diárias de Provera® por 10 a 14 dias consecutivos, a cada 28 dias ou a cada ciclo mensal.

Uso em pacientes com Insuficiência Hepática (diminuição da função do fígado): Não foram realizados estudos clínicos para avaliar o efeito do Provera® e a farmacocinética em pacientes com doença hepática. Contudo, Provera® é quase exclusivamente eliminado pelo metabolismo hepático (pelo funcionamento do fígado) e os hormônios esteroides (hormônios derivados do colesterol, incluindo os hormônios sexuais, como estrógeno e progesterona) podem ser pouco metabolizados em pacientes com insuficiência hepática severa (veja questão 3).

Uso em pacientes com Insuficiência Renal (diminuição da função dos rins): Não foram realizados estudos clínicos para avaliar o efeito de Provera® e a farmacocinética em pacientes com doença renal. Contudo, sendo que Provera® é quase exclusivamente eliminado pelo metabolismo hepático, não é necessário ajuste de dose em pacientes com insuficiência renal.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento. Não interrompa o tratamento sem o conhecimento do seu médico.

Este medicamento não deve ser partido, aberto ou mastigado.', 'Provera® é contraindicado nas seguintes condições: a pacientes que apresentam ou já tiveram doenças vasculares [tromboflebite (coágulo com inflamação dos vasos sanguíneos), distúrbios tromboembólicos (formação de um trombo, coágulo, no interior dos vasos sanguíneos)], e cerebrovasculares (alterações nos vasos sanguíneos do cérebro); insuficiência (diminuição) grave da função do fígado; quando houver presença ou suspeita de doença maligna dos órgãos genitais; no sangramento vaginal de causa desconhecida; a pacientes que apresentam hipersensibilidade (alergia) à medroxiprogesterona, ou a qualquer componente da fórmula.

Provera® está contraindicado na presença ou suspeita de doença maligna de mama.

Este medicamento não deve ser utilizado por mulheres grávidas ou que possam ficar grávidas durante o tratamento.

Este medicamento é contraindicado para uso em mulheres grávidas', 'Informe ao seu médico o aparecimento de qualquer reação desagradável durante o tratamento com Provera®, tais como:

Reação muito comum (ocorre em 10% ou mais dos pacientes que utilizam este medicamento): dor de cabeça, náusea, sangramento uterino disfuncional (irregular, aumento, redução, spotting).

Reação comum (ocorre entre 1% e 10% dos pacientes que utilizam este medicamento): hipersensibilidade (reação alérgica) ao medicamento, depressão, insônia, nervosismo, tontura, alopecia (perda de cabelo), acne, urticária (alergia de pele), prurido (coceira), corrimento cervical, dor na mama, sensibilidade na mama, febre, fadiga (cansaço), aumento de peso.

Reação incomum (ocorre entre 0,1% e 1% dos pacientes que utilizam este medicamento): hirsutismo (aumento de pelos), galactorreia (secreção inapropriada de leite), edema (inchaço), retenção de líquidos,

Frequência não conhecida (não pode ser estimada a partir dos dados disponíveis): reação anafilática (reação alérgica), reação anafilactoide (reações alérgicas graves), angioedema (inchaço das partes mais profundas da pele ou da mucosa, geralmente de origem alérgica), anovulação (ausência de ovulação) prolongada, sonolência, embolismo e trombose (entupimento de uma veia por trombo), icterícia (coloração amarelada da pele e mucosas por acúmulo de pigmentos biliares), icterícia colestática (tipo de icterícia), lipodistrofia adquirida* (alteração da distribuição da gordura corporal), rash (erupção cutânea), amenorreia (ausência de menstruação), erosão do colo uterino (inflamação do colo do útero), diminuição da tolerância à glicose (alteração da glicose), perda de peso.

* Reação adversa ao medicamento identificada pós-comercialização

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento.

Informe também à empresa através do seu serviço de atendimento.', 'Provera® pode interagir com outros medicamentos, como a aminoglutetimida.

Se tiver que realizar exames laboratoriais, informe ao médico patologista que está em tratamento com este medicamento..

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'Informe ao seu médico se você tem ou teve depressão, retenção de líquidos, ou tromboembolismo venoso. Informe ao seu médico se você é diabética.

Vários estudos realizados quanto aos efeitos em longo prazo de esquema combinado estrógeno-progestágeno (hormônios femininos) em mulheres pós-menopausadas (que já entraram na menopausa) relataram um aumento no risco de vários distúrbios, incluindo doenças cardiovasculares [doenças do coração ou dos vasos sanguíneos, como por exemplo, doença coronariana (doença em que ocorre obstrução dos vasos sanguíneos que levam sangue ao músculo do coração, podendo levar ao infarto), acidente vascular cerebral (“derrame”), tromboembolismo venoso (formação de coágulo dentro de uma veia) e embolia pulmonar (formação de um coágulo dentro de um vaso sanguíneo do pulmão)]; câncer (tumor maligno) de mama; demência (doença caracterizada por perda progressiva da memória) e câncer do ovário.

Este medicamento pode interromper a menstruação por período prolongado e/ou causar sangramentos intermenstruais severos.

Procure seu médico se ocorrer perda completa ou parcial súbita da visão ou no caso de instalação súbita de proptose (avanço anormal de ambos os olhos), visão dupla ou enxaqueca (dor de cabeça)

GRAVIDEZ E LACTAÇÃO

Este medicamento causa malformação ao bebê durante a gravidez.

Provera® e seus metabólitos são excretados (eliminados) no leite materno. Não há evidência sugerindo que esse fato determine qualquer dano ao bebê.

INFORMAÇÕES IMPORTANTES SOBRE UM DOS COMPONENTES DO MEDICAMENTO

Atenção: Este medicamento contém Açúcar, portanto, deve ser usado com cautela em portadores de Diabetes.

INTERAÇÕES MEDICAMENTOSAS

Provera® pode interagir com outros medicamentos, como a aminoglutetimida.

Se tiver que realizar exames laboratoriais, informe ao médico patologista que está em tratamento com este medicamento..

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/provera', 'Vermelha', '2026-11-22T23:23:57.463Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/provera');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Etinilestradiol + Levonorgestrel', 'Etinilestradiol + Levonorgestrel', 'Levonorgestrel + etinilestradiol é um contraceptivo oral que combina 2 hormônios, o etinilestradiol e o levonorgestrel. Os contraceptivos orais combinados, que possuem 2 hormônios em sua composição, suprimem as gonadotrofinas, ou seja, inibem os estímulos hormonais que levam à ovulação, o que leva à contracepção.', 'Levonorgestrel + etinilestradiol é indicado na prevenção da gravidez e para o controle de irregularidades menstruais. Embora os contraceptivos orais sejam altamente eficazes, há casos de gravidez em mulheres que os utilizam.', 'COMO TOMAR

Os comprimidos devem ser tomados diariamente no mesmo horário e na ordem indicada na embalagem.

Tomar um comprimido diariamente por 21 dias consecutivos, seguido de um intervalo de 7 dias sem a ingestão de comprimidos. A embalagem seguinte deve ser iniciada após o intervalo de 7 dias sem a ingestão de comprimidos, ou seja, no 8° dia após o término da embalagem anterior. Após 2-3 dias do último comprimido de levonorgestrel + etinilestradiol ter sido tomado, inicia-se, em geral, a menstruação que pode não cessar antes do início da embalagem seguinte.

Como começar a tomar levonorgestrel + etinilestradiol: Sem uso anterior de contraceptivo hormonal no mês anterior: o primeiro comprimido de levonorgestrel + etinilestradiol deve ser tomado no 1º dia do ciclo natural (ou seja, o primeiro dia de sangramento menstrual). Pode-se iniciar o tratamento com levonorgestrel + etinilestradiol entre o 2º e o 7º dia do ciclo menstrual, mas recomenda-se a utilização de método contraceptivo não hormonal (como preservativo e espermicida) nos primeiros 7 dias de administração de levonorgestrel + etinilestradiol.

Quando se passa a usar levonorgestrel + etinilestradiol no lugar de outro contraceptivo oral: preferencialmente, deve-se começar a tomar levonorgestrel + etinilestradiol no dia seguinte ao último comprimido ativo do contraceptivo oral combinado (com 2 hormônios) anterior ter sido ingerido, mas não mais tarde do que no dia após o intervalo sem comprimidos ou após a ingestão do último comprimido inativo (sem efeito) do contraceptivo oral combinado anterior.

Quando se passa a usar levonorgestrel + etinilestradiol no lugar de outro método com apenas progestogênio (minipílulas, implante, dispositivos intrauterinos [DIU], injetáveis): pode-se interromper o uso da minipílula em qualquer dia e deve-se começar a tomar levonorgestrel + etinilestradiol no dia seguinte. Deve-se iniciar o uso de levonorgestrel + etinilestradiol no mesmo dia da remoção do implante de progestogênio ou remoção do DIU. O uso de levonorgestrel + etinilestradiol deve ser iniciado na data em que a próxima injeção está programada.

Em cada uma dessas situações, a paciente deve ser orientada a utilizar outro método não hormonal de contracepção durante os 7 primeiros dias de administração de levonorgestrel + etinilestradiol.

Após aborto no primeiro trimestre: pode-se começar a tomar levonorgestrel + etinilestradiol imediatamente. Não são necessários outros métodos contraceptivos.

Pós parto: como o pós-parto imediato está associado ao aumento do risco de tromboembolismo (obstrução de um ou mais vaso sanguíneos por coágulo), o tratamento com levonorgestrel + etinilestradiol não deve começar antes do 28° dia após o parto em mulheres não lactantes (que não estão amamentando) ou após aborto no segundo trimestre. Deve-se orientar a paciente a utilizar outro método não hormonal de contracepção durante os 7 primeiros dias de administração de levonorgestrel + etinilestradiol. Entretanto, se já tiver ocorrido relação sexual, a possibilidade de gravidez antes do início da utilização de levonorgestrel + etinilestradiol deve ser descartada ou deve-se esperar pelo primeiro período menstrual espontâneo (vide item “4. O que devo saber antes de usar este medicamento?”).

ORIENTAÇÃO EM CASO DE VÔMITOS E/OU DIARREIA

No caso de vômito e/ou diarreia no período de 4 horas após a ingestão do comprimido, a absorção pode ser incompleta. Neste caso, um comprimido extra, de uma outra cartela, deve ser tomado. Para mais informações, consulte o item 7. O que devo fazer quando eu me esquecer de tomar este medicamento?.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento. Não interromper o tratamento sem o conhecimento do seu médico.

Este medicamento não deve ser partido ou mastigado.', 'Levonorgestrel + etinilestradiol não deve ser utilizado por mulheres grávidas ou com suspeita de gravidez, ou ainda por mulheres que estejam amamentando.

Levonorgestrel + etinilestradiol não deve ser utilizado por mulheres com hipersensibilidade (alergia) a qualquer um dos componentes de levonorgestrel + etinilestradiol.

Levonorgestrel + etinilestradiol não deve ser utilizado por mulheres que apresentem qualquer uma das seguintes condições: história anterior ou atual de trombose venosa profunda (obstrução de uma veia); história anterior ou atual de tromboembolismo (obstrução de um ou mais vasos sanguíneos por coágulo); doença vascular cerebral (“derrame”) ou arterial coronariana; valvulopatias trombogênicas (alteração cardíaca que leva à formação de coágulos); distúrbios do ritmo cardíaco trombogênico (alteração do ritmo do coração que leva à formação de coágulos), trombofilias hereditárias ou adquiridas (distúrbios da coagulação com formação de coágulos); dor de cabeça com sintomas neurológicos tais como aura (sensações que antecedem crises de enxaqueca, que podem ser alterações na visão, formigamentos no corpo ou diminuição de força); diabetes com comprometimento da circulação; hipertensão (pressão alta) não controlada; câncer de mama ou outra neoplasia dependente do hormônio estrogênio conhecido ou suspeito; tumores do fígado, ou doença do fígado ativa, desde que a função hepática não tenha retornado ao normal; sangramento vaginal sem causa determinada; história anterior ou atual de pancreatite associada à hipertrigliceridemia severa (inflamação do pâncreas com aumento dos níveis de triglicerídeos no sangue). Os contraceptivos orais combinados são contraindicados para o uso concomitante com certos medicamentos antivirais contra o vírus da hepatite C (HCV), como ombistavir, paritaprevir, ritonavir e dasabuvir.

Este medicamento não deve ser utilizado por mulheres grávidas ou que possam ficar grávidas durante o tratamento.

Este medicamento causa malformação ao bebê durante a gravidez. Este medicamento é contraindicado para uso por homens.

Atenção: Este medicamento contém AÇÚCAR, portanto deve ser utilizado com cautela em portadores de Diabetes.', 'O uso de contraceptivos orais combinados tem sido associado ao aumento dos seguintes riscos:

• Eventos tromboembólicos (formação e eliminação de coágulos nos vasos sanguíneos) e trombóticos (obstrução) arteriais e venosos, incluindo infarto do miocárdio, acidente vascular cerebral (“derrame”), ataque isquêmico transitório (sintomas do derrame, porém com regressão em 24 horas), trombose venosa (obstrução de uma veia) e embolia pulmonar (obstrução de um vaso pulmonar por coágulo);

• Câncer de colo de útero;

• Câncer de mama;

• Tumores hepáticos (do fígado) benignos (por exemplo: hiperplasia nodular focal, adenoma hepático). As reações adversas estão relacionadas de acordo com sua frequência:

Reação muito comum (ocorre em mais de 10% dos pacientes que utilizam este medicamento): cefaleia (dor de cabeça), incluindo enxaqueca, sangramento de escape.

Reação comum (ocorre ente 1% e 10% dos pacientes que utilizam este medicamento): vaginite (inflamação na vagina), incluindo candidíase (infecção causada pelo fungo Candida); alterações de humor, incluindo depressão, alterações de libido, nervosismo, tontura, náuseas (enjoo), vômitos, dor abdominal, acne, dor das mamas, aumento da sensibilidade das mamas, aumento do volume mamário, saída de secreção das mamas, dismenorreia (cólica menstrual), alteração do fluxo menstrual, alteração da secreção e ectrópio cervical (alteração do epitélio do colo do útero), amenorreia (falta da menstruação), retenção hídrica/edema (inchaço), alterações de peso (ganho ou perda).

Reação incomum (ocorre ente 0,1% e 1% dos pacientes que utilizam este medicamento): alterações de apetite (aumento ou diminuição), cólicas abdominais, distensão (aumento do volume abdominal), erupções cutâneas (lesão na pele), cloasma/melasma (manchas escuras na pele do rosto), que pode persistir; hirsutismo (aumento dos pelos), alopecia (perda de cabelo), aumento da pressão arterial, alterações nos níveis séricos de lipídios, incluindo hipertrigliceridemia (aumento dos triglicerídeos).

Reação rara (ocorre ente 0,01% e 0,1% dos pacientes que utilizam este medicamento): reações anafiláticas/anafilactoides (reações alérgicas graves), incluindo casos muito raros de urticária (alergia da pele), angioedema (inchaço das partes mais profundas da pele ou da mucosa, geralmente de origem alérgica) e reações graves com sintomas respiratórios e circulatórios, intolerância à glicose (aumento das taxas de açúcar no sangue), intolerância à lentes de contato, icterícia colestática (coloração amarelada da pele e mucosas por acúmulo de pigmentos biliares, devido à obstrução), eritema nodoso (nódulos [protuberâncias] subcutâneos vermelhos e dolorosos), diminuição dos níveis séricos de folato***.

Reação muito rara (ocorre com menos de 0,01% dos pacientes que utilizam este medicamento): carcinomas hepatocelulares (câncer de fígado), exacerbação do lúpus eritematoso sistêmico, exacerbação da porfiria, exacerbação da coreia, neurite óptica* (inflamação do nervo do olho), trombose vascular retiniana (obstrução de um vaso da retina), piora das varizes, pancreatite (inflamação no pâncreas), colite isquêmica (inflamação do intestino grosso ou cólon por falta de oxigenação), doença biliar, incluindo cálculos biliares** (cálculo na vesícula biliar), eritema multiforme (manchas vermelhas, bolhas e/ou ulcerações pelo corpo), síndrome hemolítico-urêmica (síndrome caracterizada por anemia, diminuição do número de plaquetas e prejuízo na função renal entre outras alterações).

Reações adversas cuja frequência é desconhecida: doença inflamatória intestinal (Doença de Crohn, colite ulcerativa), lesão hepatocelular (por exemplo: hepatite, função anormal do fígado).

*A neurite óptica (inflamação de um nervo do olho) pode resultar em perda parcial ou total da visão.

** Os contraceptivos orais combinados podem piorar doenças biliares preexistentes e podem acelerar o desenvolvimento dessa doença em mulheres que anteriormente não tinham estes sintomas.

*** Pode haver diminuição dos níveis séricos de folato com o tratamento com contraceptivo oral combinado. Isso pode ser clinicamente significativo se a mulher engravidar logo após descontinuar os contraceptivos orais combinados.

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também à empresa através do seu serviço de atendimento.', 'Alguns medicamentos podem reduzir a eficácia dos contraceptivos orais quando tomados ao mesmo tempo.

Interações entre etinilestradiol (um dos hormônios presentes no levonorgestrel + etinilestradiol) e outras substâncias podem diminuir ou aumentar as concentrações séricas (no sangue) de etinilestradiol. O uso concomitante com os medicamentos contendo ombitasvir/paritaprevir/ritonavir e dasabuvir, com ou sem ribavirina, pode aumentar o risco de elevações de ALT. Portanto as usuárias de contraceptivos orais combinados devem mudar somente para um método contraceptivo alternativo (por exemplo, métodos contraceptivos somente com progestagênio ou não hormonais) antes de iniciar a terapia com medicamentos antivirais de HCV, como ombitasvir, paritaprevir, ritonavir, dasabuvir. Os contraceptivos orais combinados podem ser reiniciados 2 semanas após a conclusão do tratamento com um medicamento antiviral HCV. Concentrações séricas (no sangue) mais baixas de etinilestradiol podem causar maior incidência de sangramento de escape e irregularidades menstruais e, possivelmente, podem reduzir a eficácia do contraceptivo oral combinado. Durante o uso concomitante de produtos com etinilestradiol e substâncias que podem diminuir as concentrações séricas de etinilestradiol, recomenda-se que um método anticoncepcional não hormonal (como preservativos e espermicida) seja utilizado além da ingestão regular de levonorgestrel + etinilestradiol. No caso de uso prolongado dessas substâncias, os contraceptivos orais combinados não devem ser considerados os contraceptivos primários (principal).

Após a descontinuação das substâncias que podem diminuir as concentrações séricas de etinilestradiol, recomenda-se o uso de um método anticoncepcional não hormonal por, no mínimo, 7 dias. Em alguns casos é necessário o uso por um tempo mais prolongado do método anticoncepcional não hormonal, deste modo converse com o seu médico para que ele possa avaliar possíveis interações com outros medicamentos e/ou substâncias.

A seguir, alguns exemplos das substâncias que podem diminuir as concentrações séricas de etinilestradiol:

• qualquer substância que reduza o tempo do trânsito gastrintestinal e, portanto, a absorção do etinilestradiol;

• medicamentos como: rifampicina (medicamento usado para tratamento de tuberculose), rifabutina, barbitúricos (medicamentos utilizados em anestesias), fenilbutazona, fenitoína (antiepiléptico), dexametasona, griseofulvina (medicamento antifúngico, para tratamento de micoses), topiramato (antiepiléptico), modafinila (medicamento usado no tratamento de distúrbios do sono);

• Hypericum perforatum, também conhecido como erva de São João, e ritonavir (antiviral);

A seguir, alguns exemplos de substâncias que podem aumentar as concentrações séricas de etinilestradiol:

• atorvastatina (medicamento para colesterol);

• ácido ascórbico (vitamina C) e o paracetamol (acetaminofeno);

• indinavir (antiviral), fluconazol (antifúngico) e troleandomicina (antibiótico).

A troleandomicina pode aumentar o risco de colestase intra-hepática (parada ou dificuldade da eliminação da bile) durante a administração concomitante com contraceptivos orais combinados.

O etinilestradiol pode interferir no metabolismo de outras drogas podendo aumentar as concentrações plasmáticas e teciduais (por exemplo: ciclosporina, teofilina, corticosteroides) ou diminuir (por exemplo: lamotrigina). Em pacientes tratados com a flunarizina (medicamento para vertigem), relatou-se que o uso de contraceptivos orais aumenta o risco de galactorreia (surgimento de leite nas mamas fora do período de amamentação). Houve relatos de gravidez quando os contraceptivos orais combinados foram coadministrados com certos antibióticos (por exemplo, ampicilina, outras penicilinas e tetraciclinas). As bulas dos medicamentos concomitantes devem ser consultadas para identificar possíveis interações.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'PRECAUÇÕES

O uso de contraceptivos orais combinados deve ser feito com acompanhamento médico. Intolerância à glicose tem sido relatada em usuárias de contraceptivos orais combinados. Por isso, pacientes com intolerância à glicose ou diabetes mellitus devem ser acompanhadas criteriosamente enquanto estiverem recebendo contraceptivos orais combinados (vide item “3. Quando não devo usar este medicamento?”).

Uma pequena parcela das usuárias de contraceptivos orais combinados pode apresentar alterações lipídicas (alteração dos níveis de colesterol). Hipertrigliceridemia (aumento dos triglicerídeos) persistente pode ocorrer em uma pequena parcela das usuárias de contraceptivos orais combinados. Elevações de triglicérides plasmáticos em usuárias de contraceptivos orais combinados podem resultar em pancreatite (inflamação do pâncreas) e outras complicações. Mulheres em tratamento para dislipidemias devem ser rigorosamente monitoradas se optarem pelo uso de contraceptivos orais combinados. Algumas mulheres podem não apresentar menstruação durante o intervalo sem comprimidos. Se o contraceptivo oral combinado não foi utilizado de acordo com as orientações antes da menstruação ou se não ocorrer duas menstruações consecutivas, deve-se interromper o uso e utilizar um método contraceptivo não hormonal de controle da natalidade até que a possibilidade de gravidez seja excluída.

Pode ocorrer sangramento de escape em mulheres em tratamento com contraceptivos orais combinados, sobretudo nos primeiros três meses de uso. Se esse tipo de sangramento persistir ou recorrer, o médico deve ser informado. Caso alguma destas alterações ocorra, o médico deve ser informado.

Mulheres utilizando contraceptivos orais combinados com história de depressão devem ser observadas criteriosamente e o medicamento deve ser suspenso se a depressão reaparecer com gravidade. As pacientes que ficarem significantemente deprimidas durante o tratamento com contraceptivos orais combinados devem interromper o uso do medicamento e utilizar um método contraceptivo alternativo, na tentativa de determinar se o sintoma está relacionado ao medicamento.

Este produto não protege contra infecção por HIV (AIDS) ou outras doenças sexualmente transmissíveis. Diarreia e/ou vômitos podem reduzir a absorção do hormônio, resultando na diminuição das concentrações séricas (no sangue). Orientação em caso de vômitos e/ou diarreia vide item 6. Como devo usar este medicamento?

GRAVIDEZ E LACTAÇÃO

Se ocorrer gravidez durante o tratamento com contraceptivo oral combinado, as próximas administrações devem ser interrompidas. Não há evidências conclusivas de que o estrogênio e o progestogênio contidos no contraceptivo oral combinado prejudicarão o desenvolvimento do bebê se houver concepção acidental durante seu uso (vide item “3. Quando não devo usar este medicamento?”).

Este medicamento não deve ser utilizado por mulheres grávidas ou que possam ficar grávidas durante o tratamento.

Pequenas quantidades de contraceptivos hormonais e/ou metabólitos foram identificadas no leite materno e poucos efeitos adversos foram relatados em lactentes, incluindo icterícia (cor amarelada da pele) e aumento das mamas. A lactação pode ser afetada pelos contraceptivos orais combinados, pois contraceptivos orais combinados podem reduzir a quantidade e alterar a composição do leite materno. Em geral, não deve ser recomendado o uso de contraceptivos orais combinados até que a lactante tenha deixado totalmente de amamentar a criança.

Este medicamento causa malformação ao bebê durante a gravidez.

ADVERTÊNCIAS

Fumar aumenta o risco de efeitos colaterais cardiovasculares graves decorrentes do uso de contraceptivos orais combinados. Este risco aumenta com a idade e com a intensidade do consumo de cigarros e é bastante acentuado em mulheres com mais de 35 anos de idade. Mulheres que tomam contraceptivos orais combinados devem ser firmemente aconselhadas a não fumar.

TROMBOEMBOLISMO E TROMBOSE VENOSA E ARTERIAL

O uso de contraceptivos orais combinados está associado a aumento do risco de eventos tromboembólicos (formação e eliminação de coágulos nos vasos sanguíneos) e trombóticos (obstrução de uma veia ou artéria). Entre os eventos relatados estão: trombose venosa profunda (obstrução de uma veia por um coágulo); embolia pulmonar (obstrução de uma veia do pulmão por um coágulo), infarto do miocárdio e acidentes vasculares cerebrais (conhecido como derrame), ataque isquêmico transitório (paciente apresenta sintomas de derrame que duram menos de 24 horas).

O risco para tais eventos é ainda maior em mulheres com condições predisponentes para tromboembolismo e trombose venosos.

A seguir, exemplos de condições predisponentes para tromboembolismo e trombose venosa e arterial:

• obesidade;

• cirurgia ou trauma com maior risco de trombose;

• parto recente ou aborto no segundo trimestre;

• imobilização prolongada;

• idade avançada;

• tabagismo, fumo;

• hipertensão (pressão alta);

• dislipidemias (aumento do colesterol no sangue).

O risco de acidente vascular cerebral (“derrame”) pode ser maior em usuárias de contraceptivo oral combinado que sofrem de enxaqueca (particularmente enxaqueca com aura, sensações ou mal estar que antecedem crises de enxaqueca).

LESÕES OCULARES

Houve relatos de casos de trombose vascular retiniana (obstrução de um vaso do olho) com o uso de contraceptivos orais combinados, que podem resultar em perda total ou parcial da visão. Se houver sinais ou sintomas de alterações visuais, início de proptose (olho saltado para fora) ou diplopia (visão dupla), papiledema (edema, inchaço, do nervo do olho) ou lesões vasculares retinianas (dos vasos da retina), deve-se interromper o uso dos contraceptivos orais combinados e avaliar imediatamente a causa.

PRESSÃO ARTERIAL

Aumento da pressão arterial tem sido relatado em mulheres em uso de contraceptivos orais combinados.

Em mulheres com hipertensão (pressão alta), histórico de hipertensão ou doenças relacionadas à hipertensão (incluindo algumas doenças renais), pode ser preferível utilizar outro método de controle da natalidade. Se contraceptivos orais combinados forem usados nestes casos, um acompanhamento rigoroso deve ser feito, caso ocorra aumento significativo da pressão arterial, deve-se interromper o uso do contraceptivo oral combinado. Aumento da pressão arterial associado ao uso de contraceptivo oral combinado, geralmente retorna aos valores basais (normais) com a interrupção do uso.

O uso de contraceptivo oral combinado é contraindicado em mulheres com hipertensão não controlada.

CÂNCER DOS ÓRGÃOS REPRODUTORES

Câncer de colo de útero

O fator de risco mais importante para o câncer cervical, de colo de útero, é a infecção pelo papiloma vírus humano. Alguns estudos sugerem que o uso de contraceptivo oral combinado pode estar associado ao aumento do risco de câncer de colo de útero em algumas populações de mulheres. No entanto, ainda há controvérsia sobre o grau em que essas descobertas podem estar relacionadas a diferenças de comportamento sexual e outros fatores. Nos casos de sangramento genital anormal não diagnosticado, estão indicadas medidas diagnósticas adequadas.

Câncer de mama

Os fatores de risco estabelecidos para o desenvolvimento do câncer de mama incluem aumento da idade, histórico familiar, obesidade, mulheres que nunca tiveram filhos e idade tardia para a primeira gravidez. Um estudo mostrou que o risco de diagnóstico de câncer de mama foi ligeiramente maior em mulheres que utilizaram contraceptivos orais combinados do que nas que nunca utilizaram. O aumento do risco desaparece gradualmente no transcorrer de 10 anos após a interrupção do uso de contraceptivos orais combinados. O padrão observado de aumento do risco de diagnóstico de câncer de mama pode ser consequência da detecção mais precoce desse câncer em usuárias de contraceptivos orais combinados, dos efeitos biológicos dos contraceptivos orais combinados ou uma combinação de ambos.

Neoplasia hepática/doença hepática/hepatite C

Os tumores (câncer) hepáticos, em casos extremamente raros, podem estar associados ao uso de contraceptivo oral combinado. O risco parece aumentar com o tempo de uso do contraceptivo oral combinado. Mulheres com história de colestase (doença que compromete a produção da bile, o fígado e a vesícula biliar) relacionada ao contraceptivo oral combinado, e as que desenvolveram colestase durante a gravidez são mais propensas a apresentar essa condição, colestase com o uso de contraceptivo oral combinado. Essas pacientes que usam contraceptivo oral combinado devem ser rigorosamente monitoradas, e o uso de contraceptivo oral combinado deve ser interrompido se a colestase recorrer.

Foi relatada lesão das células do fígado com o uso de contraceptivos orais combinados. A identificação precoce da lesão associada ao uso de contraceptivo oral combinado pode reduzir a gravidade do quadro quando o contraceptivo oral combinado é descontinuado. Se a lesão for diagnosticada, a paciente deve interromper o uso do contraceptivo oral combinado, utilizar um método de controle da natalidade não hormonal e consultar seu médico. Durante os ensaios clínicos com paciente tratados para infecções por HCV com os medicamentos contendo ombitasvir/paritapevir/ritonavir e dasabuvir com ou sem ribavirina, elevações de transaminases (ALT) maiores que 5 vezes o limite superior do normal (LSN) ocorreram significativamente com mais frequência em mulheres que utilizaram medicamentos contendo etinilestradiol, como contraceptivos orais combinados.

ENXAQUECA/CEFALEIA

Início ou piora de enxaqueca, ou desenvolvimento de cefaleia (dor de cabeça) com padrão novo que seja recorrente, persistente ou grave requer a descontinuação do contraceptivo oral combinado e avaliação da causa. Mulheres que sofrem de enxaqueca, particularmente enxaqueca com aura (sensações ou mal estar que antecedem crises de enxaqueca), que fazem uso de contraceptivos orais combinados podem ter um risco aumentado de “derrame”.

IMUNE

Angioedema (edema, inchaço generalizado)

Os estrogênios exógenos podem induzir ou exacerbar os sintomas de angiodema (inchaço de todas as partes do corpo, podendo incluir as vias aéreas), particularmente em mulheres com angioedema hereditário.

INFORMAÇÕES IMPORTANTES SOBRE UM DOS COMPONENTES DO MEDICAMENTO

Atenção: Este medicamento contém AÇÚCAR, portanto deve ser utilizado com cautela em portadores de Diabetes.

INTERAÇÕES MEDICAMENTOSAS

Alguns medicamentos podem reduzir a eficácia dos contraceptivos orais quando tomados ao mesmo tempo.

Interações entre etinilestradiol (um dos hormônios presentes no levonorgestrel + etinilestradiol) e outras substâncias podem diminuir ou aumentar as concentrações séricas (no sangue) de etinilestradiol. O uso concomitante com os medicamentos contendo ombitasvir/paritaprevir/ritonavir e dasabuvir, com ou sem ribavirina, pode aumentar o risco de elevações de ALT. Portanto as usuárias de contraceptivos orais combinados devem mudar somente para um método contraceptivo alternativo (por exemplo, métodos contraceptivos somente com progestagênio ou não hormonais) antes de iniciar a terapia com medicamentos antivirais de HCV, como ombitasvir, paritaprevir, ritonavir, dasabuvir. Os contraceptivos orais combinados podem ser reiniciados 2 semanas após a conclusão do tratamento com um medicamento antiviral HCV. Concentrações séricas (no sangue) mais baixas de etinilestradiol podem causar maior incidência de sangramento de escape e irregularidades menstruais e, possivelmente, podem reduzir a eficácia do contraceptivo oral combinado. Durante o uso concomitante de produtos com etinilestradiol e substâncias que podem diminuir as concentrações séricas de etinilestradiol, recomenda-se que um método anticoncepcional não hormonal (como preservativos e espermicida) seja utilizado além da ingestão regular de levonorgestrel + etinilestradiol. No caso de uso prolongado dessas substâncias, os contraceptivos orais combinados não devem ser considerados os contraceptivos primários (principal).

Após a descontinuação das substâncias que podem diminuir as concentrações séricas de etinilestradiol, recomenda-se o uso de um método anticoncepcional não hormonal por, no mínimo, 7 dias. Em alguns casos é necessário o uso por um tempo mais prolongado do método anticoncepcional não hormonal, deste modo converse com o seu médico para que ele possa avaliar possíveis interações com outros medicamentos e/ou substâncias.

A seguir, alguns exemplos das substâncias que podem diminuir as concentrações séricas de etinilestradiol:

• qualquer substância que reduza o tempo do trânsito gastrintestinal e, portanto, a absorção do etinilestradiol;

• medicamentos como: rifampicina (medicamento usado para tratamento de tuberculose), rifabutina, barbitúricos (medicamentos utilizados em anestesias), fenilbutazona, fenitoína (antiepiléptico), dexametasona, griseofulvina (medicamento antifúngico, para tratamento de micoses), topiramato (antiepiléptico), modafinila (medicamento usado no tratamento de distúrbios do sono);

• Hypericum perforatum, também conhecido como erva de São João, e ritonavir (antiviral);

A seguir, alguns exemplos de substâncias que podem aumentar as concentrações séricas de etinilestradiol:

• atorvastatina (medicamento para colesterol);

• ácido ascórbico (vitamina C) e o paracetamol (acetaminofeno);

• indinavir (antiviral), fluconazol (antifúngico) e troleandomicina (antibiótico).

A troleandomicina pode aumentar o risco de colestase intra-hepática (parada ou dificuldade da eliminação da bile) durante a administração concomitante com contraceptivos orais combinados.

O etinilestradiol pode interferir no metabolismo de outras drogas podendo aumentar as concentrações plasmáticas e teciduais (por exemplo: ciclosporina, teofilina, corticosteroides) ou diminuir (por exemplo: lamotrigina). Em pacientes tratados com a flunarizina (medicamento para vertigem), relatou-se que o uso de contraceptivos orais aumenta o risco de galactorreia (surgimento de leite nas mamas fora do período de amamentação). Houve relatos de gravidez quando os contraceptivos orais combinados foram coadministrados com certos antibióticos (por exemplo, ampicilina, outras penicilinas e tetraciclinas). As bulas dos medicamentos concomitantes devem ser consultadas para identificar possíveis interações.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/levonorgestrel-etinilestradiol-comprimido-0-15-mg-0-03-mg', 'Vermelha', '2026-11-22T23:23:57.465Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/levonorgestrel-etinilestradiol-comprimido-0-15-mg-0-03-mg');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Colecalciferol', 'Colecalciferol', 'DePURA (colecalciferol) atua na absorção intestinal do cálcio e fósforo, fundamental para a mineralização óssea. A vitamina D3, no tecido muscular, estimula a síntese proteica, crescimento dos miócitos e transporte de cálcio e com isso apresenta efeito positivo sobre a força, volume, tônus e velocidade da contração muscular.', 'DePURA (colecalciferol) é um medicamento a base de vitamina D3 (colecalciferol), indicado para pacientes que apresentam insuficiência e deficiência de vitamina D.

Pode ser utilizado na prevenção e tratamento auxiliar na desmineralização óssea, do raquitismo, osteomalacia e prevenção no risco de quedas e fraturas.', 'Via oral.

POSOLOGIA

Dose de manutenção para manter os níveis de 25(OH)D consistentemente acima de 30 ng/mL:

• Comprimidos Revestidos 1000 UI: ingerir, por via oral, 01 a 02 comprimidos ao dia, preferencialmente próximo às refeições.

• Comprimidos Revestidos 2000 UI: ingerir, por via oral, 01 comprimido ao dia, preferencialmente próximo às refeições.

• Comprimidos Revestidos 7000 UI: ingerir, por via oral, 01 comprimido por semana, preferencialmente próximo às refeições.

Doses de ataque:

Concentração de 25(OH)D abaixo de 20 ng/mL

• Comprimidos Revestidos 7000 UI: ingerir, por via oral, 01 comprimido ao dia, preferencialmente próximo às refeições, durante seis a oito semanas ou até atingir o valor desejado.

A resposta ao tratamento com vitamina D é muito variável. Portanto, seu médico pode prescrever diferentes posologias, dependendo da patologia, do nível de vitamina D no sangue e da resposta ao tratamento. Assim, a dosagem pode variar em uma faixa terapêutica entre 1.000 a 50.000UI, com doses diárias, semanais ou mensais.

Ingerir os comprimidos com quantidade suficiente de líquido.

Este medicamento não deve ser partido, aberto ou mastigado.', 'DePURA (colecalciferol) não deve ser utilizado em pacientes que apresentem hipersensibilidade aos componentes da fórmula. É contraindicado também em pacientes que apresentam hipervitaminose D (absorção excessiva da vitamina), elevadas taxas de cálcio ou fosfato na corrente sanguínea e também em casos de má formação nos ossos.

Restrições a grupos de risco: não existem restrições ou cuidados especiais quanto ao uso do produto por pacientes idosos. Estudos têm relatado que idosos podem ter níveis mais baixos de vitamina D do que os adultos jovens, especialmente aqueles com pouca exposição solar.

Este medicamento é contraindicado para menores de 12 anos.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.', 'A ingestão excessiva de vitamina D3, causa o desenvolvimento de hipercalcemia (excesso de cálcio) e seus efeitos associados incluindo hipercalciúria (quantidade elevada de cálcio na urina), calcificação ectópica e dano cardiovascular e renal.

Na hipervitaminose D (absorção excessiva da vitamina), têm sido relatados casos de secura da boca, dor de cabeça, polidipsia (sensação de sede), poliúria (aumento no volume normal de urina), perda de apetite, náuseas, vômitos, fadiga, sensação de fraqueza, aumento da pressão arterial, dor muscular e prurido.

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também a empresa através do seu serviço de atendimento.', NULL, 'Pacientes com arteriosclerose, insuficiência cardíaca, hiperfosfatemia e insuficiência renal devem procurar orientação médica para avaliar risco/benefício da administração da vitamina D. Em caso de hipervitaminose D (absorção excessiva da vitamina), recomenda-se administrar dieta com baixa quantidade de cálcio, grandes quantidades de líquido e se necessário glicocorticoides.

Informe seu médico caso você utilize antiácidos que contenham magnésio, pois o uso concomitante com vitamina D pode resultar em hipermagnesemia. Não se recomenda o uso simultâneo de vitamina D e calcifediol, devido ao efeito aditivo e aumento do potencial tóxico. Preparações que contenham cálcio em doses elevadas ou diuréticos tiazídicos quando usados concomitantemente com vitamina D, aumentam o risco de hipercalcemia e as que contém fósforo, também em doses elevadas, aumentam o potencial de risco de hiperfosfatemia. Não há restrições específicas quanto à ingestão concomitante de alimentos. As alterações em testes laboratoriais descritas em decorrência do uso da vitamina D são:

Alguns antiepilépticos (ex.: carbamazepina, fenobarbital, fenitoína e primidona) podem aumentar a necessidade de vitamina D3. O uso concomitante de DePURA com outros produtos contendo vitamina D3 não é recomendado devido ao efeito aditivo e aumento do potencial tóxico.

• os anticonvulsionantes e os barbitúricos podem acelerar a metabolização de vitamina D3, reduzindo a sua eficácia.

• alterações endócrinas e metabólicas: a toxicidade pela vitamina D em doses elevadas acima das recomendadas incluindo a nefrocalcinose/insuficiência renal, hipertensão e psicose, podem ocorrer com o uso prolongado de colecalciferol; a hipervitaminose D é reversível com a descontinuação do tratamento ao menos que ocorra dano renal grave.

• anormalidades das gorduras do sangue: efeitos dislipidêmicos do colecalciferol, caracterizados pela redução do HDL-colesterol e aumento do LDL-colesterol, tem sido observados quando as vitaminas são administradas isoladas em mulheres pós-menopausa.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.

Informe ao seu médico ou cirurgião-dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/depura-comprimido', 'Sem Tarja', '2026-11-22T23:23:57.466Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/depura-comprimido');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Complexo de Hidróxido de Ferro III', 'Complexo de Hidróxido de Ferro III', 'Noripurum® solução oral (gotas) age combatendo a anemia e é especificamente indicado para o tratamento das anemias nutricionais e de hemácias pequenas, causadas por deficiência de ferro. O ferro de Noripurum® solução oral (gotas) apresenta-se sob a forma de um complexo com moléculas grandes, não iônico, o que confere ao preparado características vantajosas: boa absorção e bom aproveitamento pelo organismo, baixa toxicidade e boa tolerabilidade, ausência de interação com certos medicamentos (p.ex. tetraciclinas, hidróxido de alumínio), preservação do seu volume utilizável, sem perdas por elimin', 'Noripurum® é indicado para o tratamento de anemias e deficiências de ferro. Noripurum® é indicado em:

• síndromes (conjunto de sinais e sintomas) da deficiência de ferro que ainda não se manifestou ou se manifestou de maneira suave;

• anemias ferroprivas devidas a subnutrição e/ou carências alimentares tanto de qualidade quanto de quantidade;

• anemias devido à má absorção intestinal;

• anemia por deficiência de ferro (ferropriva) durante a gravidez e a amamentação;

• anemia devida a sangramentos recentes ou por períodos longos e em condições em que seja conveniente a suplementação de fatores produzidos pelo sangue.', 'Noripurum® deve ser administrado por via oral.

Noripurum® solução oral (gotas) deve ser ingerido durante ou imediatamente após as refeições.

Ao contrário do ferro iônico, o ferro de Noripurum® não mancha o esmalte dos dentes. Porém, pacientes que apresentam placa bacteriana e crianças em fase de dentição devem atentar à higiene dental de modo a evitar um escurecimento temporário da placa (semelhante ao causado por bebidas à base de cafeína). Em geral, o escurecimento da placa bacteriana é reversível após profilaxia dental em consultório odontológico.

Aos pacientes portadores de próteses dentárias, particularmente à base de "Luva Light", recomenda-se lavar a boca e escovar as próteses imediatamente após uso do preparado, a fim de evitar escurecimento delas.

Siga a orientação de seu médico, respeitando sempre os horários, as doses e a duração do tratamento. Não interrompa o tratamento sem o conhecimento do seu médico.

POSOLOGIA

A posologia e a duração da terapia dependem da extensão da deficiência de ferro.

Para uso pediátrico, a dose diária de ferro a administrar é calculada conforme a gravidade do caso numa faixa de 2,5 a 5,0 mg por kg de peso. Esta dose pode ser aumentada ou diminuída a critério médico e pode ser administrada em uma ou mais tomadas conforme o volume total resultante.

Para fins de cálculo, note-se que o teor férrico de Noripurum® solução oral (gotas) é: 1 ml (20 gotas) = 50 mg.

Para deficiência de ferro manifesta, a terapia deve durar cerca de três a cinco meses até a normalização dos valores de hemoglobina. Posteriormente, a terapia deve prosseguir por várias semanas (cerca de dois a três meses) com uma dose igual à indicada para deficiência de ferro latente a fim de restaurar a reserva de ferro.

Para a deficiência de ferro manifesta durante a gravidez, a terapia deve ser mantida até a normalização dos valores de hemoglobina. Posteriormente, a terapia deve prosseguir para reposição das reservas de ferro pelo menos até o término da gravidez com dose igual à indicada para a deficiência de ferro latente.

Para a deficiência de ferro latente, a terapia deve durar cerca de um a dois meses. Como posologia média sugere-se:

Deficiência de ferro latente (profilaxia da anemia)

Deficiência de ferro manifesta (tratamento da anemia)*

Prematuros

1-2 gotas/kg peso corporal por 3-5 meses

(2,5 - 5 mg de ferro)

Crianças de até 1 ano

6-10 gotas/dia

(15-25 mg de ferro)

10-20 gotas/dia

(25-50 mg de ferro)

Crianças de 1 a 12 anos

10-20 gotas/dia

(25-50 mg de ferro)

20-40 gotas/dia

(50-100 mg de ferro)

Maiores de 12 anos, adultos e lactantes

20-40 gotas/dia

(50-100 mg de ferro)

40-120 gotas/dia

(100-300 mg de ferro)

Mulheres grávidas

40 gotas/dia

(100 mg de ferro)

80-120 gotas/dia

(200-300 mg de ferro)

Em casos mais graves, Noripurum® Parenteral (IM ou EV) pode ser utilizado como tratamento inicial, conforme critério médico.

Dose (mg) tratamento

Dose (mg) profilaxia

%IDR*

tratamento

%IDR*

profilaxia

Lactentes e crianças de até 1 ano

50

25

18.518% - 556%

9.259% - 278%

Crianças (1-12 anos)

100

50

1.667% - 1111%

833% - 556%

Gestantes

300

100

1.111%

370%

Lactantes

300

100

2.000%

667%

Adultos

300

100

2.143%

714%

*IDR = Ingestão Diária Recomendada. Cálculos com base na dose diária máxima recomendada em bula.

MÉTODO DE ADMINISTRAÇÃO

A dose diária pode ser administrada de uma vez ou pode ser dividida em doses separadas. Noripurum® solução oral (gotas) deve ser administrado durante ou imediatamente após as refeições.

Para facilitar a administração, a dose pode ser misturada com mingau, suco de frutas ou verduras ou leite, uma vez que Noripurum® solução oral (gotas) não sofre redução apreciável da absorção intestinal, como ocorre com outros sais de ferro. Uma possível leve coloração não afeta o gosto e nem a eficácia do produto.

Observação: No tratamento de ferropenias em prematuros e pacientes idosos recomenda-se o uso de Noripurum® solução oral (gotas) que, além da sua excelente tolerabilidade, oferece a vantagem de permitir uma dose exata e facilmente fracionável.

Atenção: o frasco de Noripurum® solução oral (gotas) vem acompanhado de uma tampa de segurança e um gotejador mais moderno e de fácil manuseio:

Não administre medicamentos diretamente na boca das crianças, utilize uma colher para pingar as gotinhas.', 'O produto não deve ser usado por pacientes alérgicos a medicamentos à base de ferro ou a qualquer um dos componentes do produto, sobrecarga de ferro tais como hemocromatose (doença causada devido ao aumento da absorção de ferro, secundário à sobrecarga de ferro utilizada) e hemosiderose (acúmulo de ferro nos tecidos e órgãos), com doenças hepáticas (do fígado) agudas, com doenças gastrintestinais (como diarreias crônicas e retocolite ulcerativa), com anemias não causadas por deficiência de ferro (tais como anemia falciforme, hemolítica ou megaloblástica devido à deficiência de vitamina B12) ou com incapacidade de utilização do ferro (tais como anemia causada por envenenamento por chumbo, anemia sídero-acréstica, talassemia).

Informe seu médico caso se tenha submetido a transfusões sanguíneas repetidas na vigência do uso do produto.', 'Efeitos indesejáveis de estudos clínicos (pré e pós-registro, incluindo estudos de segurança pós-registro).

A segurança e a tolerabilidade da ferripolimaltose foram avaliadas em 24 artigos publicados na literatura científica ou relatórios de estudos clínicos que abrangeram um total de 1473 pacientes expostos.

As principais reações adversas medicamentosas que foram relatadas nestes estudos são:

Reação muito comum (≥1/10): fezes escurecidas*;

Reação comum (≥ 1/100 e < 1/10): diarreia, náusea, dor abdominal, constipação;

Reação incomum (≥ 1/1000 e < 1/100): vômito, escurecimento de dente, gastrite, rash4,5 (exantema, erupção cutânea, rash cutâneo), urticária, prurido (coceira), eritema (vermelhidão da pele), cefaleia (dor de cabeça);

Reação rara > 1/10.000 e ≤ 1/1.000: espasmos musculares, mialgia (dor muscular).

Inclui: dor abdominal, dispepsia, desconforto epigástrico, distensão abdominal.

Inclui: vômitos, regurgitação.

Inclui: contração muscular involuntária, tremor.

Inclui: erupção cutânea, erupção cutânea macular, erupção cutânea vesicular.

Eventos originários de Relatórios Espontâneos Pós-comercialização, incidência estimada de <1/491 pacientes (limite superior do intervalo de confiança de 95%).

*As fezes escurecidas são uma reação adversa bem conhecida dos medicamentos orais por ferro, no entanto, isso não tem relevância clínica. Outros efeitos colaterais comumente vistos foram distúrbios gastrointestinais (náuseas, constipação, diarreia).

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também a empresa através do seu serviço de atendimento.', 'A ingestão excessiva de álcool causa aumento do depósito de ferro no fígado e aumenta a probabilidade de efeitos adversos e até tóxicos do ferro quando em uso prolongado.

As interações da ferripolimaltose com tetraciclina ou hidróxido de alumínio foram investigadas em três estudos em humanos (desenho cruzado, 22 pacientes por estudo). Não foi observada redução significativa na absorção de tetraciclina. A concentração plasmática de tetraciclina não reduziu abaixo do nível da concentração inibitória mínima necessária para o nível sérico bacteriostático. A absorção de ferro da ferripolimaltose não foi reduzida pelo hidróxido de alumínio ou tetraciclina. A ferripolimaltose pode, portanto, ser administrada ao mesmo tempo que tetraciclina ou outros compostos fenólicos, bem como com hidróxido de alumínio.

Estudos em ratos com tetraciclina, hidróxido de alumínio, acetilsalicilato, sulfassalazina, carbonato de cálcio, acetato de cálcio e fosfato de cálcio em combinação com a vitamina D3, bromazepam, aspartato de magnésio, D-penicilamina, metildopa, paracetamol e auranofina não mostraram nenhuma interação com a ferripolimaltose.

Do mesmo modo, não foram observadas interações com ingredientes de alimentos tais como ácido fítico, ácido oxálico, tanino, alginato de sódio, colina e sais de colina, vitamina A, vitamina D3 e vitamina E, óleo de soja e farinha de soja em estudos in vitro com ferripolimaltose. Estes resultados sugerem que a ferripolimaltose pode ser tomada durante ou imediatamente após a ingestão de alimentos.

O teste de sangue oculto nas fezes (seletivo para Hb) para a detecção de sangue oculto não é prejudicado e, portanto, não há necessidade de interromper a terapia.

A administração concomitante de ferro parenteral e oral não é recomendada, uma vez que a absorção de ferro oral seria reduzida.

Informe ao seu médico ou cirurgião dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'Como todos os preparados férricos, Noripurum® deve ser administrado com cautela na presença de alcoolismo, hepatite, infecções agudas e estados inflamatórios do trato gastrintestinal (enterites, colite ulcerativa), pancreatite e úlcera péptica.

A administração do produto a pacientes submetidos a repetidas transfusões de sangue deve ser realizada sob rigoroso controle médico e observação do quadro sanguíneo, visto que a concomitância da aplicação de sangue com alto nível de ferro eritrocitário e sais de ferro por via oral pode resultar em sobrecarga férrica.

Em casos de anemias associadas a infecções ou neoplasias, o ferro substituído é armazenado no sistema retículo-endotelial, de onde é mobilizado e utilizado somente após a cura da doença primária. Assim, recomenda-se uma avaliação de risco/benefício.

Atenção diabéticos: contém açúcar.

Cada 1 ml de Noripurum contém 50 mg de sacarose.

Aos pacientes portadores de próteses dentárias, particularmente à base de “Luva Light” recomenda-se lavar a boca e escovar as próteses imediatamente após o uso do preparado, a fim de evitar o escurecimento delas.

Como outros compostos de ferro, Noripurum® provoca coloração escura das fezes, fato este sem qualquer importância clínica.

GRAVIDEZ E LACTAÇÃO

Não existem dados de estudos clínicos disponíveis sobre o uso de ferripolimaltose em mulheres grávidas durante o primeiro trimestre. Até o momento, não houve relatos de reações adversas graves após o uso de ferripolimaltose em doses terapêuticas para o tratamento da anemia na gravidez. Estudos de reprodução em animais não revelaram evidência de risco ao feto ou à mãe. Estudos controlados em mulheres grávidas após o primeiro trimestre de gravidez não revelaram nenhum efeito adverso para a mãe ou o neonato. Portanto, é improvável uma influência negativa sobre o feto.

O ferro ligado em lactoferrina passa para o leite materno em pequenas quantidades, mas é improvável a ocorrência de efeitos adversos ao lactente.

Como medida de precaução, as mulheres em idade fértil e mulheres durante a gravidez só devem usar Noripurum® depois de consultar um médico. É recomendável uma avaliação benefício/risco.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.

EFEITOS NA CAPACIDADE DE DIRIGIR E OPERAR MÁQUINAS

Não foram realizados estudos relevantes. No entanto, é improvável que o Noripurum® tenha qualquer efeito sobre a capacidade de conduzir veículos e usar máquinas.

INTERFERÊNCIA EM EXAMES LABORATORIAIS

Noripurum® pode causar fezes escurecidas. Fezes escuras podem ocultar o sangramento do trato gastrointestinal visualmente. No entanto, o teste de hemocultura (seletivo para hemoglobina) para a detecção de sangue oculto não é prejudicado e, portanto, não há necessidade de interromper a terapia.

INTERAÇÕES MEDICAMENTOSAS

A ingestão excessiva de álcool causa aumento do depósito de ferro no fígado e aumenta a probabilidade de efeitos adversos e até tóxicos do ferro quando em uso prolongado.

As interações da ferripolimaltose com tetraciclina ou hidróxido de alumínio foram investigadas em três estudos em humanos (desenho cruzado, 22 pacientes por estudo). Não foi observada redução significativa na absorção de tetraciclina. A concentração plasmática de tetraciclina não reduziu abaixo do nível da concentração inibitória mínima necessária para o nível sérico bacteriostático. A absorção de ferro da ferripolimaltose não foi reduzida pelo hidróxido de alumínio ou tetraciclina. A ferripolimaltose pode, portanto, ser administrada ao mesmo tempo que tetraciclina ou outros compostos fenólicos, bem como com hidróxido de alumínio.

Estudos em ratos com tetraciclina, hidróxido de alumínio, acetilsalicilato, sulfassalazina, carbonato de cálcio, acetato de cálcio e fosfato de cálcio em combinação com a vitamina D3, bromazepam, aspartato de magnésio, D-penicilamina, metildopa, paracetamol e auranofina não mostraram nenhuma interação com a ferripolimaltose.

Do mesmo modo, não foram observadas interações com ingredientes de alimentos tais como ácido fítico, ácido oxálico, tanino, alginato de sódio, colina e sais de colina, vitamina A, vitamina D3 e vitamina E, óleo de soja e farinha de soja em estudos in vitro com ferripolimaltose. Estes resultados sugerem que a ferripolimaltose pode ser tomada durante ou imediatamente após a ingestão de alimentos.

O teste de sangue oculto nas fezes (seletivo para Hb) para a detecção de sangue oculto não é prejudicado e, portanto, não há necessidade de interromper a terapia.

A administração concomitante de ferro parenteral e oral não é recomendada, uma vez que a absorção de ferro oral seria reduzida.

Informe ao seu médico ou cirurgião dentista se você está fazendo uso de algum outro medicamento. Não use medicamento sem o conhecimento do seu médico. Pode ser perigoso para a sua saúde.', 'https://www.bulas.med.br/bula/noripurum-gotas', 'Sem Tarja', '2026-11-22T23:23:57.469Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/noripurum-gotas');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Carbonato de Cálcio + Colecalciferol', 'Carbonato de Cálcio + Colecalciferol', 'Oscal 500 + D repõe o cálcio e vitamina D (que auxilia na absorção de cálcio) quando existe a falta desse no organismo.', 'Prevenção ou tratamento auxiliar na desmineralização (perda ou diminuição de constituintes minerais de organismos e tecidos individuais, especialmente do osso) óssea pré e pós menopausa (última menstruação).', 'POSOLOGIA

Adultos: 1 a 2 comprimidos ao dia;

Crianças: 1 comprimido ao dia.

Você deve tomar os comprimidos com líquido, por via oral durante as refeições ou conforme orientação médica.

Doses maiores devem ser ingeridas de acordo com a prescrição do seu médico.

Ingestão Diária Recomendada (IDR) de Oscal 500 + D:

Posologia diária recomendada
% IDR*

Crianças
Adultos

1 a 3 anos
4 a 6 anos
7 a 10 anos

Cálcio
1 comprimido

(100%)
1 comprimido

(83%)
1 comprimido

(71%)
1 ou 2 comprimidos

(50% – 100%)

Vitamina D
1 comprimido

(200%)
1 comprimido

(200%)
1 comprimido

(200%)
1 ou 2 comprimidos

(200% – 400%)

Não há estudos dos efeitos de Oscal 500 + D administrado por vias não recomendadas. Portanto, por segurança e para eficácia deste medicamento, a administração deve ser somente pela via oral.

Siga corretamente o modo de usar. Em caso de dúvidas sobre este medicamento, procure orientação do farmacêutico. Não desaparecendo os sintomas, procure orientação de seu médico ou cirurgião-dentista.

Este medicamento não deve ser partido ou mastigado.', '• Alergia aos componentes da fórmula;

• Hipercalcemia (aumento dos níveis de cálcio no sangue);

• Sarcoidose (doença inflamatória que pode acometer órgãos como os pulmões, fígado e gânglios linfáticos);

• Hipercalciúria (eliminação de cálcio na urina);

• Doenças e/ou condições que levam à hipercalcemia e/ou hipercalciúria (por exemplo, nefrocalcinose – deposição de cálcio nos rins, mieloma – tumor formado por células normalmente encontradas na medula óssea, metástases ósseas, hiperparatiroidismo primário – sinais e sintomas decorrentes do aumento de cálcio no sangue, na urina, perda óssea de cálcio e pedras nos rins, sarcoidose – doença inflamatória que pode acometer órgãos como os pulmões, fígado e gânglios linfáticos), imobilização prolongada acompanhada por hipercalciúria e/ou hipercalcemia);

• Hipervitaminose D;

• Uso concomitante com agentes digitálicos (vide “Interações medicamentosas”).

Este medicamento é contraindicado para uso por pacientes com problemas graves nos rins.', 'A seguinte classificação de frequência do CIOMS é usada, quando aplicável:

Categoria
Frequência

Muito comum
≥ 10%

Comum
≥ 1% e < 10%

Incomum
≥ 0,1% e < 1%

Raro
≥ 0,01% e < 0,1%

Muito raro
< 0,01%

Desconhecida
Não pode ser estimada pelos dados disponíveis

Distúrbios do sistema imunológico

• Desconhecida: hipersensibilidade (alergia ou intolerância).

Distúrbios metabólicos e nutricionais

• Desconhecida: hipercalcemia (aumento nos níveis de cálcio no sangue), hipercalciúria (eliminação de cálcio na urina).

Distúrbios gastrintestinais

• Desconhecida: náusea, diarreia, dor abdominal, constipação, flatulência (excesso de gases no estômago ou intestinos).

Distúrbios renais e urinários

• População especial: Paciente com insuficiência renal

• Desconhecida: risco potencial de hiperfosfatemia (nível anormalmente elevado de fosfato no sangue), nefrolitíase (presença de pedras nos rins) e nefrocalcinose (deposição de cálcio nos rins).

Informe ao seu médico, cirurgião-dentista ou farmacêutico o aparecimento de reações indesejáveis pelo uso do medicamento. Informe também a empresa através do seu serviço de atendimento.', NULL, 'ADVERTÊNCIAS

Durante o tratamento a longo prazo com Oscal® 500 + D, os níveis de cálcio sérico devem ser controlados e a função renal deve ser monitorizada através de medidas da creatinina sérica. A monitorização é especialmente importante em

idosos, em pacientes sob tratamento concomitante com diuréticos tiazídicos (vide “Interações medicamentosas”) e em pacientes com uma elevada tendência para formação de cálculos.

Em caso de hipercalcemia ou sinais de insuficiência renal, a dose deve ser reduzida ou o tratamento descontinuado.

A ingestão de cálcio e álcalis de outras fontes (alimentos, alimentos enriquecidos ou outros medicamentos) deve ser observada quando Oscal® 500 + D for prescrito. Se doses elevadas de cálcio forem administradas concomitante com agentes alcalinos (como carbonatos), isso pode levar a uma síndrome láctea-alcalina (síndrome de Burnett) associada à hipercalcemia, alcalose metabólica, insuficiência renal e calcificação dos tecidos moles.

Os níveis de cálcio devem ser monitorados no soro e na urina quando doses elevadas são administradas.

PRECAUÇÕES

Oscal® 500 + D deve ser usado sob supervisão médica caso você tenha:

• Insuficiência renal (problemas nos rins) crônica;

• Histórico de formação de cálculos renais;

• Doença cardiovascular.

Recomenda-se supervisão médica para uso de Oscal® 500 + D em crianças.

A vitamina D deve ser usada com cautela caso você apresente comprometimento da função renal e o efeito nos níveis de cálcio e fosfato deve ser monitorado. O risco de calcificação dos tecidos moles deve ser levado em consideração. Em pacientes com insuficiência renal grave, a vitamina D na forma de colecalciferol, não é metabolizada normalmente e outras formas de vitamina D devem ser usadas.

O conteúdo de Oscal® 500 + D deve ser considerado na prescrição de outros medicamentos contendo vitamina D.

Doses adicionais de cálcio ou vitamina D devem ser tomadas sob rigorosa supervisão médica. Nesses casos, é necessário monitorar frequentemente os níveis séricos de cálcio e a excreção urinária de cálcio.

Oscal® 500 + D deve ser usado com cautela caso você esteja imobilizado com osteoporose devido ao aumento do risco de hipercalcemia.

Informe seu médico para que ele realize o controle de hipercalciúria e, se necessário, reduza a dose ou interrompa o tratamento.

Caso você tenha ausência ou redução dos níveis de ácido clorídrico no estômago, a absorção de cálcio pode estar reduzida, sendo assim, tome Oscal 500 + D durante as refeições.

PACIENTES IDOSOS

O uso prolongado de cálcio pode provocar prisão de ventre.

GRAVIDEZ E LACTAÇÃO

Oscal® 500 + D pode ser usado durante a gravidez em caso de deficiência de cálcio e vitamina D. Durante a gravidez, a ingestão diária não deve exceder as doses diárias recomendadas de cálcio e vitamina D.

A supervisão médica é recomendada para o uso de Oscal® 500 + D na gravidez.

Foi demonstrado que doses excessivas de vitamina D apresenta efeitos teratogênicos em experimentos com animais.

Em mulheres grávidas, a superdose de cálcio e vitamina D deve ser evitada, uma vez que a hipercalcemia prolongada tem sido por vezes associada a um atraso no desenvolvimento físico e mental, estenose aórtica supravalvular e retinopatia na criança.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica.

Oscal® 500 + D pode ser usado durante a amamentação sob supervisão médica rigorosa. A vitamina D e seus metabólitos são excretados no leite materno. Isso deve ser considerado quando se administra vitamina D adicional à criança.

EFEITOS NA HABILIDADE DE DIRIGIR E USAR MÁQUINAS

Não existem dados disponíveis sobre o efeito na capacidade de dirigir e utilizar máquinas.

INFORMAÇÕES IMPORTANTES SOBRE UM DOS COMPONENTES DO MEDICAMENTO

Pacientes com problemas hereditários raros de intolerância à frutose, mal-absorção de glucose-galactose ou insuficiência de sacarose-isomaltase não devem tomar este medicamento.

Atenção diabéticos: contém AÇÚCAR.

INTERAÇÕES MEDICAMENTOSAS

INTERAÇÕES MEDICAMENTO–MEDICAMENTO

Diuréticos tiazídicos: reduzem a excreção urinária de cálcio. Devido ao aumento do risco de hipercalcemia, o cálcio sérico deve ser monitorizado regularmente durante o uso concomitante com diuréticos tiazídicos.

Corticosteróides sistêmicos: reduzem a absorção de cálcio. Além disso, o efeito da vitamina D pode ser diminuído.

Cetoconazol: a absorção e, portanto, a eficácia do cetoconazol será diminuída pela ingestão concomitante de Oscal® 500 + D.

Tetraciclinas: a administração concomitante de carbonato de cálcio e tetraciclinas deve ser evitada devido à absorção prejudicada destes medicamentos. As tetraciclinas devem ser tomadas pelo menos 2 a 3 horas após a ingestão do cálcio.

Quinolonas: a absorção de antibióticos quinolonas pode ser prejudicada se administrada concomitantemente com o cálcio. Os antibióticos quinolona devem ser tomados duas horas antes ou seis horas após a ingestão de cálcio.

Medicamento contendo ferro: a absorção e, portanto, a eficácia é reduzida como alguns outros medicamentos (por exemplo, ferro), com o uso concomitante de Oscal® 500 + D. Os medicamentos contendo ferro devem ser tomados pelo menos 2 a 3 horas após a ingestão de cálcio.

Medicamentos contendo bifosfonato: a administração concomitante de Oscal® 500 + D e bifosfonatos deve ser evitada, devido ao prejuízo na absorção destes medicamentos. Os bisfosfonatos devem ser administrados pelo menos 2 a 3 horas após a ingestão Oscal® 500 + D.

Resinas de troca iônica: o tratamento simultâneo com resinas de troca iônica, como a colestiramina, pode reduzir a absorção gastrintestinal da vitamina D. Portanto, recomenda-se um intervalo de tempo o maior possível entre as ingestões.

Levotiroxina: a eficácia da levotiroxina pode estar reduzida pelo uso concomitante de cálcio, devido à diminuição da absorção de levotiroxina. A administração de cálcio e levotiroxina deve ser separada por pelo menos quatro horas.

Rifampicina: o tratamento concomitante com rifampicina pode diminuir o efeito da vitamina D3 devido à ativação metabólica.

Medicamentos digitálicos: o uso concomitante de cálcio e glicosídeos cardíacos (medicamentos digitálicos) é contraindicado devido ao risco aumentado de arritmia (vide “Contraindicações”).

Bloqueadores de canais de cálcio: a administração de altas doses de cálcio pode reduzir o efeito dos bloqueadores dos canais de cálcio.

Fenitoína, barbitúricos: o tratamento concomitante com fenitoína ou barbitúricos pode diminuir o efeito de Oscal® 500 + D devido à ativação metabólica.

Fluoreto de sódio: a absorção e, portanto, a eficácia do fluoreto de sódio será diminuída pela ingestão concomitante de

Oscal® 500 + D.

Estramustina: a absorção e, portanto, a eficácia da estramustina será diminuída pela ingestão concomitante de Oscal® 500 + D.

INTERAÇÕES MEDICAMENTO–ALIMENTO

O ácido fítico e o ácido oxálico podem inibir a absorção de cálcio através da formação de compostos insolúveis com íons cálcio. O paciente não deve ingerir produtos de cálcio dentro de duas horas após ingerir alimentos ricos em ácido fítico e ácido oxálico.

Fibras: as fibras podem modificar a absorção intestinal de cálcio.

INTERAÇÕES MEDICAMENTO–SUBSTÂNCIAS QUÍMICAS

Cálcio e álcool, cafeína ou tabaco: o consumo excessivo destas substâncias pode reduzir a quantidade de cálcio absorvida.

INTERAÇÕES MEDICAMENTO–EXAME LABORATORIAL

Não há dados disponíveis até o momento sobre a interferência deste medicamento em testes laboratoriais. Informe ao seu médico se você está fazendo uso de algum outro medicamento.', 'https://www.bulas.med.br/bula/oscal-d', 'Sem Tarja', '2026-11-22T23:23:57.470Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/oscal-d');
  INSERT INTO public.bulario (nome, principio_ativo, resumo, indicacoes, posologia, contraindicacoes, efeitos_adversos, interacoes, precaucoes, fonte, tarja, data_extracao) SELECT 'Fosfato de Oseltamivir', 'Fosfato de Oseltamivir', 'O fosfato de oseltamivir é um pró-fármaco do carboxilato de oseltamivir, inibidor potente e seletivo das enzimas neuraminidase do vírus da gripe, que são glicoproteínas encontradas na superfície do vírion. A atividade da enzima viral neuraminidase é importante principalmente para a liberação de partículas virais recém-formadas nas células infectadas e para a posterior disseminação do vírus infeccioso no organismo. Sugere-se também que a neuraminidase pode desempenhar um papel importante na entrada do vírus nas células não infectadas.', 'Tamiflu® é indicado para tratamento e profilaxia de gripe em adultos e crianças com idade superior a 1 ano.

Tamiflu® não substitui a vacina contra a gripe.', 'Tamiflu® cápsulas deve ser administrado por via oral e pode ser administrado com ou sem alimento. Porém, a administração com alimento pode aumentar a tolerabilidade em alguns pacientes.

O tratamento deve ser iniciado nas primeiras 48 horas após o início dos sintomas.

POSOLOGIA

DOSAGEM PADRÃO

Tratamento da gripe

O tratamento deve ser iniciado dentro do primeiro ou segundo dia do aparecimento dos sintomas de gripe.

• Adultos e adolescentes: A dose oral recomendada de Tamiflu® a adultos e adolescentes, com 13 anos de idade ou mais é de 75 mg, duas vezes ao dia, por cinco dias.

• Crianças entre 1 e 12 anos de idade: Dose recomendada de Tamiflu® a crianças com idade entre 1 e 12 anos:

PESO CORPORAL

TRATAMENTO POR CINCO DIAS

≤ 15 kg

30 mg, duas vezes ao dia

> 15 a 23 kg

45 mg, duas vezes ao dia

> 23 a 40 kg

60 mg, duas vezes ao dia

> 40 kg

75 mg*, duas vezes ao dia

*Crianças com peso superior a 40 kg devem receber dose adulto; se conseguem ingerir cápsulas podem receber tratamento com cápsulas de 75 mg, duas vezes ao dia, ou uma cápsula de 30 mg e uma de 45 mg, concomitantemente, duas vezes ao dia, por cinco dias.

Profilaxia da gripe

• Adultos e adolescentes: A dose oral recomendada de Tamiflu® para a profilaxia da gripe após contato próximo com um indivíduo infectado, é de 75 mg, uma vez ao dia, durante 10 dias. A terapia deve ser iniciada dentro de até dois dias após a exposição. A dose recomendada para profilaxia em caso de surto comunitário de gripe é de 75 mg, uma vez ao dia. A segurança e a eficácia foram demonstradas por até seis semanas de uso contínuo. A proteção é mantida enquanto se continua a administração da medicação.

• Crianças entre 1 e 12 anos de idade: Dose profilática recomendada de Tamiflu® a crianças com idade entre 1 e 12 anos:

PESO CORPORAL

PROFILAXIA POR DEZ DIAS*

≤ 15 kg

30 mg, uma vez ao dia

> 15 a 23 kg

45 mg, uma vez ao dia

> 23 a 40 kg

60 mg, uma vez ao dia

> 40 kg

75 mg**, uma vez ao dia

* Ou por tempo prolongado de acordo com orientação médica.

** Crianças com peso superior a 40 kg devem receber dose de adulto, se conseguem ingerir cápsulas, podem receber tratamento profilático com cápsulas de 75 mg, uma vez ao dia, ou uma cápsula de 30 mg e uma de 45 mg, concomitantemente, uma vez ao dia, por 10 dias.

Seguir as instruções abaixo a fim de garantir a correta dosagem, utilizando cápsulas de 30, 45 ou 75 mg:

Adultos, adolescentes ou crianças que não conseguem ingerir cápsulas podem receber doses apropriadas de Tamiflu® abrindo as cápsulas e transferindo todo o conteúdo para uma pequena quantidade (no máximo 1 colher de chá) de alimentos adocicados, tais como calda de chocolate, mel (apenas para crianças com 2 anos de idade ou mais velhas), açúcar mascavo ou refinado dissolvido em água, cobertura de sobremesa, leite condensado, calda de frutas ou iogurte, para mascarar o sabor amargo (veja as instruções de preparo abaixo).

Determine o número de cápsulas necessárias para o preparo da mistura.

PESO CORPORAL*

QUANTIDADE DE CÁPSULAS NECESSÁRIAS PARA OBTER A DOSE RECOMENDADA PARA O TRATAMENTO

QUANTIDADE DE CÁPSULAS NECESSÁRIAS PARA OBTER A DOSE RECOMENDADA PARA PROFILAXIA

≤ 15 Kg

1 cápsula de 30 mg, duas vezes ao dia

1 cápsula de 30 mg, uma vez ao dia

> 15 a 23 Kg

1 cápsula de 45 mg, duas vezes ao dia

1 cápsula de 45 mg, uma vez ao dia

> 23 a 40 Kg

2 cápsulas de 30 mg, duas vezes ao dia

2 cápsulas de 30 mg, uma vez ao dia

> 40 Kg

1 cápsula de 75 mg, duas vezes ao dia

1 cápsula de 75 mg, uma vez ao dia

Verifique se você está usando a dose correta de acordo com a tabela acima. Pegue a cápsula sobre um recipiente e cuidadosamente abra a cápsula e verta todo o conteúdo no recipiente.

Adicione uma pequena quantidade de alimento adocicado apropriado (máximo 1 colher de chá), à mistura, a fim de mascarar o gosto amargo, e misture bem.

Agite essa mistura e administre todo o conteúdo para o paciente. Essa mistura deve ser administrada imediatamente após o preparo.

Repita esse procedimento para cada dose que será administrada.

INSTRUÇÕES ESPECIAIS DE DOSAGEM

Uso geriátrico: Não é necessário ajuste de dose para pacientes idosos, tanto para o tratamento quanto para a profilaxia da gripe.

Uso pediátrico: A segurança e a eficácia de Tamiflu® em crianças abaixo de 1 ano de idade ainda não foram estabelecidas e, portanto, este medicamento não deve ser utilizado para essa faixa etária.

Pacientes com insuficiência renal:

• Tratamento da gripe: não são necessários ajustes de dose para pacientes com depuração de creatinina superior a 60 mL/min. Em pacientes com depuração de creatinina de > 30–60 mL/min, é recomendado que a dose seja reduzida para 30 mg de Tamiflu®, duas vezes ao dia, durante 5 dias. Para pacientes com depuração de creatinina entre 10 e 30 mL/min, recomenda-se que a dose seja reduzida para uma cápsula de 30 mg de Tamiflu®, uma vez ao dia, durante cinco dias, ou, para crianças, doses de acordo com o peso corporal, uma vez por dia, durante cinco dias. Em pacientes submetidos à hemodiálise, uma dose inicial de 30 mg de Tamiflu® pode ser administrada antes do início da diálise se os sintomas de gripe aparecerem dentro de 48 horas entre as sessões de diálise. Para manter a concentração plasmática em níveis terapêuticos, a dose de 30 mg deve ser administrada após cada sessão de hemodiálise. Para diálise peritoneal, a dose de 30 mg de Tamiflu® administrada antes do início da diálise seguida de doses de 30 mg adicionais administradas a cada 5 dias é recomendada para tratamento (vide item “3. Características Farmacológicas - Farmacocinética em situações clínicas especiais”). A farmacocinética de oseltamivir não foi estudada em pacientes com doença renal terminal (isto é, depuração de creatinina inferior a 10 mL/min) não submetidos a diálise. Desta forma, não é possível recomendar dose para esse grupo de pacientes.

• Profilaxia da gripe: não são necessários ajustes de doses para pacientes com depuração de creatinina superior a 60 mL/min. Em pacientes com depuração de creatinina de >30 – 60 mL/min, é recomendado que a dose seja reduzida para 30 mg de Tamiflu® uma vez ao dia. Para pacientes com depuração de creatinina entre 10 e 30 mL/min recebendo Tamiflu®, recomenda-se que a dose seja reduzida para uma cápsula de 30 mg de Tamiflu® em dias alternados, por tempo a critério médico, ou para crianças, doses de acordo com o peso corporal, em dias alternados, por tempo a critério médico. Em pacientes submetidos à hemodiálise, uma dose inicial de 30 mg de Tamiflu® pode ser administrada antes do início da diálise. Para manter a concentração plasmática em níveis terapêuticos, a dose de 30 mg deve ser administrada após cada sessão alternada de hemodiálise. Para diálise peritoneal, uma dose inicial de 30 de Tamiflu® administrada antes do início da diálise e seguida de doses de 30 mg adicionais administradas a cada 7 dias é recomendada para profilaxia (vide item “3. Características Farmacológicas - Farmacocinética em situações clínicas especiais”). A farmacocinética de oseltamivir não foi estudada em pacientes com doença renal terminal (isto é,depuração de creatinina inferior a 10 mL/min) não submetidos a diálise. Desta forma, não é possível recomendar dose para esse grupo de pacientes.

Os dados clínicos disponíveis em pacientes pediátricos com comprometimento renal são insuficientes para recomendar dose para este grupo.

Pacientes com insuficiência hepática: Não é necessário ajuste de dose para pacientes que tenham disfunção hepática leve a moderada e que estejam em tratamento ou profilaxia para gripe. A segurança e a farmacocinética em pacientes com disfunção hepática grave não foram estudadas.', 'Tamiflu® é contraindicado a pacientes com hipersensibilidade conhecida ao fosfato de oseltamivir ou a qualquer componente do produto.', 'RESUMO DO PERFIL DE SEGURANÇA

O perfil de segurança global do tratamento com Tamiflu® está baseado em dados de 2.646 pacientes adultos / adolescentes e 859 pacientes pediátricos com gripe, e em dados de 1.943 pacientes adultos / adolescentes e 148 pacientes pediátricos recebendo Tamiflu® como profilaxia para gripe em estudos clínicos. Em estudos de tratamento em adultos / adolescentes, as reações adversas relatadas com mais frequência foram náusea, vômito e dor de cabeça. A maioria destas reações adversas foi relatada em situações únicas e ocorreu tanto no primeiro ou no segundo dia de tratamento e foram resolvidas espontaneamente dentro de 1–2 dias. Em estudos de profilaxia em adultos / adolescentes, as reações adversas mais frequentemente relatadas foram náusea, vômito, dor de cabeça e dor. Em crianças, a reação adversa mais comumente relatada foi vômito. Na maioria dos pacientes, estes eventos não ocasionaram descontinuação do Tamiflu®.

RESUMO TABELADO DE REAÇÕES ADVERSAS AO MEDICAMENTO EM ESTUDOS CLÍNICOS

As reações adversas ao medicamento em estudos clínicos são listadas conforme a classificação MedDRA de sistemas de órgãos. A categoria de frequência correspondente para cada reação adversa ao medicamento (Tabela 1) é baseada na seguinte convenção:

Categoria
Frequência

Muito comum
≥ 10%

Comum
≥ 1% e < 10%

Incomum
≥ 0,1% e < 1%

Raro
≥ 0,01% e < 0,1%

Muito raro
< 0,01%

Desconhecida
Não pode ser estimada pelos dados disponíveis

TRATAMENTO E PROFILAXIA DA GRIPE EM ADULTOS E ADOLESCENTES

Em estudos de tratamento e profilaxia em adultos / adolescentes, as reações adversas ao medicamento que ocorreram mais frequentemente (?1%) na dose recomendada (75 mg duas vezes ao dia por 5 dias para tratamento e 75 mg uma vez ao dia por até 6 semanas para profilaxia) e cuja incidência foi pelo menos 1% maior no grupo recebendo Tamiflu® quando comparado ao placebo, estão demonstrados na Tabela 1.

A população incluída nos estudos de tratamento de gripe foi composta tanto por adultos / adolescentes saudáveis quanto por pacientes de risco (pacientes com maior risco de desenvolverem complicações associadas à gripe, por exemplo, pacientes idosos e pacientes com doença cardíaca ou respiratória crônica). Em geral, o perfil de segurança em pacientes de alto risco foi qualitativamente similar ao de pacientes adultos/adolescentes saudáveis.

O perfil de segurança relatado em indivíduos que receberam a dose recomendada de Tamiflu® para profilaxia (75 mg uma vez ao dia por até 6 semanas) foi qualitativamente similar ao observado em estudos de tratamento (Tabela 1), apesar da duração maior da dose nos estudos de profilaxia.

Tabela 1. Resumo das reações adversas em ≥ 1% dos pacientes adultos e adolescentes que receberam oseltamivir para tratamento ou profilaxia da gripe em estudos clínicos (diferença do placebo ≥ 1%)

Sistema de classificação de órgãos para reação adversa ao medicamento

Estudos de tratamento

Profilaxia

Categoria da frequênciaa

Oseltamivir (75 mg duas vezes ao dia) N=2.646

Oseltamivir (75 mg uma vez ao dia) N=1.943

Distúrbios gastrintestinais

Náusea

Vômito

10%

8%

8%

2%

muito comum

comum

Distúrbios do sistema nervoso

Dor de cabeça

2%

17%

muito comum

Distúrbios gerais

Dor

<1%

4%

comum

a A categoria da frequência é reportada apenas no grupo de oseltamivir.

ESTUDOS DE TRATAMENTO E PROFILAXIA EM CRIANÇAS ≥ 1 ANO DE IDADE

Um total de 1.481 crianças (incluindo crianças saudáveis entre 1 e 12 anos e crianças asmáticas entre 6 e 12 anos) participou de estudos clínicos de tratamento com oseltamivir para gripe. Um total de 859 crianças recebeu tratamento com a suspensão oral de oseltamivir.

A reação adversa que ocorreu em ≥1% das crianças com idade entre 1–12 anos que receberam oseltamivir em estudos clínicos para tratamento da gripe adquirida naturalmente (n=859) e que tiveram incidência de pelo menos 1% maior no grupo de Tamiflu® quando comparado ao placebo (n= 622) foi vômito (16% com oseltamivir vs. 8% com placebo). Dentre as 148 crianças que receberam a dose recomendada de Tamiflu® uma vez ao dia em um estudo de profilaxia pós-exposição com contato domiciliar (n= 99) e em outro estudo separado pediátrico de profilaxia de 6 semanas (n= 49), vômito foi a reação adversa mais frequente (8% com oseltamivir vs.2% no grupo sem profilaxia). Tamiflu® foi bem tolerado nestes estudos e os eventos adversos estão consistentes com aqueles anteriormente observados em estudos pediátricos de tratamento.

TRATAMENTO E PROFILAXIA DA GRIPE EM PACIENTES GERIÁTRICOS

Não houve diferenças clinicamente relevantes no perfil de segurança entre os 942 indivíduos com 65 anos de idade ou mais que receberam Tamiflu® ou placebo, em comparação à população mais jovem (até 65 anos).

PROFILAXIA DA GRIPE EM INDIVÍDUOS IMUNOCOMPROMETIDOS

Em um estudo de profilaxia de 12 semanas em 475 indivíduos imunocomprometidos, incluindo 18 crianças de 1–12 anos de idade, o perfil de segurança em 238 indivíduos recebendo Tamiflu® foi consistente com o previamente observado em estudos clínicos de profilaxia com Tamiflu®.

EXPERIÊNCIA PÓS-COMERCIALIZAÇÃO

Os eventos adversos a seguir foram identificados durante o período de pós-comercialização do Tamiflu®. Como estes eventos foram reportados voluntariamente a partir de uma população de tamanho desconhecido, não é possível estimar com segurança suas frequências e / ou estabelecer relação causal com a exposição a Tamiflu®.

Alteração de pele e de tecido subcutâneo: hipersensibilidade tais como reações alérgicas de pele incluindo dermatites, rash, eczema, urticária, eritema multiforme, alergia, reações anafiláticas ou anafilactoides, edema de face, síndrome de Steven-Johnson e necrólise epidérmica tóxica têm sido reportados.

Alteração hepatobiliar: hepatite e elevação de enzimas hepáticas têm sido reportados em pacientes com síndrome gripal recebendo oseltamivir.

Alteração psiquiátrica e alteração do sistema nervoso: convulsão e delírio (incluindo sintomas como nível alterado de consciência, confusão, comportamento anormal, ilusões, alucinações, agitação, ansiedade, pesadelos) têm sido reportados durante a administração de Tamiflu® em pacientes com gripe, predominantemente em crianças e adolescentes. Em raros casos, esses eventos resultaram em danos acidentais. A relação entre o uso de Tamiflu® e esses eventos é desconhecida. Tais eventos neuropsiquiátricos também têm sido relatados em pacientes com gripe que não fizeram uso de Tamiflu®.

Alterações gastrintestinais: sangramentos gastrintestinais foram observados após o uso de Tamiflu®. Em particular, quadros de colite hemorrágica regrediram ao final da gripe ou quando o tratamento com Tamiflu® foi interrompido.

ALTERAÇÕES EM EXAMES LABORATORIAIS

Elevação das enzimas hepáticas foi relatada em pacientes com síndrome gripal recebendo oseltamivir.

Em casos de eventos adversos, notifique ao Sistema de Notificações em Vigilância Sanitária – NOTIVISA ou à Vigilância Sanitária Estadual ou Municipal.', 'As informações derivadas de estudos de farmacologia e farmacocinética com fosfato de oseltamivir sugerem que as interações medicamentosas clinicamente significativas são improváveis.

O fosfato de oseltamivir é convertido extensivamente no composto ativo por esterases localizadas predominantemente no fígado. Interações medicamentosas que envolvem competição por esterases não foram relatadas extensivamente na literatura. A baixa ligação de oseltamivir e do metabólito ativo com proteínas não sugere probabilidade de interações por deslocamento do fármaco.

Estudos in vitro demonstraram que nem o fosfato de oseltamivir nem o seu metabólito ativo são bons substratos para as oxidases de função mista P450 ou para glucoroniltransferases. Não há base de mecanismo para a interação com contraceptivos orais.

A cimetidina, inibidor não específico das isoformas do citocromo P450 e competidor para secreção tubular renal de fármacos básicos ou catiônicos, não tem efeito sobre as concentrações plasmáticas de oseltamivir ou de seus metabólitos ativos.

As interações clinicamente importantes do fármaco, envolvendo competição para a secreção tubular renal, são improváveis devido à margem de segurança já conhecida para a maioria desses fármacos às características de eliminação do metabólito ativo (filtração glomerular e secreção tubular aniônica) e à capacidade de excreção dessas vias. A coadministração de probenecida resulta no aumento de, aproximadamente, duas vezes na exposição ao metabólito ativo, devido à diminuição na secreção tubular ativa no rim. Entretanto, por causa da ampla margem de segurança do metabólito ativo, não é necessário ajuste de dose quando coadministrado com probenecida.

A coadministração com amoxicilina não altera as concentrações plasmáticas dos dois compostos, indicando que a competição pela via de secreção aniônica é fraca. A coadministração com paracetamol não altera as concentrações plasmáticas de oseltamivir, de seu metabólito ativo ou do paracetamol. Nenhuma interação farmacocinética entre oseltamivir ou seu principal metabólito tem sido observada quando coadministrado com paracetamol, ácido acetilsalicílico, cimetidina, antiácidos (magnésio, hidróxido de alumínio, carbonato de cálcio), varfarina, rimantadina ou amantadina.

Em estudos clínicos fase III de profilaxia e de tratamento, Tamiflu® foi coadministrado com medicamentos usados comumente, como inibidores da ECA (enalapril, captopril), diuréticos tiazídicos (bendrofluazida), antibióticos (penicilina, cefalosporina, azitromicina, eritromicina e doxiciclina), bloqueadores do receptor H2 (ranitidina, cimetidina), betabloqueadores (propranolol), xantinas (teofilina), simpatomiméticos (pseudoefedrina), opioides (codeína), corticosteroides, broncodilatadores inalatórios e agentes analgésicos (ácido acetilsalicílico, ibuprofeno e paracetamol). Não foi observada mudança da frequência ou do perfil de eventos adversos como resultado da coadministração de Tamiflu® com esses compostos.

Estudos clínicos incluíram várias crianças recebendo medicações para asma e um número maior de crianças tratadas concomitantemente com ampla gama de antibióticos. A segurança de oseltamivir foi comparada entre crianças recebendo agentes com potencial teórico para interação farmacológica e crianças que não estavam recebendo essas medicações. Não foram encontradas diferenças em perfil de efeitos colaterais ou avaliações laboratoriais. Portanto, parece que os medicamentos mais comumente prescritos para crianças e adolescentes, quando administrados em conjunto com oseltamivir, não aumentam o nível de risco para o paciente.', 'GERAIS

Eventos neuropsiquiátricos semelhantes a convulsões e delírios têm sido relatados durante a administração de Tamiflu® em pacientes com gripe, predominantemente em crianças e adolescentes. Em raros casos, esses eventos resultaram em dano acidental. A contribuição de Tamiflu® para esses eventos é desconhecida. Esses eventos também têm sido relatados em pacientes com gripe que não estavam tomando Tamiflu®. Três grandes estudos epidemiológicos independentes confirmaram que pacientes infectados com gripe recebendo Tamiflu® não apresentam maior risco de desenvolvimento de eventos neuropsiquiátricos em comparação com indivíduos infectados com influenza que não receberam tratamento antiviral (vide item Reações adversas Pós-comercialização).

Os pacientes, especialmente crianças e adolescentes, devem ser rigorosamente monitorados para sinais de comportamento anormal.

Não há evidência da eficácia de Tamiflu® em nenhum tipo de doença causada por outros agentes que não os vírus causadores da gripe, influenza A e B.

Interações medicamentosas clinicamente importantes que envolvam a competição pela secreção tubular renal são pouco prováveis, devido à margem de segurança conhecida para a maioria das substâncias, as características de eliminação do metabólito ativo (filtração glomerular e secreção tubular aniônica) e à capacidade de excreção dessas vias. No entanto, deve se ter cautela ao prescrever oseltamivir a indivíduos que estejam tomando agentes co-excretados com uma margem terapêutica estreita (por exemplo: clorpropamida, metotrexato e fenilbutazona).

EFEITO SOBRE A CAPACIDADE PARA DIRIGIR E OPERAR MÁQUINAS

Não foram realizados estudos sobre o efeito de Tamiflu® na capacidade de dirigir veículos ou operar máquinas. A atividade farmacológica e os eventos adversos reportados até o momento não indicam que este efeito é provável.

Até o momento não há informações de que Tamiflu® possa causar doping.

GRAVIDEZ

Categoria de risco na gravidez: B.

Este medicamento não deve ser utilizado por mulheres grávidas sem orientação médica ou do cirurgião-dentista.

Em estudos reprodutivos em ratos e coelhos, não foi observado efeito teratogênico. A exposição fetal em ratos e coelhos foi de aproximadamente 15%–20% da exposição da mãe.

Não foram realizados estudos clínicos controlados para avaliar o uso de oseltamivir em mulheres grávidas; contudo, há evidências pós-comercialização e de estudos observacionais que demonstram o benefício do regime posológico atual nessa população de pacientes. Os resultados da análise farmacocinética indicam uma baixa exposição ao metabólito ativo; todavia, ajustes de dose não são recomendados no tratamento ou profilaxia da gripe em mulheres grávidas (vide item “3. Características Farmacológicas – Farmacocinética em situações clínicas especiais”). Uma grande quantidade de dados de mulheres grávidas expostas a oseltamivir (mais de 1.000 resultados expostos durante o primeiro trimestre) de relatórios pós-comercialização e estudos observacionais, em conjunto com os estudos em animais, indicam nenhum efeito nocivo direto ou indireto no que diz respeito à gravidez, desenvolvimento embrionário/fetal ou pós-natal. Após considerar as informações de segurança e benefício disponíveis, a patogenicidade do vírus da gripe em circulação e a condição subjacente da paciente grávida, Tamiflu® poderá ser usado em mulheres grávidas.

O uso seguro de oseltamivir durante o trabalho de parto e o parto não foi estabelecido.

LACTANTES

Em ratas lactantes, oseltamivir e o metabólito ativo são excretados no leite. Há pouca informação disponível sobre a amamentação de crianças por mães em uso de oseltamivir e a excreção de oseltamivir no leite materno. Dados limitados demonstraram que oseltamivir e o metabólito ativo foram detectados no leite materno; contudo, os níveis eram baixos, o que resultaria em doses sub-terapêuticas para o lactente. Com base nessas informações, na patogenicidade do vírus da gripe em circulação e na condição subjacente da lactante, o uso de Tamiflu® pode ser considerado.', 'https://www.bulas.med.br/bula/tamiflu-profissional', 'Vermelha', '2026-11-22T23:23:57.473Z' WHERE NOT EXISTS (SELECT 1 FROM public.bulario WHERE fonte = 'https://www.bulas.med.br/bula/tamiflu-profissional');
  PERFORM setval('public.bulario_id_seq', GREATEST((SELECT coalesce(max(id),1) FROM public.bulario), (SELECT last_value FROM public.bulario_id_seq)));
END
$migracao$;
