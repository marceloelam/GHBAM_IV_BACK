package br.com.iv.repository;

import br.com.iv.entity.NotificacaoSinanHistorico;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotificacaoSinanHistoricoRepository implements PanacheRepositoryBase<NotificacaoSinanHistorico, Long> {
}
