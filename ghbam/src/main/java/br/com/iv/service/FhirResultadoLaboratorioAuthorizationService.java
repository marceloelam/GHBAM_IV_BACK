package br.com.iv.service;

import java.util.List;
import java.util.Locale;

import br.com.iv.entity.Bam;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Autoriza o acesso ao Bundle laboratorial antes de qualquer controller expor
 * identificadores ou resultados clínicos.
 */
@ApplicationScoped
public class FhirResultadoLaboratorioAuthorizationService {

    private static final List<String> PERMISSOES_CLINICAS = List.of(
            "klaboratorio", "kmedico", "kenfermagem", "pprontuariocompleto", "pfhirsumarioalta", "kadm");

    public boolean podeExportar(AuthenticatedUser usuario, Bam bam) {
        if (usuario == null || bam == null || usuario.matricula() == null || usuario.matricula().isBlank()) {
            return false;
        }
        if (!usuario.hasAnyPermission(PERMISSOES_CLINICAS)) {
            return false;
        }
        if (usuario.hasPermission("kadm") || usuario.hasPermission("ksuperusuario")
                || usuario.hasPermission("superusuario")) {
            return true;
        }
        return mesmaUnidade(usuario.unidade(), bam.getUnidade());
    }

    static boolean mesmaUnidade(String unidadeUsuario, String unidadeBam) {
        if (unidadeUsuario == null || unidadeBam == null
                || unidadeUsuario.isBlank() || unidadeBam.isBlank()) {
            return false;
        }
        return unidadeUsuario.trim().toUpperCase(Locale.ROOT)
                .equals(unidadeBam.trim().toUpperCase(Locale.ROOT));
    }
}
