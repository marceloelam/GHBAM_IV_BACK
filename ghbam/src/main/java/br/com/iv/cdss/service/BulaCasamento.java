package br.com.iv.cdss.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import br.com.iv.entity.Bulario;

/**
 * Escolhe a bula de um medicamento com segurança.
 *
 * A busca da tabela {@code bulario} procura o termo em todos os campos (inclusive
 * interações), então o primeiro resultado pode ser a bula de outro remédio que só
 * cita o prescrito. Aqui a bula só vale se nome/princípio ativo contiverem TODOS os
 * fármacos do termo; sais, formas e unidades não contam. Sem bula compatível,
 * devolve vazio — nunca "a primeira que apareceu".
 *
 * Mesma regra do frontend (Bulario.jsx → scoreDetailMatch).
 */
public final class BulaCasamento {

    private static final Set<String> TERMOS_GENERICOS = Set.of((
            "de da do das dos e com rename mg ml mcg ui g amp cp comp "
            + "cloridrato dicloridrato fumarato propionato maleato succinato tartarato besilato acetato sulfato bissulfato "
            + "fosfato citrato oxalato estearato valerato furoato mesilato bromidrato brometo trometamol etexilato "
            + "sodico sodica potassico potassica calcica magnesico mono di tri hidratado hidratada monoidratado monoidratada "
            + "comprimido comprimidos capsula capsulas spray nasal injetavel solucao oral suspensao gotas gota xarope creme "
            + "pomada gel frasco ampola liberacao retardada prolongada revestido revestidos").split(" "));

    private BulaCasamento() {
    }

    public static Optional<Bulario> escolher(String medicamento, List<Bulario> candidatos) {
        Set<String> farmacos = farmacos(medicamento);
        if (farmacos.isEmpty() || candidatos == null) {
            return Optional.empty();
        }
        return candidatos.stream()
                .filter(Objects::nonNull)
                .filter(b -> tokens(b.getNome() + " " + b.getPrincipioAtivo()).containsAll(farmacos))
                // Bula de associação com fármaco a mais perde para a do fármaco isolado.
                .min(Comparator.comparingInt((Bulario b) -> extras(b, farmacos))
                        .thenComparingInt(b -> b.getId()));
    }

    private static int extras(Bulario b, Set<String> farmacos) {
        Set<String> daBula = farmacos(b.getPrincipioAtivo() != null && !b.getPrincipioAtivo().isBlank()
                ? b.getPrincipioAtivo() : b.getNome());
        daBula.removeAll(farmacos);
        return daBula.size();
    }

    static Set<String> farmacos(String texto) {
        return tokens(texto).stream()
                .filter(t -> t.length() > 2 && !TERMOS_GENERICOS.contains(t) && !t.matches("\\d+"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> tokens(String texto) {
        if (texto == null) {
            return new LinkedHashSet<>();
        }
        String base = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("([a-zA-Z])(\\d)", "$1 $2")
                .replaceAll("(\\d)([a-zA-Z])", "$1 $2")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return Arrays.stream(base.split(" "))
                .filter(t -> t.length() > 1)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
