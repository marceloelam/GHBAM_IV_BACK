package br.com.iv.util;

//utilizando IA com Fine Tunning e API de chat
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/api/ia")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AcessoIaResource {

    @Inject
    AcessoIa acessoIa;

    @POST
    @Path("/perguntar")
    public Response perguntar(Map<String, Object> body) {
        try {
            String pergunta = (String) body.get("pergunta");
            String modelo = modeloSolicitado(body);

            if (pergunta == null || pergunta.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Campo 'pergunta' e obrigatorio"))
                    .build();
            }

            br.com.iv.iaauditoria.IaAuditoriaContexto.registrar("IA_PERGUNTA_LIVRE", null, null, pergunta);
            String resposta = modelo == null
                ? acessoIa.enviarPergunta(pergunta)
                : acessoIa.enviarPergunta(pergunta, modelo);
            return Response.ok(Map.of("resposta", resposta)).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    static String modeloSolicitado(Map<String, Object> body) {
        Object modelo = body.get("modelo");
        if (!(modelo instanceof String valor) || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    @POST
    @Path("/chat")
    public Response chat(Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> mensagens = (List<Map<String, String>>) body.get("mensagens");
            String modelo = body.containsKey("modelo") ? (String) body.get("modelo") : null;

            if (mensagens == null || mensagens.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Campo 'mensagens' e obrigatorio"))
                    .build();
            }

            br.com.iv.iaauditoria.IaAuditoriaContexto.registrar("IA_CHAT_LIVRE", null, null, null);
            String resposta = modelo == null || modelo.isBlank() ? acessoIa.enviarMensagens(mensagens) : acessoIa.enviarMensagens(mensagens, modelo);
            return Response.ok(Map.of("resposta", resposta)).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/modelos")
    public Response modelos() {
        return Response.ok(List.of(
            Map.of("id", "groq:openai/gpt-oss-120b", "nome", "Groq - GPT OSS 120B (Mais Rapido)"),
            Map.of("id", "groq:mistral-nemo-12b-07-2024", "nome", "Groq - Mistral Nemo 12B"),
            Map.of("id", "deepseek/deepseek-chat-v3-0324:free", "nome", "DeepSeek Chat"),
            Map.of("id", "qwen/qwen3-30b-a3b:free", "nome", "Qwen 3 30B"),
            Map.of("id", "google/gemma-3n-e4b:free", "nome", "Gemma 3"),
            Map.of("id", "meta-llama/llama-4-scout:free", "nome", "Llama 4 Scout")
        )).build();
    }

    @GET
    @Path("/fine-tuning/preparar-dados")
    public Response prepararDadosFineTuning(@QueryParam("setor") String setor) {
        String systemPrompt = "Voce e um assistente medico do GHBAM IV. " +
            "Responda em portugues brasileiro, de forma clara e profissional. " +
            "Forneca informacoes clinicas baseadas em evidencias, mas sempre recomende consulta com medico responsavel. " +
            "Nunca forneca diagnosticos definitivos, apenas orientacoes gerais e possiveis medicacoes" +
            "Voce deve apoiar o medico ou equipe de saude, respondendo perguntas clinicas e fornecendo orientacoes baseadas em protocolos atualizados. ";

        List<Map<String, String>> exemplos = new ArrayList<>();

        if ("FONOAUDIOLOGIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com dificuldade de articulacao fonologica. Quais exercicios indicar?"),
                Map.of("role", "assistant", "content", "Para dificuldades de articulacao fonologica, recomendo exercicios de consciencia fonologica, repeticao de fonemas em silabas isoladas e progressivamente em palavras, e jogos fonoarticulatorios. Importante avaliar historico de otites e adenoides."),
                Map.of("role", "user", "content", "Como avaliar risco de disfagia em paciente idoso?"),
                Map.of("role", "assistant", "content", "Utilize o protocolo EAT-10 para triagem, observe tempo de degluticao, presenca de tosse/engasgo, qualidade vocal pos-degluticao. Considere encaminhamento para RNM de disfagia se score > 3 no EAT-10.")
            ));
        }

        if ("PSICOLOGIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com sinais de ansiedade e insonia. Como abordar?"),
                Map.of("role", "assistant", "content", "Avalie frequencia e duracao dos sintomas (criterios DSM-5). Investigue triggers, historico familiar, uso de substancias. Considere escala HAD. Encaminhe para avaliacao psiquiatrica se necessario. Tecnicas cognitivo-comportamentais podem ser indicadas."),
                Map.of("role", "user", "content", "Quando suspectar depressao em adolescente?"),
                Map.of("role", "assistant", "content", "Observe alteracoes comportamentais, isolamento social, queda no rendimento escolar, mudancas de apetite/sono, ideacao suicida. Use escalas como CDI (Children's Depression Inventory). Encaminhar para psiquiatria se necessario.")
            ));
        }

        if ("NUTRICAO".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente diabetico tipo 2 com glicemia de 250mg/dL em jejum. Qual orientacao nutricional?"),
                Map.of("role", "assistant", "content", "Priorizar carboidratos complexos (integrais), limitar acucares simples, aumentar fibras. Fracionar refeicoes em 5-6x/dia. Verificar funcao renal para ajuste proteico. Encaminhar para educacao em diabetes. Reavaliar medicacao antidiabetica."),
                Map.of("role", "user", "content", "Como calcular necessidades caloricas em paciente critico?"),
                Map.of("role", "assistant", "content", "Usar equacoes preditivas (Harris-Benedict ou Mifflin-St Jeor) com fator de estresse. Preferir via oral se possivel. Considerar imunomoduladores em pacientes nutricionalmente vulneraveis. Monitorar balanco nitrogenado.")
            ));
        }

        if ("ASSISTENCIA SOCIAL".equals(setor) || "ASSISTENCIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com vinculo social fragilizado. Como proceder?"),
                Map.of("role", "assistant", "content", "Avaliar rede de apoio familiar e comunitario. Verificar condicoes de moradia, renda e acesso a beneficios sociais. Encaminhar para CRAS/CREAS conforme necessidade. Articular com equipe de saude para plano de acompanhamento integrado."),
                Map.of("role", "user", "content", "Como identificar situacao de vulnerabilidade social em internacao?"),
                Map.of("role", "assistant", "content", "Aplicar questionario social na admissao: verificar moradia, renda per capita, composicao familiar, acesso a servicos de saude, historico de internacoes previas. Identificar fatores de risco como idosos que moram sozinhos, criancas em situacao de vulnerabilidade, pacientes com doencas cronicas sem acompanhamento.")
            ));
        }

        if ("FISIOTERAPIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente pos-AVC com hemiparesia direita. Qual protocolo de reabilitacao?"),
                Map.of("role", "assistant", "content", "Iniciar precoce com mobilizacao passiva, posicionamentos antiespasticidade, exercicios de facilitacao neuromuscular proprioceptiva (PNF), treino de transfers e marcha conforme evolucao. Usar abordagem task-oriented. Avaliar spasticidade pela escala MAS. Reavaliacao semanal minima."),
                Map.of("role", "user", "content", "Como prevenir Trombose Venosa Profunda em paciente acamado?"),
                Map.of("role", "assistant", "content", "Mobilizacao passiva de MMII, compressao elastoterapica se nao contra-indicado, profilaxia medicamentosa com enoxaparina conforme protocolo medico, hidratacao adequada, elevacao de membros inferiores quando possivel.")
            ));
        }

        if ("PEDIATRIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Lactente de 3 meses com febre 38.5C, o que fazer?"),
                Map.of("role", "assistant", "content", "Avaliar estado geral, aceitacao de dieta, presenca de sinais de alarma (letargia, petequias, recusa alimentar). Verificar calendario vacinal. Solicitar hemocultura se temperatura > 38C em < 3 meses. Encaminhar para avaliacao presencial. Orientar pais sobre sinais de piora."),
                Map.of("role", "user", "content", "Como calcular medicacao pediatrica por peso?"),
                Map.of("role", "assistant", "content", "Regra basica: dose = peso(kg) x dose unitaria. Exemplo: paracetamol 15mg/kg/dose, maximo 75mg/kg/dia fracionado em 4-6 doses. Sempre confirmar dose maxima. Usar seringa calibrada para precisao. Verificar interacoes medicamentosas.")
            ));
        }

        if ("DENTISTA".equals(setor) || "BUCOMAXILO".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com dor de dente intensa e swelling facial. Qual conduta?"),
                Map.of("role", "assistant", "content", "Avaliar necessidade de antibioticoterapia (amoxicilina 500mg 8/8h se infeccao evidente). Solicitar radiografia periapical. Encaminhar para endodontia/exodontia conforme diagnostico. Orientar sobre higiene oral e analgesicos. Avaliar necessidade de drenagem se abscesso."),
                Map.of("role", "user", "content", "Como prevenir caries em pacientes especiais?"),
                Map.of("role", "assistant", "content", "Fluo topico diario, higiene assistida conforme capacidade do paciente, dieta pobre em acucares, aplicacao de selante, visitas semestrais ao dentista. Em pacientes com deficiencia, avaliar necessidade de anestesia geral para tratamento odontologico.")
            ));
        }

        if ("CLINICA".equals(setor) || "CLINICA MEDICA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente 60 anos, hipertenso, pressao 180/110mmHg com cefaleia. Conduta?"),
                Map.of("role", "assistant", "content", "Hipertensao grau 2 com sintomatologia. Investigar emergencia hipertensiva? Avaliar orgaos-alvo (fundo de olho, ECG, creatinina). Iniciar ou otimizar antihipertensivo. Encaminhar para observacao se necessario. Manter monitorizacao Pressorica. Investigar causa secundaria."),
                Map.of("role", "user", "content", "Como investigar dispneia cronica em paciente de 55 anos?"),
                Map.of("role", "assistant", "content", "Solicitar espirometria, radiografia de torax, BNP, hemograma, TSH. Avaliar historico toxicagico, exposicao ocupacional. Considerar Eco coracao, TC de torax, prova de funcao pulmonar completa. Encaminhar para pneumologia ou cardiologia conforme achados.")
            ));
        }

        if ("GINECOLOGIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Mulher 35 anos com corrimento vaginal amarelado e prurido. Qual diagnostico?"),
                Map.of("role", "assistant", "content", "Corrimento amarelado sugere tricomoniase ou vaginose bacteriana. Solicitar exame de corrimento com Gram, pH vaginal, whiff test. Tratar conforme etiologia (metronidazol para vaginose, tinidazol para tricomoniase). Avaliar parceiros. Orientar higiene e uso de preservativo."),
                Map.of("role", "user", "content", "Quando indicar rastreamento de HPV e colposcopia?"),
                Map.of("role", "assistant", "content", "Rastreamento inicia aos 25 anos com citologia cervical (Papanicolau) a cada 3 anos. A partir dos 30, pode associar teste HPV. Colposcopia indicada para ASC-US com HPV positivo, LSIL, ou teste HPV positivo. Encaminhar para ambulatorio de colposcopia.")
            ));
        }

        if ("CARDIOLOGIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com dor toracica tipica. Quando suspectar SCA?"),
                Map.of("role", "assistant", "content", "Dor anginosa tipica: retroesternal, opressiva, irradiada para braco esquerdo/pescoço, associada a sudorese, nausea. Factor de risco cardiovascular associado. Encaminhar IMEDIATAMENTE para emergencia com ECG e troponina. Nao atrasar. Codigo infarto."),
                Map.of("role", "user", "content", "Como stratificar risco cardiovascular em paciente assintomatico?"),
                Map.of("role", "assistant", "content", "Usar escore de Framingham ou ASCVD. Avaliar fatores: idade, sexo, colesterol total/HDL, pressao arterial, diabetes, tabagismo. Calcular risco em 10 anos. Intervencoes conforme risco: <5% mudanca de estilo de vida, 5-20% estatina +/- anti-hipertensivo, >20% tratamento intensivo.")
            ));
        }

        if ("ORTOPEDIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com fratura de femure. Quando operar?"),
                Map.of("role", "assistant", "content", "Fraturas de femur proximal: idealmente nas primeiras 24-48h se condicoes clinicas permitirem. Avaliar comorbidades, manter hidratacao e analgesia adequada. Profilaxia de TVP pre-operatoria. Contra-indicacao: choque nao controlado, IAM recente, infeccao ativa."),
                Map.of("role", "user", "content", "Como avaliar paciente com lombalgia aguda?"),
                Map.of("role", "assistant", "content", "Avaliar flags vermelhos: idade >50 anos, historia de cancer, perda de peso, uso de corticoides, dor noturna, deficit neurologico. Se presentes, solicitar radiografia, ressonancia magnetica. Se flags ausentes: manejar conservadoramente com AINES, mobilizacao precoce, fisioterapia.")
            ));
        }

        if ("DERMATOLOGIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com lesao cutanea suspeita. Quando biopsiar?"),
                Map.of("role", "assistant", "content", "Biopsiar lesoes com: mudanca de cor, bordas irregulares, diametro >6mm, assimetria, sangramento espontaneo (criterios ABCDE). Em lesoes dudas, sempre biopsiar para seguranca. Escolher metodo conforme localizacao e suspeita clinica (shave, punch, excisional)."),
                Map.of("role", "user", "content", "Como tratar herpes zoster em paciente imunocompetente?"),
                Map.of("role", "assistant", "content", "Aciclovir 800mg 5x/dia por 7 dias (iniciar nas primeiras 72h). Analgesia adequada (paracetamol ou analgesicos). Avaliar necessidade de corticoides sistemicos. Monitorar complicacoes: neuralgia pos-herpetica, oftalmologica, neurologicas.")
            ));
        }

        if ("NEUROLOGIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com cefaleia subita 'a pior da vida'. Conduta?"),
                Map.of("role", "assistant", "content", "Cefaleia thunderclap = emergencia. Suspectar hemorragia subaracnoidea. Encaminhar IMEDIATAMENTE para TC sem contraste e, se negativa, puncao lombar. AngioTC ou arteriografia para identificar aneurisma. Manter em unidade de terapia intensiva se instavel."),
                Map.of("role", "user", "content", "Como avaliar nivel de consciencia na emergencia?"),
                Map.of("role", "assistant", "content", "Usar escala de Coma de Glasgow (abertura ocular 1-4, resposta verbal 1-5, resposta motora 1-6). Avaliar pupilas (simetria, fotorreatividade). Identificar sinais de herniação cerebral. Pupilas midriaticasfixas = emergencia neurocirurgica imediata.")
            ));
        }

        if ("URGENCIA".equals(setor)||"TRAUMA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente inconsciente na emergencia. Protocolo?"),
                Map.of("role", "assistant", "content", "ABC: Via aerea com protecao cervical, ventilacao, oxigenacao. Acesso venoso periferico. Glicemia capilar imediata. ECG. Avaliar Glasgow. Pupilas. Busca de sinais de trauma. Exames: hemograma, glicemia, eletrolitos, gasometria. Medidas conforme etiologia."),
                Map.of("role", "user", "content", "Como manejar choque hipovolemico?"),
                Map.of("role", "assistant", "content", "2 acessos venosos calibrosos, infusao rapida de cristaloides (Ringer Lactato ou SF 0,9%). Repor 3:1 (3ml de cristaloides para cada 1ml de sangue perdido). Monitorizar PVC, diureses (>0,5ml/kg/h). Identificar e controlar fonte de sangramento. Transfusao se Hb <7g/dL.")
            ));
        }

        if ("MATERNIDADE".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Gestante com pressao arterial 160/100mmHg no terceiro trimestre. Conduta?"),
                Map.of("role", "assistant", "content", "Suspectar pre-eclampsia. Avaliar presenca de proteinuria, cefaleia, turacao visual. Solicitar uricemia, funcao hepatica, plaquetas, crestina. Encaminhar para maternidade de referencia. Antihipertensivo: metildopa ou hidralazina. Avaliar necessidade de antecipacao do parto."),
                Map.of("role", "user", "content", "Como avaliar trabalho de parto prematuro?"),
                Map.of("role", "assistant", "content", "Avaliar frequencia e regularidade das contracoes, modificacao cervical (esvaecimento, dilatao), ultrassom para comprimento cervical (<25mm sugestivo). Solicitar fibronectina fetal se duvida.TOCOLISE: indicated se <34 semanas e sem contra-indicacoes.")
            ));
        }

        if ("RAIO X".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com suspeita de fratura de punho. Qual incidencia radiografica solicitar?"),
                Map.of("role", "assistant", "content", "Solicitar pelo menos 3 incidencias: AP, perfil e obliqua. Em criancas, incluir comparativo com lado contralateral. Avaliar linhas de crescimento e physis. Se suspeita deLesao de Galeazzi ou Monteggia, incluir antebraco inteiro."),
                Map.of("role", "user", "content", "Como identificar pneumotorax no RX de torax?"),
                Map.of("role", "assistant", "content", "Procurar: ausencia de marcação pulmonar, linha visceral pleura, hipertransparencia unilateral, ausencia de vascularizacao na area afetada. Paciente em inspiracao maxima. RX em AP ou PA. Confirmar com TC se duvida.")
            ));
        }

        if ("TOMOGRAFIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com AVC莳timo confirmado no RX. Quando solicitar TC de crânio?"),
                Map.of("role", "assistant", "content", "TC sem contraste IMEDIATAMENTE para differentiate AVC ischemico de hemorragico. Avaliar sinal da arteria cerebral média, efeito de masa, calcificacoes. TC com contraste indicada para investigacao de tumors ou infections."),
                Map.of("role", "user", "content", "Como preparar paciente para TC de abdomen com contraste?"),
                Map.of("role", "assistant", "content", "Jejum de 4-6 horas. Hidratacao adequada pre e pos-exame. Avaliar funcao renal (creatinina) se contraste iodado. Suspensometformina 48h antes e depois. Questionar allergy a iodo.")
            ));
        }

        if ("EMERGENCIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente arrivedor com dor toracica aguda. Protocolo inicial?"),
                Map.of("role", "assistant", "content", "ABC: Avaliar via aerea, ventilacao, circulacao. ECG em 10 minutos. Oximetria, PA, FC, FR. Acesso venoso. Labs: troponina, CK-MB, hemograma, coagulacao, glicemia. Avaliar criterios de alto risco. Codigo infarto se indicado."),
                Map.of("role", "user", "content", "Como evaluar sepse na emergencia?"),
                Map.of("role", "assistant", "content", "Usar criterios qSOFA: PAS <100mmHg, FR >22, alteracao mental. Se >=2 criterios, sospeitar sepse. Solicitar lactato, hemoculturas, urocultura, RX torax. Iniciar antibioticoterapia em 1 hora. Bolus de cristaloides 30ml/kg se hipotenso.")
            ));
        }

        if ("BIOPSIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com nodulo tireoidiano Bethesda III. Conduta?"),
                Map.of("role", "assistant", "content", "Bethesda III = atipia de significado indeterminado. RepetirPunção com agulha fina orientada por ultrassom. Se novamente atypia, considerar lobectomia. Avaliar TSH, ultrassom com doppler. Seguir protocolo TI-RADS para acompanhamento."),
                Map.of("role", "user", "content", "Quando indicar biopsia de medula ossea?"),
                Map.of("role", "assistant", "content", "Indicada para investigate cytopenias inexplicadas, suspecta de leucemia, linfoma, mieloma, metastase medular. Local: cristailon posterior ou espinha iliaca anterior. Analgesia local. Avaliar coagulograma previo. Contra-indicada: infeccao local, coagulopatia grave.")
            ));
        }

        if ("CIRURGIA GERAL".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com apendicite aguda confirmada. Quando operar?"),
                Map.of("role", "assistant", "content", "Apendicectomia eletiva idealmente nas primeiras 24 horas. Antibioticoterapia pre-operatoria: cefalosporina + metronidazol. Laparoscopia preferida se disponivel. Se peritonite ou abscesso, considerar drenagem primeiro."),
                Map.of("role", "user", "content", "Como avaliar paciente para colecistectomia laparoscopica?"),
                Map.of("role", "assistant", "content", "Avaliar: risco cardiologico, funcao hepatica, coagulacao, grupamento sanguineo. Suspender anticoagulantes. Jejum de 8h. Avaliar vias aereas dificeis. TC ou ultrassom previo confirmando diagnostico. Consentimento informado.")
            ));
        }

        if ("COLONOSCOPIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Como preparar intestino para colonoscopia?"),
                Map.of("role", "assistant", "content", "Dieta liquida 1 dia antes. Polietilenoglicol 4L ou fosfato de sodio conforme prescricao. Hidratacao abundante. Suspender ferro 5 dias antes. Suspender anticoagulantes conforme risco. Contra-indicado em obstipacao grave ou megacólon."),
                Map.of("role", "user", "content", "Quando indicar colonoscopia de urgencia?"),
                Map.of("role", "assistant", "content", "Hemorragia digestiva baixa ativa, oclusão intestinal, corpo estranho. Colonoscopia eletiva: rastreamento a partir dos 50 anos, antecedentes familiares de cancer colorretal, sangramento retal persistente, alteracao do habito intestinal.")
            ));
        }

        if ("CTI".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente em ventilacao mecanica. Como ajustar parametros iniciais?"),
                Map.of("role", "assistant", "content", "Modo: AC ou SIMV. Volume corrente 6-8ml/kg. FR 12-20. FiO2 inicial 100%, titular para SpO2 >94%. PEEP inicial 5cmH2O. Avaliar pressoes de via aerea (plateau <30cmH2O). Gasometria arterial em 30 min."),
                Map.of("role", "user", "content", "Como prevenir pneumonia associada a ventilacao mecanica?"),
                Map.of("role", "assistant", "content", "Cabeca do leito elevated 30-45 graus. Higiene oral com clorexidina. Aspiracao traqueal apenas quando necessario. Interromper sedacao diariamente para evaluar readiness. Profilaxia de TVP. Descolonizacao de MRSA se indicado.")
            ));
        }

        if ("ENDOSCOPIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com hematemese. Quando solicitar endoscopia urgente?"),
                Map.of("role", "assistant", "content", "Endoscopia em até 12h se hemorragia ativa ou sinais de alto risco (estigmas de sangramento recente). Estabilizacao hemodinamica previa. Jejum. Acesso venoso bilateral. Critérios de Rockall para estratificacao de risco."),
                Map.of("role", "user", "content", "Como evaluar disfagia para endoscopia?"),
                Map.of("role", "assistant", "content", "Historia detalhada: tipo de alimento, duracao, perda ponderal, odinofagia. Raio X contrastado se suspeita de fistula ou obstrucao. Endoscopia com biopsia para diagnostico etiolögico. Avaliar sinais de alarma.")
            ));
        }

        if ("ENFERMARIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente internada com risco de queda. Como prevenir?"),
                Map.of("role", "assistant", "content", "Avaliar com escala Morse. Orientar paciente e familiares. Camas no nivel mais baixo, rodas travadas. Iluminacao adequada. Calcado apropriado. Revisao de medicacoes que causam sedacao. Protocolo de prevencao de quedas institucional."),
                Map.of("role", "user", "content", "Como prevenir ulceras por pressao em paciente acamado?"),
                Map.of("role", "assistant", "content", "Repositionamento a cada 2h. Colchao de pressao alternada. Protecao de proeminencias osseas. Avaliacao diaria de pele. Nutricao adequada (proteinas, vitamina C, zinco). Hidratacao. Documentacao de lesions.")
            ));
        }

        if ("ECOCARDIOGRAMA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Quando solicitar ecocardiograma?"),
                Map.of("role", "assistant", "content", "Sopros cardiacos, insuficicardia, dor toracica de origem nao definida, arritmias, doenca de Chagas, hipertensao de longa data, pre-operatorio de cirurgia nao cardiaca de alto risco. Avaliar funcao ventricular, valvulopatias, trombos."),
                Map.of("role", "user", "content", "Como interpretar insuficiencia mitral leve ao eco?"),
                Map.of("role", "assistant", "content", "Avaliar jet de regurgitacao por Doppler cor. Graus I-IV pela largura do jet. Insuficiencia leve geralmente bem tolerada. Acompanhamento clinico e eco em 1-2 anos. Vigiar progressao para moderadal/severa.")
            ));
        }

        if ("MASTOLOGIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Nodulo mamario palpavel. Como investigar?"),
                Map.of("role", "assistant", "content", "Avaliar caracteristicas: tamanho, mobilidade, limites, pele. Solicitar mamografia e ultrassom. BIRADS 4 ou 5 = biopsia. BIRADS 3 = seguimento em 6 meses. Avaliar marcadores tumorais se cancer confirmado. Encaminhar para mastologista."),
                Map.of("role", "user", "content", "Como fazer rastreamento de cancer de mama?"),
                Map.of("role", "assistant", "content", "Mamografia bienal de 50-69 anos (SUS). Clinico das mamas a partir de 40 anos. Autoexame mensal opcional. Historia familiar positiva = inicio mais precoce. Ressonancia magnetica para alto risco genetic.")
            ));
        }

        if ("ULTRASSONOGRAFIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Gestante com sangramento no primeiro trimestre. Quando solicitar ultrassom?"),
                Map.of("role", "assistant", "content", "Ultrassom transvaginal urgente para avaliar viabilidade fetal, localizacao do saco gestacional, presenca de atividade cardiaca. Descartar gestacao ectopica ou abortamento. Avaliar idade gestacional."),
                Map.of("role", "user", "content", "Como avaliar massa ovariana no ultrassom?"),
                Map.of("role", "assistant", "content", "Classificar como solida, cistica ou mista. Medir dimensiones. Avaliar paredes (lisas vs irregulares), conteúdo (simples vs complexo), doppler. Cistos simples <5cm podem ser observados. Componentes solidos ou septos = biopsia.")
            ));
        }

        if ("NEFROLOGIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com creatinina elevada. Como investigar Lesao Renal Aguda?"),
                Map.of("role", "assistant", "content", "Classificar LRA: prerenall ( razão BUN/Cr >20), renal, pos-renal. Avaliar volemia, medicacoes nefrotóxicas, obstrucao. Solicitar ureia, creatinina, eletrólitos, gasometria, urina tipo I, sodio urinario. Eco se suspeita obstructiva."),
                Map.of("role", "user", "content", "Quando iniciar dialise de urgencia?"),
                Map.of("role", "assistant", "content", "Indicacoes: hiperpotassemia >6.5mEq/L com alteracoes ECG, acidose metabolica severa (pH <7.1), sobrecarga de volume resistente a diureticos com edema pulmonar, uremia sintomatica (encefalopatia, pericardite).")
            ));
        }

        if ("SALA DE MEDICACAO".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com reacao allergica a medicacao. Conduta?"),
                Map.of("role", "assistant", "content", "Suspender imediatamente o farmaco suspeito. Antihistaminico IM ou IV (dexclorfeniramina). Corticoide sistemico se moderada a grave. Adrenalina IM se anafilaxia (0.3-0.5mg). Monitorizacao hemodinamica. Documentar allergy."),
                Map.of("role", "user", "content", "Como administering medicacao EV com seguranca?"),
                Map.of("role", "assistant", "content", "Verificar prescricao, dose, via, horario. Identificar paciente pelo nome e data de nascimento. Checar allergies. Diluir conforme recomendacao. Velocidade de infusao correta. Observar reaccoes adversas. Documentar administracao.")
            ));
        }

        if ("LABORATORIO".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com leucocitose. Como interpretar?"),
                Map.of("role", "assistant", "content", "Leucocitose >11000/mm3. Causas: infections (neutrofilia), estresse, inflamacao, leucemia (blastos). Avaliar neutrofilos em banda, presenca de cayots toxic, celularidade global. Repetir em 24h se duvida. Investigar fonte de infeccao."),
                Map.of("role", "user", "content", "Como investigar anemia?"),
                Map.of("role", "assistant", "content", "VCM orienta: microcitica (ferro baixa, ferritina, ferritina baixa = ID), normocitica ( inflamacao cronica, IRC, mieloma), macrocitica (B12, folato, hipotireoidismo, alcool). Hemograma completo + indices, reticulocitos, ferro, ferritina, B12, folato.")
            ));
        }

        if ("PSIQUIATRIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com tentativa de suicidio. Conduta imediata?"),
                Map.of("role", "assistant", "content", "Avaliar risco imediato: metodo usado, intencionalidade, planejamento. Internacao psiquiatrica se alto risco. Garantir seguranca do paciente (retirar objetos perigosos). Avaliar comorbididades psiquiatricas. Seguimento ambulatorial intenso."),
                Map.of("role", "user", "content", "Quando indicar internacao psiquiatrica involuntaria?"),
                Map.of("role", "assistant", "content", "Lei 10.216/2001: paciente com transtorno mental que, por falta de consentemento, necessita de internacao. Risco iminente a si ou terceiros. Avaliacao por dois medicos. Ordem medica + comunicacao ao MP em 72h.")
            ));
        }

        if ("CLINICA DA DOR".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com dor cronica. Como avaliar?"),
                Map.of("role", "assistant", "content", "Usar escalas de dor (VAS, EVA, FACES). Caracterizar: localizacao, intensidade, qualidade, duracao, fatores de piora e melhora. Avaliar impacto funcional (qualidade de vida, trabalho). Usar multimodal approach: farmacologico + nao farmacologico."),
                Map.of("role", "user", "content", "Quando indicar bloqueio nervoso para dor cronica?"),
                Map.of("role", "assistant", "content", "Dor neuropatica localizada, dor musculoesqueletica refractaria a tratamento convencional, cancer com dor localizada, antes de procedimentocirurgico. Avaliar com radiologista intervencionista. Temporario (anestesico) ou permanente (neuroablacao).")
            ));
        }

        if ("ANESTESIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente para anestesia geral. Avaliacao pre-anestesica?"),
                Map.of("role", "assistant", "content", "Historia clinica e exame fisico completo. Classificacao ASA. Via aerea (Mallampati). Airway exam. Jejum adequado. Suspender anticoagulantes conforme risco. Exames complementares conforme doencas associadas. Consentimento informado."),
                Map.of("role", "user", "content", "Quando usar anestesia raquidiana vs geral?"),
                Map.of("role", "assistant", "content", "Raquidiana: procedimentos de membros inferiores, pelve, cesariana. Contra-indicada se coagulopatia, infeccao local, hipertensao intracraniana. Geral: procedimentos abdominais altos, via aerea comprometida, preferencia do paciente.")
            ));
        }

        if ("PEQUENAS CIRURGIAS".equals(setor) || "CIRURGIA G. AMBU".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Drenagem de abscesso cutaneo. Tecnica?"),
                Map.of("role", "assistant", "content", "Anestesia local. Incisao e drenagem (I&D) para abscessos >5mm. Drenagem com pinca se locacoes profundas. Cultivo da secrecao. Antibioticoterapia se celulite ao redor, imunossupressao, abscessos recorrentes. Curativo diario."),
                Map.of("role", "user", "content", "Quando suturar ferimento por corte?"),
                Map.of("role", "assistant", "content", "Ferimentos limpos, <6h de evolucao, bordas opostas, sem lesao de estruturas profundas. Contra-indicado: mordidas, ferimentos contaminados, >8h, lesao de tendao nervo vascular. Considerar sutura em camadas se profunda.")
            ));
        }

        if ("AMBULATORIO PC".equals(setor) || "AMBULATORIO P.C.".equals(setor) || "AMBU".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Hipertenso em acompanhamento ambulatorial. Meta de pressao?"),
                Map.of("role", "assistant", "content", "PA <130/80mmHg para a maioria. <140/90mmHg para idosos >80 anos ou fragilidade. Avaliar risco cardiovascular global. Estilo de vida: reducao de sodio, atividade fisica, perda de peso. farmacoterapia escalonada."),
                Map.of("role", "user", "content", "Diabetico tipo 2 em acompanhamento. Meta de HbA1c?"),
                Map.of("role", "assistant", "content", "<7% para maioria. <8% para idosos, expectativa de vida limitada ou comorbidades graves. Avaliar hipoglicemias. drug therapy: metformina de primeira linha. Escalonar com sulfonilureia, inibidor SGLT2, agonista GLP1 conforme perfil.")
            ));
        }

        if ("ORTOPEDIA AMBU".equals(setor) || "ORTOPEDIA".equals(setor) || setor == null) {
            exemplos.addAll(List.of(
                Map.of("role", "user", "content", "Paciente com sindrome do tunel do carpo. Indicacao cirurgica?"),
                Map.of("role", "assistant", "content", "Cirurgia indicada se sintomatologia persistente apos 3-6 meses de tratamento conservador, atrofia de tenar, conducao nervosa alterada. Libertacao do ligamento transverso por via endoscopica ou aberta. Prognostico geralmente bom."),
                Map.of("role", "user", "content", "Como tratar fascite plantar?"),
                Map.of("role", "assistant", "content", "Conservador: alongamento de gastrocnemio e plantar, palmilhas, AINES, fisioterapia. Infiltracao com corticoide se refractario. Ondas de choque em casos cronicos. Cirurgia apenas em casos refratários. Prognostico geralmente bom em 6-12 meses.")
            ));
        }

        return Response.ok(Map.of(
            "setor", setor != null ? setor : "todos",
            "total_exemplos", exemplos.size() / 2,
            "system_prompt", systemPrompt,
            "dados_formatados", exemplos
        )).build();
    }

    @POST
    @Path("/fine-tuning/exportar")
    public Response exportarDadosFineTuning(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> dados = (List<Map<String, String>>) body.get("dados");
        String setor = (String) body.getOrDefault("setor", "medico");

        if (dados == null || dados.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("erro", "Lista de dados vazia"))
                .build();
        }

        StringBuilder jsonl = new StringBuilder();
        for (Map<String, String> item : dados) {
            jsonl.append("{\"messages\":[");
            jsonl.append("{\"role\":\"system\",\"content\":\"Voce e um assistente medico especializado em ").append(setor).append(" do GHBAM.\"}");
            jsonl.append(",{\"role\":\"user\",\"content\":\"").append(escapeJson(item.get("pergunta"))).append("\"}");
            jsonl.append(",{\"role\":\"assistant\",\"content\":\"").append(escapeJson(item.get("resposta"))).append("\"}");
            jsonl.append("]}\n");
        }

        return Response.ok(Map.of(
            "formato", "JSONL",
            "total_registros", dados.size(),
            "conteudo", jsonl.toString(),
            "instrucoes", "Salve o conteudo em arquivo .jsonl e use na plataforma de fine-tuning (OpenRouter/Groq/Azure)"
        )).build();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
