package br.com.iv.repository;

import java.util.List;
import java.util.Optional;

import br.com.iv.entity.ProcedimentoConsultorioPreco;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcedimentoConsultorioPrecoRepository implements PanacheRepository<ProcedimentoConsultorioPreco> {

    public List<ProcedimentoConsultorioPreco> listarPorProcedimento(int idprocedimentoConsultorio, String unidade) {
        return find("idprocedimentoConsultorio = ?1 and upper(unidade) = ?2 order by ativo desc, tipoCobranca, convenio, plano",
                idprocedimentoConsultorio, normalizar(unidade)).list();
    }

    public Optional<ProcedimentoConsultorioPreco> buscarMelhorPreco(int idprocedimentoConsultorio, String unidade,
            String tipoCobranca, String convenio, String plano) {
        String tipo = normalizarTipo(tipoCobranca);
        String conv = normalizarValor(convenio);
        String pla = normalizarValor(plano);
        List<ProcedimentoConsultorioPreco> precos = find(
                "idprocedimentoConsultorio = ?1 and upper(unidade) = ?2 and tipoCobranca = ?3 and ativo = 'S' "
                        + "and (convenio = ?4 or convenio = 'TODOS') and (plano = ?5 or plano = 'TODOS')",
                idprocedimentoConsultorio, normalizar(unidade), tipo, conv, pla).list();

        return precos.stream()
                .sorted((a, b) -> Integer.compare(score(b, conv, pla), score(a, conv, pla)))
                .findFirst();
    }

    public boolean existeEscopo(int idprocedimentoConsultorio, String unidade, String tipoCobranca, String convenio,
            String plano) {
        return count(
                "idprocedimentoConsultorio = ?1 and upper(unidade) = ?2 and tipoCobranca = ?3 and convenio = ?4 and plano = ?5",
                idprocedimentoConsultorio, normalizar(unidade), normalizarTipo(tipoCobranca),
                normalizarValor(convenio), normalizarValor(plano)) > 0;
    }

    private int score(ProcedimentoConsultorioPreco preco, String convenio, String plano) {
        int score = 0;
        if (convenio.equals(preco.getConvenio())) {
            score += 2;
        }
        if (plano.equals(preco.getPlano())) {
            score += 1;
        }
        return score;
    }

    private String normalizar(String valor) {
        return String.valueOf(valor == null ? "" : valor).trim().toUpperCase();
    }

    private String normalizarValor(String valor) {
        String normalizado = normalizar(valor);
        return normalizado.isEmpty() || normalizado.equals("-") ? "TODOS" : normalizado;
    }

    private String normalizarTipo(String valor) {
        String tipo = normalizar(valor);
        if (tipo.equals("CONVENIO") || tipo.equals("COPARTICIPACAO") || tipo.equals("CORTESIA")) {
            return tipo;
        }
        return "PARTICULAR";
    }
}
