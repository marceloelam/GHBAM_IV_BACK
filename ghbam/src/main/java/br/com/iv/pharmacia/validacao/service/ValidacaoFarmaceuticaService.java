package br.com.iv.pharmacia.validacao.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.iv.entity.PharmaciaProduto;
import br.com.iv.pharmacia.validacao.dto.AlertaFarmaceuticoDto;
import br.com.iv.pharmacia.validacao.dto.ItemDispensacaoDto;
import br.com.iv.pharmacia.validacao.dto.ValidacaoDispensacaoRequest;
import br.com.iv.pharmacia.validacao.dto.ValidacaoFarmaceuticaResponse;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.AlergiaCruzada;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.Duplicidade;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.Interacao;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.Medicamento;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Validacao farmaceutica da dispensacao: a analise que o farmaceutico faz antes
 * de entregar o medicamento.
 *
 * Nao substitui o CDSS da prescricao (br.com.iv.cdss), que atua antes, sobre o
 * que o medico escreveu. Este motor atua depois, sobre o que a farmacia vai de
 * fato entregar, cruzando os itens entre si, com o que o paciente ja usa e com
 * as condicoes clinicas (idade, peso, funcao renal, gestacao).
 *
 * Principio de projeto: o motor nunca decide sozinho. Ele produz alertas com
 * conduta; quem confirma, justifica e assina e o farmaceutico.
 */
@ApplicationScoped
public class ValidacaoFarmaceuticaService {

    private static final Pattern DOSE_MASSA = Pattern.compile("(\\d+(?:[,.]\\d+)?)\\s*(mcg|ug|mg|g)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERVALO_FRACIONADO = Pattern.compile("(\\d+)\\s*/\\s*\\1\\s*(?:h|hora)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERVALO_A_CADA = Pattern.compile("(?:a\\s*cada|q)\\s*(\\d+(?:[,.]\\d+)?)\\s*(?:h|hora)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VEZES_AO_DIA = Pattern.compile("(\\d+)\\s*(?:x|vezes)\\s*(?:ao|por|/)?\\s*dia",
            Pattern.CASE_INSENSITIVE);

    /** A partir desta idade valem os criterios de Beers. */
    private static final int IDADE_IDOSO = 65;

    @Inject
    BaseConhecimentoFarmaceutico base;

    public ValidacaoFarmaceuticaResponse validar(ValidacaoDispensacaoRequest request) {
        ValidacaoFarmaceuticaResponse resposta = new ValidacaoFarmaceuticaResponse();
        resposta.setVersaoBase(base.getVersao());

        if (request == null || request.getItens().isEmpty()) {
            resposta.setAlertas(List.of(alerta("FARM_SEM_ITENS", "MINOR", "DOSE", "Nada a validar",
                    "Nenhum item foi informado para dispensacao.",
                    "Inclua ao menos um item antes de solicitar a validacao.", null, null, false, List.of())));
            return resposta;
        }

        List<AlertaFarmaceuticoDto> alertas = new ArrayList<>();
        List<ItemDispensacaoDto> itens = request.getItens();
        resposta.setItensTotais(itens.size());

        // Resolve cada item contra a base uma unica vez.
        List<Medicamento> resolvidos = new ArrayList<>();
        int reconhecidos = 0;
        for (ItemDispensacaoDto item : itens) {
            Medicamento m = base.identificar(item.identificacaoClinica());
            if (m == null && item.getProdutoNome() != null) {
                m = base.identificar(item.getProdutoNome());
            }
            resolvidos.add(m);
            if (m != null) {
                reconhecidos++;
            }
        }
        resposta.setItensReconhecidos(reconhecidos);

        for (int i = 0; i < itens.size(); i++) {
            ItemDispensacaoDto item = itens.get(i);
            Medicamento med = resolvidos.get(i);

            // O cadastro do produto (alta vigilancia, antimicrobiano, lista controlada,
            // exige receita) e uma segunda fonte de sinalizacao, independente da base de
            // conhecimento: cobre os itens que a base nao conhece (formulacao local,
            // manipulado) e respeita a curadoria que o proprio farmaceutico fez no cadastro.
            PharmaciaProduto produtoEntity = item.getProdutoId() != null
                    ? PharmaciaProduto.findById(item.getProdutoId())
                    : null;

            if (med == null) {
                alertas.add(alerta("FARM_ITEM_NAO_RECONHECIDO", "MINOR", "ORIENTACAO",
                        "Item sem correspondencia na base",
                        "Nao foi possivel identificar a substancia de " + nome(item)
                                + ", entao as checagens clinicas automaticas (alergia, interacao, dose) nao se aplicam a este item.",
                        "Cadastre o principio ativo no produto para habilitar as checagens, e confira este item manualmente.",
                        nome(item), null, false, List.of("cadastro")));
                avaliarSinalizacoes(item, null, produtoEntity, alertas);
                continue;
            }

            avaliarAlergias(request, item, med, alertas);
            avaliarCondicoesDoPaciente(request, item, med, alertas);
            avaliarDose(request, item, med, alertas);
            avaliarSinalizacoes(item, med, produtoEntity, alertas);
            avaliarCoerenciaQuantidade(item, alertas);
        }

        avaliarInteracoes(request, itens, resolvidos, alertas);
        avaliarDuplicidade(itens, resolvidos, alertas);

        alertas.sort(Comparator.comparingInt(a -> ordem(a.getSeveridade())));
        resposta.setAlertas(alertas);
        resposta.setPossuiCritico(alertas.stream().anyMatch(a -> "CRITICAL".equalsIgnoreCase(a.getSeveridade())));
        resposta.setExigeJustificativa(alertas.stream().anyMatch(AlertaFarmaceuticoDto::isExigeJustificativa));
        resposta.setExigeDuplaChecagem(alertas.stream()
                .anyMatch(a -> "ALTA_VIGILANCIA".equalsIgnoreCase(a.getCategoria())));
        return resposta;
    }

    // ------------------------------------------------------------------
    // Alergia: a checagem que mais evita dano grave na bancada
    // ------------------------------------------------------------------

    private void avaliarAlergias(ValidacaoDispensacaoRequest request, ItemDispensacaoDto item, Medicamento med,
            List<AlertaFarmaceuticoDto> alertas) {
        for (String alergiaBruta : request.getAlergias()) {
            String alergia = BaseConhecimentoFarmaceutico.normalizar(alergiaBruta);
            if (alergia.isBlank()) {
                continue;
            }

            // 1) Coincidencia direta: a alergia nomeia a propria substancia.
            boolean direta = med.termos.stream().anyMatch(t -> t.length() >= 4 && alergia.contains(t));
            if (direta) {
                alertas.add(alerta("FARM_ALERGIA_DIRETA", "CRITICAL", "ALERGIA", "Alergia registrada a este medicamento",
                        "O prontuario registra alergia a " + alergiaBruta.trim() + " e o item "
                                + nome(item) + " contem " + med.nome + ".",
                        "Nao dispensar sem contato com o prescritor. Se a dispensacao for mantida, registre a justificativa clinica.",
                        nome(item), alergiaBruta.trim(), true, List.of("alergia")));
                continue;
            }

            // 2) Reatividade cruzada por classe.
            Medicamento medAlergia = base.identificar(alergiaBruta);
            Set<String> classesAlergia = new LinkedHashSet<>();
            if (medAlergia != null) {
                classesAlergia.addAll(medAlergia.classes);
            }
            classesAlergia.add(alergia);

            for (AlergiaCruzada cruzada : base.getAlergiasCruzadas()) {
                // Compara nos dois sentidos: a alergia pode vir como a classe
                // inteira ("betalactamico_penicilina", quando o termo foi
                // reconhecido na base) ou como a palavra que o paciente disse
                // na triagem ("penicilina"), que e parte do nome da classe.
                boolean alergiaBate = classesAlergia.stream().anyMatch(c -> c.equals(cruzada.classeAlergia)
                        || c.contains(cruzada.classeAlergia)
                        || (c.length() >= 4 && cruzada.classeAlergia.contains(c)));
                if (alergiaBate && med.classes.contains(cruzada.classeMedicamento)) {
                    alertas.add(alerta("FARM_ALERGIA_CRUZADA", severidade(cruzada.severidade, "MAJOR"), "ALERGIA",
                            "Possivel reatividade cruzada",
                            "Alergia registrada a " + alergiaBruta.trim() + " e o item " + nome(item)
                                    + " pertence a um grupo com reatividade cruzada (" + cruzada.grupo + "). "
                                    + cruzada.nota,
                            "Confirmar com o paciente o tipo e a gravidade da reacao anterior antes de dispensar.",
                            nome(item), alergiaBruta.trim(),
                            "CRITICAL".equalsIgnoreCase(cruzada.severidade), List.of("alergia", "reatividade_cruzada")));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Condicoes do paciente: renal, gestacao, pediatria, idoso
    // ------------------------------------------------------------------

    private void avaliarCondicoesDoPaciente(ValidacaoDispensacaoRequest request, ItemDispensacaoDto item,
            Medicamento med, List<AlertaFarmaceuticoDto> alertas) {

        // Funcao renal
        if (med.renalLimiteTfg != null && med.renalConduta != null && !med.renalConduta.isBlank()) {
            Double tfg = request.getTfg();
            if (tfg != null && tfg > 0 && tfg < med.renalLimiteTfg) {
                alertas.add(alerta("FARM_AJUSTE_RENAL", "CRITICAL", "RENAL", "Ajuste pela funcao renal",
                        "TFG informada de " + formatar(tfg) + " mL/min/1,73m2, abaixo do limite de "
                                + formatar(med.renalLimiteTfg) + " para " + med.nome + ". " + med.renalConduta,
                        "Confirmar a dose com o prescritor antes de dispensar.", nome(item), null, true,
                        List.of("funcao_renal")));
            } else if (tfg == null) {
                alertas.add(alerta("FARM_RENAL_SEM_DADO", "MINOR", "RENAL", "Funcao renal nao informada",
                        med.nome + " exige ajuste conforme a funcao renal, mas nao ha TFG informada nesta validacao.",
                        "Verificar creatinina recente no prontuario antes de dispensar.", nome(item), null, false,
                        List.of("funcao_renal", "dado_ausente")));
            }
        }

        // Gestacao
        boolean gestante = Boolean.TRUE.equals(request.getGestante());
        if (gestante && med.gestacaoRisco != null && !med.gestacaoRisco.isBlank()) {
            boolean proibido = "X".equalsIgnoreCase(med.gestacaoRisco);
            boolean risco = proibido || "D".equalsIgnoreCase(med.gestacaoRisco);
            if (risco) {
                alertas.add(alerta(proibido ? "FARM_GESTACAO_CONTRAINDICADO" : "FARM_GESTACAO_RISCO",
                        proibido ? "CRITICAL" : "MAJOR", "GESTACAO",
                        proibido ? "Contraindicado na gestacao" : "Risco na gestacao",
                        med.nome + " tem risco gestacional categoria " + med.gestacaoRisco + ". "
                                + (med.gestacaoNota == null ? "" : med.gestacaoNota),
                        "Contatar o prescritor e propor alternativa segura antes de dispensar.", nome(item), null,
                        proibido, List.of("gestacao")));
            }
        }

        Integer idade = request.getIdadeAnos();

        // Pediatria
        if (med.idadeMinimaAnos != null && idade != null) {
            double idadeEfetiva = idade;
            if (idade < 1 && request.getIdadeMeses() != null) {
                idadeEfetiva = request.getIdadeMeses() / 12.0;
            }
            if (idadeEfetiva < med.idadeMinimaAnos) {
                alertas.add(alerta("FARM_IDADE_MINIMA", "CRITICAL", "PEDIATRIA", "Abaixo da idade minima",
                        med.nome + " tem idade minima de referencia de " + formatarIdade(med.idadeMinimaAnos)
                                + " e o paciente tem " + descreverIdade(idade, request.getIdadeMeses()) + ". "
                                + (med.idadeMinimaNota == null ? "" : med.idadeMinimaNota),
                        "Nao dispensar sem confirmacao do prescritor.", nome(item), null, true,
                        List.of("pediatria", "idade")));
            }
        }

        // Idoso: criterios de Beers
        if (idade != null && idade >= IDADE_IDOSO && med.beersNota != null && !med.beersNota.isBlank()) {
            alertas.add(alerta("FARM_IDOSO_BEERS", "MAJOR", "IDOSO", "Medicamento potencialmente inapropriado em idoso",
                    "Paciente com " + idade + " anos. " + med.beersNota,
                    "Sinalizar ao prescritor e reforcar orientacao de seguranca ao paciente e ao cuidador.",
                    nome(item), null, false, List.of("idoso", "beers")));
        }
    }

    // ------------------------------------------------------------------
    // Dose
    // ------------------------------------------------------------------

    private void avaliarDose(ValidacaoDispensacaoRequest request, ItemDispensacaoDto item, Medicamento med,
            List<AlertaFarmaceuticoDto> alertas) {

        boolean pediatrico = request.getIdadeAnos() != null && request.getIdadeAnos() < 12;
        boolean semPeso = request.getPesoKg() == null || request.getPesoKg() <= 0;

        if (med.exigePeso && semPeso) {
            alertas.add(alerta("FARM_PESO_OBRIGATORIO", "CRITICAL", "DOSE", "Peso necessario para conferir a dose",
                    med.nome + " tem dose calculada por peso e o peso do paciente nao foi informado.",
                    "Registrar o peso atual antes de liberar o item.", nome(item), null, true, List.of("peso")));
        } else if (pediatrico && semPeso) {
            alertas.add(alerta("FARM_PESO_PEDIATRICO", "MAJOR", "DOSE", "Peso necessario na dispensacao pediatrica",
                    "Paciente pediatrico sem peso informado - a conferencia de dose fica incompleta.",
                    "Registrar o peso atual para conferir a dose por kg.", nome(item), null, false,
                    List.of("peso", "pediatria")));
        }

        Double doseMg = extrairDoseMg(item.getDose());
        Double doseDia = doseDiaria(doseMg, item.getFrequencia());
        if (med.doseMaximaDiaMg != null && doseDia != null && doseDia > med.doseMaximaDiaMg) {
            alertas.add(alerta("FARM_DOSE_MAXIMA", "CRITICAL", "DOSE", "Dose diaria acima do limite",
                    "Dose diaria estimada de " + formatar(doseDia) + " mg para " + med.nome
                            + ", acima do limite de referencia de " + formatar(med.doseMaximaDiaMg) + " mg/dia.",
                    "Conferir a posologia com o prescritor antes de dispensar.", nome(item), null, true,
                    List.of("dose")));
        }
    }

    // ------------------------------------------------------------------
    // Sinalizacoes: alta vigilancia, controle especial, antimicrobiano, orientacao
    // ------------------------------------------------------------------

    /**
     * Sinalizacoes de alta vigilancia, controle especial, antimicrobiano e
     * retencao de receita. Combina duas fontes: a base de conhecimento (o que o
     * sistema sabe sobre a substancia) e o proprio cadastro do produto (o que o
     * farmaceutico desta unidade marcou) - a segunda cobre o que a primeira nao
     * conhece, como formulacao local ou manipulado.
     */
    private void avaliarSinalizacoes(ItemDispensacaoDto item, Medicamento med, PharmaciaProduto produto,
            List<AlertaFarmaceuticoDto> alertas) {
        boolean altaVigilancia = (med != null && med.altaVigilancia)
                || (produto != null && Boolean.TRUE.equals(produto.getAltaVigilancia()));
        boolean antimicrobiano = (med != null && med.antimicrobiano)
                || (produto != null && Boolean.TRUE.equals(produto.getAntimicrobiano()));
        // O cadastro do produto e mais especifico que a base geral (o farmaceutico
        // sabe exatamente o que tem na prateleira), entao ele tem prioridade quando
        // preenchido; a base so complementa o que o cadastro deixou em branco.
        String listaControlada = produto != null && produto.getListaControlada() != null
                && !produto.getListaControlada().isBlank() ? produto.getListaControlada()
                        : (med != null ? med.listaControlada : null);
        boolean exigeReceita = produto != null && Boolean.TRUE.equals(produto.getExigeReceita());
        String referencia = med != null ? med.nome
                : (produto != null && produto.getPrincipioAtivo() != null && !produto.getPrincipioAtivo().isBlank()
                        ? produto.getPrincipioAtivo()
                        : nome(item));

        if (altaVigilancia) {
            alertas.add(alerta("FARM_ALTA_VIGILANCIA", "MAJOR", "ALTA_VIGILANCIA",
                    "Medicamento potencialmente perigoso",
                    referencia + " consta como medicamento potencialmente perigoso (alta vigilancia, ISMP). "
                            + (med != null && med.notaGeral != null ? med.notaGeral : ""),
                    "Aplicar dupla checagem: um segundo profissional confere medicamento, concentracao, dose e paciente antes da entrega.",
                    nome(item), null, false, List.of("alta_vigilancia", "ismp", "dupla_checagem")));
        }

        if (listaControlada != null && !listaControlada.isBlank()) {
            alertas.add(alerta("FARM_CONTROLE_ESPECIAL", "MAJOR", "CONTROLE_ESPECIAL",
                    "Substancia sob controle especial",
                    referencia + " pertence a lista " + listaControlada + " da Portaria SVS/MS 344/1998.",
                    "Reter o receituario ou a notificacao correspondente e escriturar a saida no registro de controlados.",
                    nome(item), null, false, List.of("portaria_344", "controle_especial")));
        }

        if (antimicrobiano) {
            alertas.add(alerta("FARM_ANTIMICROBIANO", "MINOR", "ANTIMICROBIANO", "Antimicrobiano",
                    referencia + " e antimicrobiano sujeito a controle da RDC ANVISA 20/2011.",
                    "Reter a segunda via da receita, conferir se ha indicacao e tempo de tratamento definidos e registrar a dispensacao.",
                    nome(item), null, false, List.of("rdc_20", "antimicrobiano")));
        }

        if (exigeReceita && !altaVigilancia) {
            alertas.add(alerta("FARM_RETENCAO_RECEITA", "MINOR", "ORIENTACAO", "Retencao de receituario",
                    "O cadastro deste produto exige retencao do receituario na dispensacao.",
                    "Reter a receita conforme o cadastro do produto.", nome(item), null, false, List.of("receita")));
        }

        if (med != null && med.notaGeral != null && !med.notaGeral.isBlank() && !altaVigilancia) {
            alertas.add(alerta("FARM_ORIENTACAO", "MINOR", "ORIENTACAO", "Orientacao ao paciente",
                    med.notaGeral, "Repassar a orientacao ao paciente ou ao cuidador no momento da entrega.",
                    nome(item), null, false, List.of("orientacao")));
        }
    }

    /**
     * Confere se a quantidade entregue cobre o tratamento. Nao e regra clinica
     * fina: e a conferencia aritmetica que evita o paciente voltar em tres dias
     * porque levou comprimidos de menos.
     */
    private void avaliarCoerenciaQuantidade(ItemDispensacaoDto item, List<AlertaFarmaceuticoDto> alertas) {
        Integer quantidade = item.getQuantidade();
        Integer duracao = item.getDuracao();
        Double tomadasDia = tomadasPorDia(item.getFrequencia());
        if (quantidade == null || quantidade <= 0 || duracao == null || duracao <= 0 || tomadasDia == null) {
            return;
        }
        double necessario = tomadasDia * duracao;
        if (quantidade + 0.001 < necessario) {
            alertas.add(alerta("FARM_QUANTIDADE_INSUFICIENTE", "MAJOR", "DOSE", "Quantidade nao cobre o tratamento",
                    "Para " + duracao + " dia(s) em " + formatar(tomadasDia)
                            + " tomada(s) por dia seriam necessarias cerca de " + formatar(necessario)
                            + " unidades, e a dispensacao registra " + quantidade + ".",
                    "Ajustar a quantidade ou registrar por que a entrega e parcial.", nome(item), null, false,
                    List.of("quantidade", "aderencia")));
        }
    }

    // ------------------------------------------------------------------
    // Interacoes e duplicidade
    // ------------------------------------------------------------------

    private void avaliarInteracoes(ValidacaoDispensacaoRequest request, List<ItemDispensacaoDto> itens,
            List<Medicamento> resolvidos, List<AlertaFarmaceuticoDto> alertas) {

        // Entre os itens da propria dispensacao.
        for (int i = 0; i < itens.size(); i++) {
            Medicamento a = resolvidos.get(i);
            if (a == null) {
                continue;
            }
            for (int j = i + 1; j < itens.size(); j++) {
                Medicamento b = resolvidos.get(j);
                if (b == null) {
                    continue;
                }
                Interacao achada = buscarInteracao(a, b);
                if (achada != null) {
                    alertas.add(montarInteracao(achada, nome(itens.get(i)), nome(itens.get(j)), "nesta dispensacao"));
                }
            }
        }

        // Entre cada item e o que o paciente ja usa.
        for (String emUsoBruto : request.getMedicamentosEmUso()) {
            Medicamento emUso = base.identificar(emUsoBruto);
            if (emUso == null) {
                continue;
            }
            for (int i = 0; i < itens.size(); i++) {
                Medicamento a = resolvidos.get(i);
                if (a == null || a == emUso) {
                    continue;
                }
                Interacao achada = buscarInteracao(a, emUso);
                if (achada != null) {
                    alertas.add(montarInteracao(achada, nome(itens.get(i)), emUsoBruto.trim(), "em uso pelo paciente"));
                }
            }
        }
    }

    private Interacao buscarInteracao(Medicamento x, Medicamento y) {
        if (x == y) {
            return null;
        }
        for (Interacao i : base.getInteracoes()) {
            if (i.porClasse) {
                boolean direto = x.classes.contains(i.a) && y.classes.contains(i.b);
                boolean inverso = y.classes.contains(i.a) && x.classes.contains(i.b);
                if (direto || inverso) {
                    return i;
                }
            } else {
                boolean direto = bate(x, i.a) && bate(y, i.b);
                boolean inverso = bate(y, i.a) && bate(x, i.b);
                if (direto || inverso) {
                    return i;
                }
            }
        }
        return null;
    }

    private boolean bate(Medicamento m, String termo) {
        if (termo == null || termo.isBlank()) {
            return false;
        }
        return m.termos.contains(termo) || m.classes.contains(termo)
                || m.termos.stream().anyMatch(t -> t.length() >= 4 && termo.contains(t));
    }

    private AlertaFarmaceuticoDto montarInteracao(Interacao i, String origem, String alvo, String contexto) {
        boolean critico = "CRITICAL".equalsIgnoreCase(i.severidade);
        return alerta("FARM_INTERACAO", severidade(i.severidade, "MAJOR"), "INTERACAO",
                critico ? "Interacao medicamentosa critica" : "Interacao medicamentosa",
                origem + " com " + alvo + " (" + contexto + "). " + i.mecanismo,
                i.conduta, origem, alvo, critico, List.of("interacao"));
    }

    private void avaliarDuplicidade(List<ItemDispensacaoDto> itens, List<Medicamento> resolvidos,
            List<AlertaFarmaceuticoDto> alertas) {
        for (Duplicidade d : base.getDuplicidades()) {
            List<String> naClasse = new ArrayList<>();
            Set<String> substancias = new LinkedHashSet<>();
            for (int i = 0; i < itens.size(); i++) {
                Medicamento m = resolvidos.get(i);
                if (m != null && m.classes.contains(d.classe) && substancias.add(m.nome)) {
                    naClasse.add(nome(itens.get(i)));
                }
            }
            if (naClasse.size() > 1) {
                alertas.add(alerta("FARM_DUPLICIDADE", severidade(d.severidade, "MAJOR"), "DUPLICIDADE",
                        "Duplicidade terapeutica",
                        "Dois ou mais itens da mesma classe (" + d.rotulo + "): " + String.join(", ", naClasse) + ". "
                                + d.conduta,
                        "Confirmar com o prescritor se a associacao e intencional.", naClasse.get(0),
                        naClasse.get(1), "CRITICAL".equalsIgnoreCase(d.severidade), List.of("duplicidade")));
            }
        }
    }

    // ------------------------------------------------------------------
    // Utilitarios
    // ------------------------------------------------------------------

    private Double extrairDoseMg(String dose) {
        if (dose == null) {
            return null;
        }
        Matcher m = DOSE_MASSA.matcher(dose);
        if (!m.find()) {
            return null;
        }
        double valor = Double.parseDouble(m.group(1).replace(',', '.'));
        String unidade = m.group(2).toLowerCase(Locale.ROOT);
        if ("g".equals(unidade)) {
            return valor * 1000d;
        }
        if ("mcg".equals(unidade) || "ug".equals(unidade)) {
            return valor / 1000d;
        }
        return valor;
    }

    private Double doseDiaria(Double doseMg, String frequencia) {
        if (doseMg == null) {
            return null;
        }
        Double tomadas = tomadasPorDia(frequencia);
        return tomadas == null ? null : doseMg * tomadas;
    }

    /** Quantas tomadas por dia a frequencia representa. null quando nao da para afirmar. */
    private Double tomadasPorDia(String frequencia) {
        if (frequencia == null || frequencia.isBlank()) {
            return null;
        }
        // A normalizacao da base descarta a barra, e sem ela "8/8h" vira "8 8h"
        // e nenhum intervalo e reconhecido. Aqui a barra precisa sobreviver.
        String valor = Normalizer.normalize(frequencia.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .trim();

        Matcher vezes = VEZES_AO_DIA.matcher(valor);
        if (vezes.find()) {
            double n = Double.parseDouble(vezes.group(1));
            return n > 0 && n <= 24 ? n : null;
        }
        Matcher fracionado = INTERVALO_FRACIONADO.matcher(valor);
        if (fracionado.find()) {
            double horas = Double.parseDouble(fracionado.group(1));
            return horas > 0 && horas <= 24 ? 24d / horas : null;
        }
        Matcher aCada = INTERVALO_A_CADA.matcher(valor);
        if (aCada.find()) {
            double horas = Double.parseDouble(aCada.group(1).replace(',', '.'));
            return horas > 0 && horas <= 24 ? 24d / horas : null;
        }
        if (valor.contains("dose unica") || valor.contains("1x dia") || valor.contains("uma vez ao dia")) {
            return 1d;
        }
        return null;
    }

    private String nome(ItemDispensacaoDto item) {
        String n = item.getProdutoNome();
        return n == null || n.isBlank() ? item.identificacaoClinica() : n.trim();
    }

    private String severidade(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor.toUpperCase(Locale.ROOT);
    }

    private int ordem(String severidade) {
        if (severidade == null) {
            return 3;
        }
        return switch (severidade.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 0;
            case "MAJOR" -> 1;
            case "MINOR" -> 2;
            default -> 3;
        };
    }

    private String formatar(double valor) {
        if (Math.abs(valor - Math.rint(valor)) < 0.01) {
            return String.valueOf((long) Math.rint(valor));
        }
        return String.format(Locale.forLanguageTag("pt-BR"), "%.2f", valor);
    }

    private String formatarIdade(double anos) {
        if (anos < 1) {
            return (int) Math.round(anos * 12) + " meses";
        }
        return formatar(anos) + " anos";
    }

    private String descreverIdade(Integer anos, Integer meses) {
        if (anos != null && anos < 1 && meses != null) {
            return meses + " meses";
        }
        return anos == null ? "idade nao informada" : anos + " anos";
    }

    private AlertaFarmaceuticoDto alerta(String codigo, String severidade, String categoria, String titulo,
            String mensagem, String conduta, String medicamento, String relacionadoA, boolean exigeJustificativa,
            List<String> fatores) {
        return new AlertaFarmaceuticoDto(codigo, severidade, categoria, titulo, mensagem, conduta, medicamento,
                relacionadoA, exigeJustificativa, fatores);
    }
}
