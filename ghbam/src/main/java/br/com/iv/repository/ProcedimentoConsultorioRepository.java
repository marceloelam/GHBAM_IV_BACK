package br.com.iv.repository;

import java.util.List;
import java.util.Optional;

import br.com.iv.entity.ProcedimentoConsultorio;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcedimentoConsultorioRepository implements PanacheRepository<ProcedimentoConsultorio> {

    public List<ProcedimentoConsultorio> listarPorUnidade(String unidade, boolean apenasAtivos) {
        if (apenasAtivos) {
            return find("upper(unidade) = ?1 and ativo = 'S' order by nome", normalizarUnidade(unidade)).list();
        }
        return find("upper(unidade) = ?1 order by ativo desc, nome", normalizarUnidade(unidade)).list();
    }

    public boolean existeCodigoNaUnidade(String unidade, String codigo) {
        return count("upper(unidade) = ?1 and upper(codigo) = ?2", normalizarUnidade(unidade), normalizarUnidade(codigo)) > 0;
    }

    public Optional<ProcedimentoConsultorio> buscarPorCodigoNaUnidade(String unidade, String codigo) {
        return find("upper(unidade) = ?1 and upper(codigo) = ?2", normalizarUnidade(unidade), normalizarUnidade(codigo))
                .firstResultOptional();
    }

    private String normalizarUnidade(String unidade) {
        return String.valueOf(unidade == null ? "" : unidade).trim().toUpperCase();
    }
}
