package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.TissConta;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TissContaRepository implements PanacheRepository<TissConta> {

    public List<TissConta> listarPorPeriodoUnidade(String dataInicial, String dataFinal, String unidade) {
        return getEntityManager()
                .createNativeQuery("""
                        select *
                          from tiss_conta
                         where unidade = ?3
                           and case
                                 when dataabertura ~ '^\\d{4}-\\d{2}-\\d{2}' then cast(substring(dataabertura, 1, 10) as date)
                                 when dataabertura ~ '^\\d{2}/\\d{2}/\\d{4}' then to_date(substring(dataabertura, 1, 10), 'DD/MM/YYYY')
                                 else null
                               end between cast(?1 as date) and cast(?2 as date)
                         order by idtissconta desc
                        """, TissConta.class)
                .setParameter(1, dataInicial)
                .setParameter(2, dataFinal)
                .setParameter(3, unidade == null ? null : unidade.toUpperCase())
                .getResultList();
    }

    public List<TissConta> listarPorBam(int bam) {
        return find("bam = ?1 order by idtissconta desc", bam).list();
    }
}
