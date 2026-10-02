package br.com.iv.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.iv.dto.CadsusPdqConsultaDto;
import br.com.iv.dto.CadsusPdqRespostaDto;
import br.com.iv.entity.Pacientes;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.Query;

/**
 * Consulta de paciente para sistemas externos (GET /integracao/pacientes), autenticada por
 * chave de API em vez de sessao GHBAM.
 *
 * Ordem da busca: primeiro o cadastro local (tabela pacientes, dados completos); so quando
 * nada e encontrado vai ao CADSUS pela API oficial PDQ, usando a credencial da unidade
 * vinculada a chave. Cada item devolvido traz "origem" (GHBAM ou CADSUS).
 *
 * Chaves: ghbam.integracao.api-keys = "sistema|UNIDADE|chave,sistema2|UNIDADE2|chave2"
 * (variavel GHBAM_INTEGRACAO_API_KEYS). Sem a variavel, a rota fica fechada.
 */
@ApplicationScoped
public class IntegracaoPacienteService {

    private static final Logger LOG = Logger.getLogger(IntegracaoPacienteService.class);

    static final int CHAVE_TAMANHO_MINIMO = 32;
    static final int LIMITE_RESULTADOS = 20;

    private static final String ACENTOS = "ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇ";
    private static final String SEM_ACENTOS = "AAAAAEEEEIIIIOOOOOUUUUC";

    @ConfigProperty(name = "ghbam.integracao.api-keys")
    Optional<String> apiKeysConfig;

    @Inject
    CadsusPdqClienteService cadsusPdq;

    public record Integrador(String sistema, String unidade) {
    }

    public record Filtro(String nome, String nascimento, String mae, String cpf, String cns) {
    }

    /** Diz no log de subida quantas chaves valem (nunca os valores), para diagnosticar 401. */
    void aoIniciar(@Observes StartupEvent evento) {
        String config = apiKeysConfig.orElse("");
        int validas = 0;
        int total = 0;
        for (String entrada : config.split(",")) {
            if (entrada.isBlank()) {
                continue;
            }
            total++;
            String[] partes = entrada.trim().split("\\|");
            if (partes.length == 3 && partes[2].trim().length() >= CHAVE_TAMANHO_MINIMO) {
                validas++;
            }
        }
        if (validas == 0) {
            LOG.warn("Integracao /integracao/pacientes: nenhuma chave valida em ghbam.integracao.api-keys ("
                    + total + " entrada(s) lida(s)) - toda chamada recebera 401.");
        } else {
            LOG.info("Integracao /integracao/pacientes: " + validas + " de " + total + " chave(s) valida(s).");
        }
    }

    /** Sistema integrador dono da chave, ou vazio quando a chave nao confere. */
    public Optional<Integrador> autenticar(String chave) {
        return autenticar(chave, apiKeysConfig.orElse(""));
    }

    static Optional<Integrador> autenticar(String chave, String config) {
        if (chave == null || chave.isBlank() || config == null || config.isBlank()) {
            return Optional.empty();
        }
        byte[] recebida = chave.trim().getBytes(StandardCharsets.UTF_8);
        Integrador encontrado = null;
        for (String entrada : config.split(",")) {
            String[] partes = entrada.trim().split("\\|");
            if (partes.length != 3 || partes[2].trim().length() < CHAVE_TAMANHO_MINIMO) {
                continue; // entrada mal formada ou chave curta demais nunca autentica
            }
            // Comparacao em tempo constante e sem parar no primeiro acerto, para o tempo de
            // resposta nao revelar qual chave (ou quanto dela) confere.
            if (MessageDigest.isEqual(recebida, partes[2].trim().getBytes(StandardCharsets.UTF_8))
                    && encontrado == null) {
                encontrado = new Integrador(partes[0].trim(), partes[1].trim());
            }
        }
        return Optional.ofNullable(encontrado);
    }

    /** Mensagem de erro de validacao do filtro, ou null quando a busca e valida. */
    public static String validar(Filtro f) {
        if (preenchido(digitos(f.cpf())) || preenchido(digitos(f.cns()))) {
            if (preenchido(digitos(f.cpf())) && digitos(f.cpf()).length() != 11) {
                return "CPF deve ter 11 digitos.";
            }
            if (preenchido(digitos(f.cns())) && digitos(f.cns()).length() != 15) {
                return "CNS deve ter 15 digitos.";
            }
            return validarNascimento(f);
        }
        int criterios = (preenchido(f.nome()) ? 1 : 0) + (preenchido(f.nascimento()) ? 1 : 0)
                + (preenchido(f.mae()) ? 1 : 0);
        if (criterios < 2) {
            return "Informe cpf ou cns, ou ao menos dois entre nome, nascimento e mae.";
        }
        return validarNascimento(f);
    }

    private static String validarNascimento(Filtro f) {
        if (preenchido(f.nascimento()) && ConsultaCnsService.formatarData(f.nascimento()) == null) {
            return "Data de nascimento invalida. Use dd/mm/aaaa ou aaaa-mm-dd.";
        }
        return null;
    }

    public Map<String, Object> consultar(Filtro filtro, Integrador integrador) {
        List<Map<String, Object>> locais = buscarLocal(filtro);
        Map<String, Object> resposta = new LinkedHashMap<>();
        if (!locais.isEmpty()) {
            resposta.put("encontrado", true);
            resposta.put("origem", "GHBAM");
            resposta.put("total", locais.size());
            resposta.put("limiteAtingido", locais.size() >= LIMITE_RESULTADOS);
            resposta.put("pacientes", locais);
            return resposta;
        }

        CadsusPdqRespostaDto cadsus = cadsusPdq.consultar(integrador.unidade(), filtroCadsus(filtro));
        List<Map<String, Object>> externos = new ArrayList<>();
        if (cadsus.pacientes() != null) {
            for (Map<String, Object> p : cadsus.pacientes()) {
                externos.add(mapearCadsus(p));
            }
        }
        resposta.put("encontrado", !externos.isEmpty());
        resposta.put("origem", "CADSUS");
        resposta.put("total", externos.size());
        resposta.put("pacientes", externos);
        resposta.put("mensagem", externos.isEmpty()
                ? "Paciente nao encontrado no GHBAM. CADSUS: " + cadsus.mensagem()
                : "Paciente nao cadastrado no GHBAM; dados vindos do CADSUS.");
        return resposta;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> buscarLocal(Filtro f) {
        StringBuilder sql = new StringBuilder("SELECT * FROM pacientes WHERE 1=1");
        Map<String, Object> params = new LinkedHashMap<>();

        if (preenchido(digitos(f.cpf()))) {
            sql.append(" AND regexp_replace(coalesce(cpf,''), '[^0-9]', '', 'g') = :cpf");
            params.put("cpf", digitos(f.cpf()));
        }
        if (preenchido(digitos(f.cns()))) {
            sql.append(" AND regexp_replace(coalesce(cns,''), '[^0-9]', '', 'g') = :cns");
            params.put("cns", digitos(f.cns()));
        }
        if (preenchido(f.nome())) {
            sql.append(" AND ").append(colunaNormalizada("nome")).append(" LIKE :nome");
            params.put("nome", textoBusca(f.nome()) + "%");
        }
        if (preenchido(f.mae())) {
            sql.append(" AND ").append(colunaNormalizada("mae")).append(" LIKE :mae");
            params.put("mae", textoBusca(f.mae()) + "%");
        }
        if (preenchido(f.nascimento())) {
            // A recepcao grava aaaa/MM/dd, mas ha cadastros em dd/MM/aaaa e aaaa-MM-dd
            // (ver ConsultaCnsService.formatarData) - aceita as tres formas.
            String[] dma = ConsultaCnsService.formatarData(f.nascimento()).split("/");
            sql.append(" AND nascimento IN (:n1, :n2, :n3)");
            params.put("n1", dma[2] + "/" + dma[1] + "/" + dma[0]);
            params.put("n2", dma[0] + "/" + dma[1] + "/" + dma[2]);
            params.put("n3", dma[2] + "-" + dma[1] + "-" + dma[0]);
        }
        sql.append(" ORDER BY nome LIMIT ").append(LIMITE_RESULTADOS);

        Query query = Pacientes.getEntityManager().createNativeQuery(sql.toString(), Pacientes.class);
        params.forEach(query::setParameter);
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Pacientes p : (List<Pacientes>) query.getResultList()) {
            resultado.add(mapearLocal(p));
        }
        return resultado;
    }

    private static String colunaNormalizada(String coluna) {
        return "translate(upper(trim(coalesce(" + coluna + ",''))), '" + ACENTOS + "', '" + SEM_ACENTOS + "')";
    }

    /** Maiusculas, sem acento, espacos colapsados e sem curingas de LIKE vindos do cliente. */
    static String textoBusca(String valor) {
        String semAcento = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.replaceAll("[%_\\\\]", "").replaceAll("\\s+", " ").toUpperCase();
    }

    private static Map<String, Object> mapearLocal(Pacientes p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("origem", "GHBAM");
        m.put("idpaciente", p.getIdpaciente());
        m.put("prontuario", p.getProntuario());
        m.put("nome", p.getNome());
        m.put("nascimento", p.getNascimento());
        m.put("idade", p.getIdade());
        m.put("sexo", p.getSexo());
        m.put("etnia", p.getEtnia());
        m.put("mae", p.getMae());
        m.put("pai", p.getPai());
        m.put("cpf", p.getCpf());
        m.put("cns", p.getCns());
        m.put("rg", p.getRg());
        m.put("estadoCivil", p.getEcivil());
        m.put("profissao", p.getProfissao());
        m.put("naturalidade", p.getNaturalidade());
        m.put("endereco", p.getEndereco());
        m.put("numero", p.getNumero());
        m.put("bairro", p.getBairro());
        m.put("municipio", p.getMunicipio());
        m.put("estado", p.getEstado());
        m.put("cep", p.getCep());
        m.put("telefone", p.getTelefone());
        m.put("tipoSanguineo", p.getSangue());
        m.put("pacienteEspecial", p.getPacienteespecial());
        m.put("dataCadastro", p.getData() == null ? null : p.getData().toString());
        return m;
    }

    private static Map<String, Object> mapearCadsus(Map<String, Object> p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("origem", "CADSUS");
        String nome = (texto(p.get("given")) + " " + texto(p.get("family"))).trim();
        m.put("nome", nome.isEmpty() ? null : nome);
        m.put("nascimento", nascimentoCadsus(texto(p.get("birthTime"))));
        m.put("sexo", p.get("administrativeGenderCode"));
        m.put("mae", p.get("mothersMaidenName"));
        m.put("cpf", p.get("cpf"));
        m.put("cns", p.get("cns"));
        return m;
    }

    /** CADSUS devolve AAAAMMDD (as vezes com hora); a resposta usa aaaa/MM/dd como o cadastro. */
    static String nascimentoCadsus(String valor) {
        String d = digitos(valor);
        return d.length() >= 8 ? d.substring(0, 4) + "/" + d.substring(4, 6) + "/" + d.substring(6, 8) : null;
    }

    private static CadsusPdqConsultaDto filtroCadsus(Filtro f) {
        String nascimento = null;
        if (preenchido(f.nascimento())) {
            String[] dma = ConsultaCnsService.formatarData(f.nascimento()).split("/");
            nascimento = dma[2] + dma[1] + dma[0]; // PDQ exige AAAAMMDD
        }
        return new CadsusPdqConsultaDto(
                vazioParaNull(digitos(f.cns())),
                vazioParaNull(digitos(f.cpf())),
                preenchido(f.nome()) ? f.nome().trim() : null,
                preenchido(f.mae()) ? f.mae().trim() : null,
                nascimento,
                null);
    }

    private static String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    static String digitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private static String vazioParaNull(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
