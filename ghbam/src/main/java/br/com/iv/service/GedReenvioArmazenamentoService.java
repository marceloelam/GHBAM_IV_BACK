package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.iv.entity.GedDocumento;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Quando o MinIO falha no upload, o GED grava o anexo no banco para nao perder o
 * documento. Este servico confere o MinIO ao subir (erro claro no log se a
 * configuracao estiver errada) e, de tempos em tempos, reenvia ao MinIO os anexos
 * que cairam no banco nos ultimos dias. Anexos antigos, de antes do MinIO, ficam
 * no banco de proposito.
 */
@ApplicationScoped
public class GedReenvioArmazenamentoService {

    private static final Logger LOG = Logger.getLogger(GedReenvioArmazenamentoService.class);
    private static final int LOTE = 50;

    @Inject ArmazenamentoArquivoService armazenamento;

    @ConfigProperty(name = "ghbam.ged.reenvio-minio.dias", defaultValue = "7")
    int dias;

    /** Mesma chave usada no upload do GedDocumentoController. */
    public static String chave(GedDocumento x) {
        return "ged/" + ArmazenamentoArquivoService.pastaUnidade(x.unidade) + "/"
                + ArmazenamentoArquivoService.pastaPaciente(x.nomePaciente, x.idpaciente, x.prontuario, x.idbam) + x.uuid;
    }

    void aoSubir(@Observes StartupEvent evento) {
        if (!armazenamento.habilitado()) return;
        Thread.ofVirtual().name("minio-verificar").start(() -> {
            try {
                armazenamento.verificar();
                LOG.infof("Armazenamento: MinIO OK em %s", armazenamento.endpoint());
            } catch (RuntimeException e) {
                LOG.errorf("Armazenamento: MinIO NAO esta funcionando (%s). Anexos irao para o banco ate corrigir: %s",
                        armazenamento.endpoint(), e.getMessage());
            }
        });
    }

    @Scheduled(every = "10m", delayed = "2m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reenviarPendentes() {
        if (!armazenamento.habilitado() || dias <= 0) return;
        LocalDateTime desde = LocalDateTime.now().minusDays(dias);
        List<Integer> ids = QuarkusTransaction.requiringNew().call(() ->
                GedDocumento.<GedDocumento>find("status = 'ATIVO' and armazenamentoChave is null"
                        + " and conteudoBase64 is not null and criadoEm >= ?1 order by id", desde)
                        .page(0, LOTE).list().stream().map(d -> d.id).toList());
        if (ids.isEmpty()) return;
        int enviados = 0;
        for (Integer id : ids) {
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    GedDocumento d = GedDocumento.findById(id);
                    if (d == null || d.armazenamentoChave != null || d.conteudoBase64 == null) return;
                    byte[] conteudo = Base64.getMimeDecoder().decode(d.conteudoBase64);
                    d.armazenamentoChave = armazenamento.salvar(chave(d), conteudo, d.tipoMime);
                    d.conteudoBase64 = null;
                });
                enviados++;
            } catch (RuntimeException e) {
                // MinIO ainda fora: para o lote e tenta na proxima rodada.
                LOG.warnf("GED: reenvio ao MinIO falhou no anexo %d, nova tentativa em 10 min: %s", id, e.getMessage());
                break;
            }
        }
        if (enviados > 0) LOG.infof("GED: %d anexo(s) que estavam no banco enviados ao MinIO", enviados);
    }
}
