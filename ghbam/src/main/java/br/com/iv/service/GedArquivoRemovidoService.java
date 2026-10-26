package br.com.iv.service;

import java.util.List;

import org.jboss.logging.Logger;

import br.com.iv.entity.GedDocumento;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Documento removido do GED nao e apagado do MinIO (prontuario tem guarda legal):
 * o arquivo sai da pasta do paciente em ged/ e vai para ged-removidos/, mesmo caminho.
 * O registro continua apontando para ele, com motivo e autor da remocao.
 */
@ApplicationScoped
public class GedArquivoRemovidoService {

    private static final Logger LOG = Logger.getLogger(GedArquivoRemovidoService.class);
    private static final String PREFIXO_ATIVO = "ged/";
    private static final String PREFIXO_REMOVIDO = "ged-removidos/";

    @Inject ArmazenamentoArquivoService armazenamento;

    /** Move o arquivo do documento e atualiza a chave. Falha no MinIO nao impede a remocao. */
    public void arquivar(GedDocumento x) {
        if (!armazenamento.habilitado() || x.armazenamentoChave == null || !x.armazenamentoChave.startsWith(PREFIXO_ATIVO)) return;
        String destino = PREFIXO_REMOVIDO + x.armazenamentoChave.substring(PREFIXO_ATIVO.length());
        try {
            armazenamento.mover(x.armazenamentoChave, destino, x.tipoMime);
            x.armazenamentoChave = destino;
        } catch (RuntimeException e) {
            LOG.warnf("GED: documento %s removido, mas o arquivo %s ficou em ged/: %s", x.id, x.armazenamentoChave, e.getMessage());
        }
    }

    // Documentos removidos antes desta regra ainda estao em ged/. Em segundo plano
    // para nao segurar a subida; cada documento na sua transacao.
    void aoSubir(@Observes StartupEvent evento) {
        if (!armazenamento.habilitado()) return;
        Thread.ofVirtual().name("ged-arquivar-removidos").start(() -> {
            try {
                List<Integer> ids = QuarkusTransaction.requiringNew().call(() ->
                        GedDocumento.<GedDocumento>find("status = 'REMOVIDO' and armazenamentoChave like ?1", PREFIXO_ATIVO + "%")
                                .list().stream().map(d -> d.id).toList());
                for (Integer id : ids) {
                    QuarkusTransaction.requiringNew().run(() -> {
                        GedDocumento d = GedDocumento.findById(id);
                        if (d != null) arquivar(d);
                    });
                }
                if (!ids.isEmpty()) LOG.infof("GED: %d documento(s) removido(s) antes movido(s) para %s", ids.size(), PREFIXO_REMOVIDO);
            } catch (RuntimeException e) {
                LOG.warnf("GED: varredura de removidos falhou: %s", e.getMessage());
            }
        });
    }
}
