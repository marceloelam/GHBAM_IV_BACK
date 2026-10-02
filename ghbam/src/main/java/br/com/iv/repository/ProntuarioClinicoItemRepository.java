package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.ProntuarioClinicoItem;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProntuarioClinicoItemRepository implements PanacheRepositoryBase<ProntuarioClinicoItem, Integer> {

    public List<ProntuarioClinicoItem> listarPorPaciente(int idpaciente, String unidade) {
        if (unidade == null || unidade.isBlank() || "TODAS".equalsIgnoreCase(unidade)) {
            return find("idpaciente = ?1 order by tipo, atualizadoEm desc", idpaciente).list();
        }
        return find("idpaciente = ?1 and unidade = ?2 order by tipo, atualizadoEm desc", idpaciente, unidade).list();
    }

    public List<ProntuarioClinicoItem> listarPorPacienteTipo(int idpaciente, String tipo, String unidade) {
        if (unidade == null || unidade.isBlank() || "TODAS".equalsIgnoreCase(unidade)) {
            return find("idpaciente = ?1 and tipo = ?2 order by atualizadoEm desc", idpaciente, tipo).list();
        }
        return find("idpaciente = ?1 and tipo = ?2 and unidade = ?3 order by atualizadoEm desc", idpaciente, tipo, unidade).list();
    }
}
