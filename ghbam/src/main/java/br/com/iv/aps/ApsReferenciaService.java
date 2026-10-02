package br.com.iv.aps;

import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Tabelas de referencia oficiais do LEDI APS 8.7.0 (extraidas da documentacao
 * da Bridge/UFSC, ver docs/aps/referencia-oficial): enumeracoes do dicionario,
 * CIAP2 x CID10, regras vacinais (imunobiologico x estrategia x dose), CBOs por
 * modelo de informacao, municipios/paises/UF/tipos de logradouro e CBOs.
 */
@ApplicationScoped
public class ApsReferenciaService {

    @Inject
    ObjectMapper mapper;

    private volatile Map<String, Object> referencias;
    private volatile Map<String, Object> localidades;
    private volatile List<Map<String, Object>> cbos;
    private volatile Map<Integer, Set<String>> cboPorTipo;
    private volatile Set<String> regrasVacinais;

    public Map<String, Object> referencias() {
        if (referencias == null) {
            referencias = load("aps/ledi-referencias.json", new TypeReference<LinkedHashMap<String, Object>>() {
            });
        }
        return referencias;
    }

    public Map<String, Object> localidades() {
        if (localidades == null) {
            localidades = load("aps/ledi-localidades.json", new TypeReference<LinkedHashMap<String, Object>>() {
            });
        }
        return localidades;
    }

    public List<Map<String, Object>> cbos() {
        if (cbos == null) {
            cbos = load("aps/ledi-cbos.json", new TypeReference<List<Map<String, Object>>>() {
            });
        }
        return cbos;
    }

    private volatile List<Map<String, Object>> catmat;

    /** Tabela de medicamentos CATMAT do LEDI (codigoCatmat do MedicamentoThrift). */
    public List<Map<String, Object>> catmat() {
        if (catmat == null) {
            catmat = load("aps/ledi-catmat.json", new TypeReference<List<Map<String, Object>>>() {
            });
        }
        return catmat;
    }

    public List<Map<String, Object>> buscarCatmat(String termo, int limite) {
        String t = termo == null ? "" : java.text.Normalizer.normalize(termo.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        if (t.length() < 2) {
            return List.of();
        }
        return catmat().stream().filter(m -> {
            String s = java.text.Normalizer.normalize((m.get("Princípio Ativo") + " " + m.get("Código CATMAT")).toLowerCase(),
                    java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            return s.contains(t);
        }).limit(Math.max(1, Math.min(limite, 100))).toList();
    }

    @SuppressWarnings("unchecked")
    public Set<String> cbosPermitidos(int tipo) {
        if (cboPorTipo == null) {
            Map<Integer, Set<String>> m = new LinkedHashMap<>();
            Map<String, Object> raw = (Map<String, Object>) referencias().get("cboPorTipoFicha");
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                Map<String, Object> v = (Map<String, Object>) e.getValue();
                m.put(Integer.valueOf(e.getKey()), new HashSet<>((List<String>) v.get("cbos")));
            }
            cboPorTipo = m;
        }
        return cboPorTipo.getOrDefault(tipo, Set.of());
    }

    /** Regra do LEDI: o CBO do profissional precisa estar autorizado para o modelo de informacao. */
    public void validarCbo(int tipo, String cbo) {
        Set<String> permitidos = cbosPermitidos(tipo);
        if (permitidos.isEmpty()) {
            return;
        }
        if (cbo == null || !permitidos.contains(cbo)) {
            throw new IllegalArgumentException("O CBO " + (cbo == null ? "(nao informado)" : cbo)
                    + " nao pode registrar " + nomeTipo(tipo) + " (regra de CBO do LEDI APS). "
                    + "Ajuste a lotacao do profissional em APS > Configuracao.");
        }
    }

    @SuppressWarnings("unchecked")
    public boolean regraVacinalValida(int imunobiologico, int estrategiaRnds, int dose) {
        if (regrasVacinais == null) {
            Set<String> s = new HashSet<>();
            for (Map<String, Object> r : (List<Map<String, Object>>) referencias().get("regrasVacinacao")) {
                s.add(r.get("imunobiologico") + "|" + r.get("estrategiaRnds") + "|" + r.get("dose"));
            }
            regrasVacinais = s;
        }
        return regrasVacinais.contains(imunobiologico + "|" + estrategiaRnds + "|" + dose);
    }

    @SuppressWarnings("unchecked")
    public String descricao(String enumeracao, Object codigo) {
        if (codigo == null) {
            return null;
        }
        Map<String, Object> enums = (Map<String, Object>) referencias().get("enums");
        Map<String, Object> e = (Map<String, Object>) enums.get(enumeracao);
        if (e == null) {
            return String.valueOf(codigo);
        }
        for (Map<String, Object> item : (List<Map<String, Object>>) e.get("itens")) {
            if (String.valueOf(codigo).equals(String.valueOf(item.get("codigo")))) {
                return (String) item.get("descricao");
            }
        }
        return String.valueOf(codigo);
    }

    public static String nomeTipo(int tipo) {
        return switch (tipo) {
            case 2 -> "Cadastro Individual";
            case 3 -> "Cadastro Domiciliar e Territorial";
            case 4 -> "Atendimento Individual";
            case 5 -> "Atendimento Odontologico Individual";
            case 6 -> "Atividade Coletiva";
            case 7 -> "Procedimentos";
            case 8 -> "Visita Domiciliar e Territorial";
            case 10 -> "Atendimento Domiciliar";
            case 11 -> "Avaliacao de Elegibilidade e Admissao";
            case 12 -> "Marcadores de Consumo Alimentar";
            case 13 -> "Sindrome Neurologica por Zika/Microcefalia";
            case 14 -> "Vacinacao";
            case 16 -> "Cuidado Compartilhado";
            default -> "Registro " + tipo;
        };
    }

    private <T> T load(String path, TypeReference<T> type) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Recurso de referencia APS ausente: " + path);
            }
            return mapper.readValue(in, type);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Falha lendo " + path + ": " + e.getMessage(), e);
        }
    }
}
