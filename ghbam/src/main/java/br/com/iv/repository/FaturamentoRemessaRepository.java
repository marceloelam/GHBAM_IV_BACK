package br.com.iv.repository;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import br.com.iv.entity.FaturamentoRemessa;

@ApplicationScoped
public class FaturamentoRemessaRepository implements PanacheRepository<FaturamentoRemessa> {

    public List<FaturamentoRemessa> listar(Integer idcompetencia) {
        return listar(idcompetencia, null);
    }

    public List<FaturamentoRemessa> listar(Integer idcompetencia, String unidade) {
        boolean filtrarUnidade = unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade);
        if (idcompetencia == null) {
            if (filtrarUnidade) {
                return list("upper(unidade) = ?1", Sort.by("dataGeracao").descending(), unidade.trim().toUpperCase());
            }
            return listAll(Sort.by("dataGeracao").descending());
        }
        if (filtrarUnidade) {
            return list("idcompetencia = ?1 and upper(unidade) = ?2", Sort.by("dataGeracao").descending(),
                    idcompetencia, unidade.trim().toUpperCase());
        }
        return list("idcompetencia", Sort.by("dataGeracao").descending(), idcompetencia);
    }

    // Maior sequencial ja usado por numeros que comecam com o prefixo (competencia,
    // unidade e instrumento). O sequencial sai do proprio numero, e nao de uma contagem
    // de linhas: contar por coluna reiniciava em 00001 sempre que o recorte da contagem
    // nao batia com o que ja estava gravado - remessa antiga com unidade nula, ou o
    // simples fato de o numero nao distinguir BPA-I de BPA-C - e numero e UNIQUE global,
    // entao a segunda geracao morria com "duplicar valor da chave". Lendo o maximo do
    // proprio prefixo, o proximo numero e sempre livre.
    //
    // O regex descarta numero em formato antigo/manual cujo final nao seja um sequencial
    // de cinco digitos: um cast direto derrubaria a consulta inteira por causa dele.
    public long maiorSequencialPorPrefixo(String prefixo) {
        Object maximo = getEntityManager()
                .createNativeQuery("""
                        select coalesce(max(cast(right(numero, 5) as integer)), 0)
                          from faturamento_remessa
                         where numero like :prefixo
                           and right(numero, 5) ~ '^[0-9]{5}$'
                        """)
                .setParameter("prefixo", prefixo + "%")
                .getSingleResult();
        return maximo instanceof Number valor ? valor.longValue() : 0;
    }
}
