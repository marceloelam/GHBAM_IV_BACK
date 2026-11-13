package br.com.iv.scheduler;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.iv.controller.VigilanciaEpidemiologicaController;
import br.com.iv.entity.ChatMensagem;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.core.Response;

/**
 * Alerta epidemiologico automatico: varre as unidades com atendimento recente, usa o mesmo calculo de
 * "emergentes" da tela (2x a media das 4 janelas anteriores) e, so para nivel ALTO, deixa uma mensagem
 * URGENTE na sala da unidade (o aviso do chat ja pede "Ciente"). Sem repeticao no mesmo dia.
 * So agregados, sem paciente; o texto deixa claro que nao confirma surto. Desligado por padrao.
 */
@ApplicationScoped
public class VigilanciaAlertaAutomaticoJob {

    private static final Logger LOG = Logger.getLogger(VigilanciaAlertaAutomaticoJob.class);
    private static final String REMETENTE = "SISTEMA-VIGILANCIA";

    @ConfigProperty(name = "ghbam.vigilancia.alerta-automatico.enabled", defaultValue = "false")
    boolean ativo;

    @Inject EntityManager em;
    @Inject VigilanciaEpidemiologicaController vigilancia;

    @Scheduled(cron = "{ghbam.vigilancia.alerta-automatico.cron:0 0 */3 * * ?}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void varrer() {
        LocalDate fim = LocalDate.now();
        LocalDate inicio = fim.minusDays(6);
        // Cada unidade liga/desliga o seu alerta na pagina da vigilancia; sem escolha, vale o padrao do servidor.
        // So as unidades ligadas recebem aviso, e so na propria sala.
        List<String> ligadas = new java.util.ArrayList<>();
        for (String unidade : unidadesComMovimento(inicio)) {
            try {
                if (vigilancia.alertaAutomaticoAtivo(unidade, ativo)) ligadas.add(unidade);
            } catch (RuntimeException e) {
                LOG.warnf("[VIGILANCIA-AUTO] nao foi possivel ler a chave da unidade %s: %s", unidade, e.getMessage());
            }
        }
        if (ligadas.isEmpty()) return; // nenhuma unidade ligou: nem faz a varredura
        int avisos = 0;
        for (String unidade : ligadas) {
            try {
                avisos += avisarUnidade(unidade, inicio, fim);
            } catch (RuntimeException e) {
                LOG.warnf("[VIGILANCIA-AUTO] falha na unidade %s: %s", unidade, e.getMessage());
            }
        }
        try {
            avisos += avisarMunicipio(fim, ligadas);
        } catch (RuntimeException e) {
            LOG.warnf("[VIGILANCIA-AUTO] falha no painel do municipio: %s", e.getMessage());
        }
        if (avisos > 0) LOG.infof("[VIGILANCIA-AUTO] %d alerta(s) urgente(s) emitido(s).", avisos);
    }

    @SuppressWarnings("unchecked")
    private List<String> unidadesComMovimento(LocalDate inicio) {
        return QuarkusTransaction.requiringNew().call(() ->
                (List<String>) em.createNativeQuery(
                        "SELECT DISTINCT b.unidade FROM bam b WHERE CAST(b.data AS date) >= :inicio "
                                + "AND COALESCE(TRIM(b.unidade), '') <> ''")
                        .setParameter("inicio", inicio).getResultList());
    }

    private int avisarUnidade(String unidade, LocalDate inicio, LocalDate fim) {
        if (!vigilancia.alertaAtivoParaJob(unidade)) return 0;
        Response resposta = vigilancia.emergentes(inicio.toString(), fim.toString(), unidade, "TODOS");
        if (resposta.getStatus() != 200 || !(resposta.getEntity() instanceof Map<?, ?> dados)
                || !(dados.get("itens") instanceof List<?> itens)) {
            return 0;
        }
        int emitidos = 0;
        for (Object o : itens) {
            if (!(o instanceof Map<?, ?> item) || !"ALTO".equals(item.get("nivel"))) continue;
            String rotulo = String.valueOf(item.get("rotulo"));
            String texto = "ALERTA EPIDEMIOLÓGICO: aumento de casos de \"" + rotulo + "\" nos últimos 7 dias ("
                    + item.get("casos") + " casos; média anterior " + item.get("mediaAnterior")
                    + "). Aumento estatístico por agrupamento de texto/CID: exige validação humana e não confirma surto. "
                    + "Veja em Vigilância Epidemiológica.";
            if (emitir(unidade, texto)) emitidos++;
        }
        return emitidos;
    }

    /**
     * Visao do municipio (ultimos 14 dias contra os 14 anteriores): POSSIVEL_SURTO ou POSSIVEL_EPIDEMIA em
     * uma doenca avisa a sala das unidades que tem casos dela E ligaram o alerta automatico. So agregados, sem paciente.
     */
    private int avisarMunicipio(LocalDate fim, List<String> unidadesLigadas) {
        Response resposta = vigilancia.painelMunicipioParaJob(fim.minusDays(13), fim);
        if (resposta.getStatus() != 200 || !(resposta.getEntity() instanceof Map<?, ?> dados)
                || !(dados.get("escalaDoencas") instanceof List<?> escalas)
                || !(dados.get("unidades") instanceof List<?> unidades)) {
            return 0;
        }
        int emitidos = 0;
        for (Object o : escalas) {
            if (!(o instanceof Map<?, ?> item)) continue;
            Object escala = item.get("escala");
            boolean epidemia = "POSSIVEL_EPIDEMIA".equals(escala);
            if (!epidemia && !"POSSIVEL_SURTO".equals(escala)) continue;
            String doenca = String.valueOf(item.get("doenca"));
            String texto = "ALERTA EPIDEMIOLÓGICO MUNICIPAL: " + (epidemia ? "possibilidade de epidemia" : "possível surto")
                    + " de \"" + doenca + "\" no município (" + item.get("casos") + " casos suspeitos em 14 dias; período anterior "
                    + item.get("anterior") + "; " + item.get("unidadesAfetadas") + " unidades). "
                    + "Sinal estatístico: exige validação humana e não confirma surto nem epidemia. "
                    + "Veja em Vigilância Epidemiológica > Mapa > Município.";
            for (Object u : unidades) {
                if (!(u instanceof Map<?, ?> un) || !(un.get("doencas") instanceof List<?> lista)) continue;
                boolean temCasos = lista.stream().anyMatch(d -> d instanceof Map<?, ?> dm
                        && doenca.equals(String.valueOf(dm.get("nome")))
                        && dm.get("casos") instanceof Number n && n.longValue() > 0);
                String unidade = String.valueOf(un.get("unidade"));
                // So a unidade que ligou o alerta recebe o aviso do municipio, na propria sala.
                if (temCasos && unidadesLigadas.contains(unidade) && emitir(unidade, texto)) emitidos++;
            }
        }
        return emitidos;
    }

    /** Um aviso por agravo e unidade por dia; devolve false se ja houve hoje. */
    private boolean emitir(String unidade, String texto) {
        return QuarkusTransaction.requiringNew().call(() -> {
            long jaHoje = ChatMensagem.count(
                    "unidade = ?1 and remetenteMatricula = ?2 and mensagem = ?3 and criadoEm >= ?4",
                    unidade, REMETENTE, texto, LocalDate.now().atStartOfDay());
            if (jaHoje > 0) return false;
            ChatMensagem m = new ChatMensagem();
            m.setTipo("room");
            m.setSala("geral");
            m.setUnidade(unidade);
            m.setMensagem(texto);
            m.setRemetenteMatricula(REMETENTE);
            m.setRemetenteNome("Vigilância Epidemiológica (automático)");
            m.setRemetenteCargo("Sistema");
            m.setRemetenteFoto("");
            m.setDestinatarioMatricula("");
            m.setDestinatarioNome("");
            m.setDestinatarioFoto("");
            m.setPrioridade("URGENTE");
            m.persist();
            return true;
        });
    }
}
