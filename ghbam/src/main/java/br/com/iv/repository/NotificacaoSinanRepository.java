package br.com.iv.repository;

import java.util.List;
import br.com.iv.entity.NotificacaoSinan;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotificacaoSinanRepository implements PanacheRepositoryBase<NotificacaoSinan, Long> {
    public List<NotificacaoSinan> listarPorUnidade(String unidade) {
        String termo = normalizarUnidade(unidade);
        return find("(upper(trim(unidade)) = upper(?1) or upper(trim(unidade)) like upper(concat(?1, ':%'))) order by dataNotificacao desc", termo).list();
    }

    public List<NotificacaoSinan> listarPendenciasPorUnidade(String unidade) {
        String termo = normalizarUnidade(unidade);
        return find("(upper(trim(unidade)) = upper(?1) or upper(trim(unidade)) like upper(concat(?1, ':%'))) and status in (?2) order by prazoNotificacao asc nulls last, dataNotificacao desc",
                termo, List.of("PRONTO", "ENCAMINHADO", "RECEBIDO")).list();
    }

    private String normalizarUnidade(String unidade) {
        return unidade == null ? "" : unidade.split(":", 2)[0].trim();
    }
}
