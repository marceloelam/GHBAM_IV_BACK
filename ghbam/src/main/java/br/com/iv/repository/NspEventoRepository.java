package br.com.iv.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import br.com.iv.entity.NspEvento;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;

@ApplicationScoped
public class NspEventoRepository implements PanacheRepositoryBase<NspEvento, Long> {

    private static final List<String> STATUS_ABERTOS = List.of("NOTIFICADO", "EM_ANALISE", "PLANO_ACAO");

    public List<NspEvento> listar(String unidade, String status, String classificacao,
            LocalDateTime inicio, LocalDateTime fim) {
        StringBuilder jpql = new StringBuilder("upper(trim(unidade)) = upper(:unidade)");
        Parameters parametros = Parameters.with("unidade", normalizarUnidade(unidade));
        if (preenchido(status)) {
            if ("ABERTOS".equalsIgnoreCase(status.trim())) {
                jpql.append(" and status in (:statusAbertos)");
                parametros = parametros.and("statusAbertos", STATUS_ABERTOS);
            } else {
                jpql.append(" and status = :status");
                parametros = parametros.and("status", status.trim().toUpperCase());
            }
        }
        if (preenchido(classificacao)) {
            jpql.append(" and classificacao = :classificacao");
            parametros = parametros.and("classificacao", classificacao.trim().toUpperCase());
        }
        if (inicio != null) {
            jpql.append(" and coalesce(dataOcorrencia, dataRegistro) >= :inicio");
            parametros = parametros.and("inicio", inicio);
        }
        if (fim != null) {
            jpql.append(" and coalesce(dataOcorrencia, dataRegistro) <= :fim");
            parametros = parametros.and("fim", fim);
        }
        return find(jpql.toString(), Sort.by("dataRegistro", Sort.Direction.Descending), parametros).list();
    }

    public NspEvento buscarNaUnidade(Long idevento, String unidade) {
        return find("idevento = ?1 and upper(trim(unidade)) = upper(?2)", idevento, normalizarUnidade(unidade))
                .firstResult();
    }

    public boolean protocoloExiste(String protocolo) {
        return count("protocolo = ?1", protocolo) > 0;
    }

    /** Contagens por status, tipo, classificacao e grau de dano no periodo, para o painel do nucleo. */
    public List<Object[]> agrupar(String campo, String unidade, LocalDateTime inicio, LocalDateTime fim) {
        String coluna = switch (campo) {
            case "status" -> "status";
            case "tipoIncidente" -> "tipoIncidente";
            case "classificacao" -> "classificacao";
            case "grauDano" -> "grauDano";
            default -> throw new IllegalArgumentException("Agrupamento nao suportado: " + campo);
        };
        StringBuilder jpql = new StringBuilder("select coalesce(e.").append(coluna).append(", 'NAO_INFORMADO'), count(e)")
                .append(" from NspEvento e where upper(trim(e.unidade)) = upper(:unidade)");
        Parameters parametros = Parameters.with("unidade", normalizarUnidade(unidade));
        if (inicio != null) {
            jpql.append(" and coalesce(e.dataOcorrencia, e.dataRegistro) >= :inicio");
            parametros = parametros.and("inicio", inicio);
        }
        if (fim != null) {
            jpql.append(" and coalesce(e.dataOcorrencia, e.dataRegistro) <= :fim");
            parametros = parametros.and("fim", fim);
        }
        jpql.append(" group by coalesce(e.").append(coluna).append(", 'NAO_INFORMADO') order by count(e) desc");
        TypedQuery<Object[]> query = getEntityManager().createQuery(jpql.toString(), Object[].class);
        parametros.map().forEach(query::setParameter);
        return new ArrayList<>(query.getResultList());
    }

    /** Casos ainda abertos com prazo de analise vencido. */
    public long contarAtrasados(String unidade, LocalDateTime referencia) {
        return count("upper(trim(unidade)) = upper(?1) and status in (?2) and prazoAnalise is not null and prazoAnalise < ?3",
                normalizarUnidade(unidade), STATUS_ABERTOS, referencia);
    }

    public long contarAbertos(String unidade) {
        return count("upper(trim(unidade)) = upper(?1) and status in (?2)", normalizarUnidade(unidade), STATUS_ABERTOS);
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private String normalizarUnidade(String unidade) {
        return unidade == null ? "" : unidade.split(":", 2)[0].trim();
    }
}
