package br.com.iv.pharmacia.validacao.service;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Carrega e indexa a base de conhecimento farmaceutico
 * (/farmacia/base-conhecimento-farmaceutico.json).
 *
 * A base fica em arquivo, e nao em constantes Java, porque quem a mantem e o
 * farmaceutico: incluir uma interacao nova nao deve exigir alterar codigo. O
 * indice e construido uma unica vez na subida da aplicacao.
 */
@ApplicationScoped
public class BaseConhecimentoFarmaceutico {

    public static final String RECURSO = "/farmacia/base-conhecimento-farmaceutico.json";

    /** Uma substancia da base, ja normalizada. */
    public static class Medicamento {
        public String nome;
        public Set<String> termos = new LinkedHashSet<>();
        public Set<String> classes = new LinkedHashSet<>();
        public String atc;
        public Double doseMaximaDiaMg;
        /** Limite por peso (mg/kg/dia); vale junto com doseMaximaDiaMg quando ambos existem. */
        public Double doseMaximaMgKgDia;
        /** Motivo de nao haver teto diario fixo (titulado, topico, UI...); null quando ha limite. */
        public String semTetoFixo;
        /** Dose maxima diaria por kg abaixo de 18 anos; o teto de adulto continua valendo. */
        public Double pediatriaMgKgDia;
        public boolean altaVigilancia;
        public boolean antimicrobiano;
        public boolean exigePeso;
        public String listaControlada;
        public Double idadeMinimaAnos;
        public String idadeMinimaNota;
        public String gestacaoRisco;
        public String gestacaoNota;
        public String beersNota;
        public String notaGeral;
        public Double renalLimiteTfg;
        public String renalConduta;
        /** Limite de dose ainda nao validado com CRF/CRM: o alerta de dose vira aviso, sem bloquear. */
        public boolean limitePendente;
    }

    public static class Interacao {
        public String a;
        public String b;
        public boolean porClasse;
        public String severidade;
        public String mecanismo;
        public String conduta;
    }

    public static class AlergiaCruzada {
        public String grupo;
        public String classeAlergia;
        public String classeMedicamento;
        public String severidade;
        public String nota;
    }

    public static class Duplicidade {
        public String classe;
        public String rotulo;
        public String severidade;
        public String conduta;
    }

    private String versao = "indisponivel";
    private final List<Medicamento> medicamentos = new CopyOnWriteArrayList<>();
    private final List<Interacao> interacoes = new ArrayList<>();
    private final List<AlergiaCruzada> alergiasCruzadas = new ArrayList<>();
    private final List<Duplicidade> duplicidades = new ArrayList<>();
    /** Termo normalizado para medicamento. Termos mais longos sao testados primeiro no casamento. */
    private final Map<String, Medicamento> porTermo = new ConcurrentHashMap<>();
    private volatile List<String> termosOrdenados = List.of();

    @PostConstruct
    void carregar() {
        try (InputStream in = BaseConhecimentoFarmaceutico.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                System.err.println("[ValidacaoFarmaceutica] Base de conhecimento nao encontrada em " + RECURSO
                        + ". A validacao respondera sem regras ate que o arquivo seja publicado.");
                return;
            }
            JsonNode raiz = new ObjectMapper().readTree(in);
            versao = texto(raiz.get("versao"));

            for (JsonNode no : opcional(raiz.get("medicamentos"))) {
                Medicamento m = new Medicamento();
                m.nome = texto(no.get("nome"));
                m.termos.add(normalizar(m.nome));
                for (JsonNode s : opcional(no.get("sinonimos"))) {
                    String termo = normalizar(s.asText());
                    if (!termo.isBlank()) {
                        m.termos.add(termo);
                    }
                }
                for (JsonNode c : opcional(no.get("classes"))) {
                    m.classes.add(normalizar(c.asText()));
                }
                m.atc = texto(no.get("atc"));
                m.doseMaximaDiaMg = decimal(no.get("doseMaximaDiaMg"));
                m.doseMaximaMgKgDia = decimal(no.get("doseMaximaMgKgDia"));
                m.semTetoFixo = texto(no.get("semTetoFixo"));
                m.pediatriaMgKgDia = decimal(no.get("pediatriaMgKgDia"));
                m.altaVigilancia = no.path("altaVigilancia").asBoolean(false);
                m.antimicrobiano = no.path("antimicrobiano").asBoolean(false);
                m.exigePeso = no.path("exigePeso").asBoolean(false);
                m.listaControlada = texto(no.get("listaControlada"));
                m.idadeMinimaAnos = decimal(no.get("idadeMinimaAnos"));
                m.idadeMinimaNota = texto(no.get("idadeMinimaNota"));
                m.gestacaoRisco = texto(no.get("gestacaoRisco"));
                m.gestacaoNota = texto(no.get("gestacaoNota"));
                m.beersNota = texto(no.get("beersNota"));
                m.notaGeral = texto(no.get("notaGeral"));
                JsonNode renal = no.get("renal");
                if (renal != null && !renal.isNull()) {
                    m.renalLimiteTfg = decimal(renal.get("limiteTfg"));
                    m.renalConduta = texto(renal.get("conduta"));
                }
                medicamentos.add(m);
                for (String termo : m.termos) {
                    porTermo.putIfAbsent(termo, m);
                }
            }

            for (JsonNode no : opcional(raiz.get("interacoes"))) {
                Interacao i = new Interacao();
                i.a = normalizar(texto(no.get("a")));
                i.b = normalizar(texto(no.get("b")));
                i.porClasse = no.path("porClasse").asBoolean(false);
                i.severidade = texto(no.get("severidade"));
                i.mecanismo = texto(no.get("mecanismo"));
                i.conduta = texto(no.get("conduta"));
                interacoes.add(i);
            }

            for (JsonNode no : opcional(raiz.get("alergiasCruzadas"))) {
                AlergiaCruzada a = new AlergiaCruzada();
                a.grupo = texto(no.get("grupo"));
                a.classeAlergia = normalizar(texto(no.get("classeAlergia")));
                a.classeMedicamento = normalizar(texto(no.get("classeMedicamento")));
                a.severidade = texto(no.get("severidade"));
                a.nota = texto(no.get("nota"));
                alergiasCruzadas.add(a);
            }

            for (JsonNode no : opcional(raiz.get("duplicidades"))) {
                Duplicidade d = new Duplicidade();
                d.classe = normalizar(texto(no.get("classe")));
                d.rotulo = texto(no.get("rotulo"));
                d.severidade = texto(no.get("severidade"));
                d.conduta = texto(no.get("conduta"));
                duplicidades.add(d);
            }

            reindexarTermos();

            System.out.println("[ValidacaoFarmaceutica] Base " + versao + " carregada: " + medicamentos.size()
                    + " medicamentos, " + interacoes.size() + " interacoes, " + duplicidades.size() + " duplicidades.");
        } catch (Exception e) {
            System.err.println("[ValidacaoFarmaceutica] Falha ao carregar a base de conhecimento: " + e.getMessage());
        }
    }

    /**
     * Encontra a substancia correspondente a um texto livre (nome comercial,
     * principio ativo, descricao do estoque). Testa os termos mais longos
     * primeiro para que "amoxicilina + clavulanato" nao seja reduzido a
     * "amoxicilina".
     */
    public Medicamento identificar(String valor) {
        String alvo = normalizar(valor);
        if (alvo.isBlank()) {
            return null;
        }
        Medicamento exato = porTermo.get(alvo);
        if (exato != null) {
            return exato;
        }
        for (String termo : termosOrdenados) {
            if (termo.length() >= 4 && alvo.contains(termo)) {
                return porTermo.get(termo);
            }
        }
        return null;
    }

    /**
     * Inclui (ou substitui pelo nome) uma substancia cadastrada pela tela de
     * limites de dose; passa a valer na hora, sem reiniciar.
     */
    public synchronized void adicionar(Medicamento m) {
        medicamentos.removeIf(x -> x.nome.equals(m.nome));
        medicamentos.add(m);
        reindexarTermos();
    }

    /** Retira uma substancia incluida pela tela. */
    public synchronized boolean remover(String nome) {
        boolean removido = medicamentos.removeIf(x -> x.nome.equals(nome));
        if (removido) {
            reindexarTermos();
        }
        return removido;
    }

    private synchronized void reindexarTermos() {
        Map<String, Medicamento> indice = new HashMap<>();
        for (Medicamento m : medicamentos) {
            for (String termo : m.termos) {
                indice.putIfAbsent(termo, m);
            }
        }
        porTermo.keySet().retainAll(indice.keySet());
        porTermo.putAll(indice);
        List<String> ordenados = new ArrayList<>(indice.keySet());
        ordenados.sort((x, y) -> Integer.compare(y.length(), x.length()));
        termosOrdenados = List.copyOf(ordenados);
    }

    public String getVersao() { return versao; }
    public List<Medicamento> getMedicamentos() { return medicamentos; }
    public List<Interacao> getInteracoes() { return interacoes; }
    public List<AlergiaCruzada> getAlergiasCruzadas() { return alergiasCruzadas; }
    public List<Duplicidade> getDuplicidades() { return duplicidades; }

    /** Minusculas, sem acento e sem pontuacao redundante: a forma usada em todo o casamento. */
    public static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(valor.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.replaceAll("[^a-z0-9+ ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static Iterable<JsonNode> opcional(JsonNode no) {
        return no == null || !no.isArray() ? List.of() : no;
    }

    private static String texto(JsonNode no) {
        return no == null || no.isNull() ? "" : no.asText("").trim();
    }

    private static Double decimal(JsonNode no) {
        return no == null || no.isNull() ? null : no.asDouble();
    }
}
