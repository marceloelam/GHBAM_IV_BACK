package br.com.iv.plataforma.municipio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Paciente unico na plataforma (banco por prefeitura): acha o MESMO paciente nos bancos dos outros
 * municipios. Vinculo pelo prontuario (unico na plataforma: cada municipio numera numa faixa propria
 * e o cadastro trazido de outro municipio mantem numero e prontuario da origem) ou, para cadastros
 * antigos, por CPF/CNS validos. Nunca so pelo idpaciente. Somente leitura.
 */
@ApplicationScoped
public class PacienteEntreMunicipios {

    private static final Logger LOG = Logger.getLogger(PacienteEntreMunicipios.class);

    @Inject
    MunicipioRoteamento roteamento;

    /** Paciente achado em outro municipio: codigo IBGE, nome do municipio e idpaciente NAQUELE banco. */
    public record Vinculo(String codibge, String municipio, int idpaciente) {
    }

    static String digitos(Object valor) {
        return valor == null ? "" : valor.toString().replaceAll("\\D", "");
    }

    /** CPF com 11 digitos e nao repetido (00000000000, 11111111111...). */
    static boolean cpfUtil(String cpf) {
        return cpf.length() == 11 && !cpf.chars().allMatch(c -> c == cpf.charAt(0));
    }

    /** CNS com 15 digitos e nao repetido. */
    static boolean cnsUtil(String cns) {
        return cns.length() == 15 && !cns.chars().allMatch(c -> c == cns.charAt(0));
    }

    /**
     * Prontuario que identifica alguem: vazio ou so zeros ("0", "000") e' o valor de quem ainda nao
     * internou e casaria com milhares de pacientes diferentes.
     */
    static boolean prontuarioUtil(String prontuario) {
        return !prontuario.replaceAll("[0\\s./-]", "").isEmpty();
    }

    /** O mesmo paciente (prontuario, CPF ou CNS) nos bancos dos OUTROS municipios. */
    public List<Vinculo> localizar(Object prontuarioInformado, Object cpfInformado, Object cnsInformado) {
        return localizar(null, null, prontuarioInformado, cpfInformado, cnsInformado);
    }

    /**
     * Cadastro unico (pacientes compartilhado entre os bancos): o mesmo idpaciente com o mesmo nome e' a
     * mesma pessoa - vinculo preferido. Senao prontuario util, CPF ou CNS validos.
     */
    public List<Vinculo> localizar(Object idpacienteInformado, Object nomeInformado, Object prontuarioInformado,
            Object cpfInformado, Object cnsInformado) {
        List<Vinculo> out = new ArrayList<>();
        if (!roteamento.ativa()) {
            return out;
        }
        int idpaciente = idpacienteInformado instanceof Number n ? n.intValue() : -1;
        String nome = nomeInformado == null ? "" : nomeInformado.toString().trim().toUpperCase();
        boolean temId = idpaciente > 0 && !nome.isEmpty();
        String prontuario = prontuarioInformado == null ? "" : prontuarioInformado.toString().trim().toUpperCase();
        String cpf = digitos(cpfInformado);
        String cns = digitos(cnsInformado);
        boolean temProntuario = prontuarioUtil(prontuario);
        boolean temCpf = cpfUtil(cpf);
        boolean temCns = cnsUtil(cns);
        if (!temId && !temProntuario && !temCpf && !temCns) {
            return out;
        }
        String daqui = roteamento.atual();
        for (String cod : roteamento.municipios()) {
            if (cod.equals(daqui)) {
                continue;
            }
            try (Connection c = roteamento.dataSource(cod).getConnection();
                    PreparedStatement ps = c.prepareStatement(
                            "SELECT idpaciente FROM pacientes WHERE "
                                    + "(? AND idpaciente = ? AND upper(trim(coalesce(nome, ''))) = ?) "
                                    + "OR (? AND upper(trim(coalesce(prontuario, ''))) = ?) "
                                    + "OR (? AND regexp_replace(coalesce(cpf, ''), '\\D', '', 'g') = ?) "
                                    + "OR (? AND regexp_replace(coalesce(cns, ''), '\\D', '', 'g') = ?) "
                                    + "ORDER BY (idpaciente = ?) DESC, (upper(trim(coalesce(prontuario, ''))) = ?) DESC, "
                                    + "idpaciente DESC LIMIT 1")) {
                ps.setBoolean(1, temId);
                ps.setInt(2, idpaciente);
                ps.setString(3, nome);
                ps.setBoolean(4, temProntuario);
                ps.setString(5, prontuario);
                ps.setBoolean(6, temCpf);
                ps.setString(7, cpf);
                ps.setBoolean(8, temCns);
                ps.setString(9, cns);
                ps.setInt(10, idpaciente);
                ps.setString(11, temProntuario ? prontuario : "#SEM_PRONTUARIO#");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        out.add(new Vinculo(cod, roteamento.nomeMunicipio(cod), rs.getInt(1)));
                    }
                }
            } catch (SQLException | RuntimeException e) {
                LOG.warn("[PACIENTE_MUNICIPIO] banco do municipio " + cod + " inacessivel: " + e.getMessage());
            }
        }
        return out;
    }
}
