package br.com.iv.aps;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.thrift.TBase;
import org.apache.thrift.TEnum;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.TSerializer;
import org.apache.thrift.meta_data.EnumMetaData;
import org.apache.thrift.meta_data.FieldMetaData;
import org.apache.thrift.meta_data.FieldValueMetaData;
import org.apache.thrift.meta_data.ListMetaData;
import org.apache.thrift.meta_data.SetMetaData;
import org.apache.thrift.meta_data.StructMetaData;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TType;

import br.gov.saude.esusab.dadotransp.DadoInstalacaoThrift;
import br.gov.saude.esusab.dadotransp.DadoTransporteThrift;
import br.gov.saude.esusab.dadotransp.VersaoThrift;
import br.gov.saude.esusab.ras.atenddomiciliar.FichaAtendimentoDomiciliarMasterThrift;
import br.gov.saude.esusab.ras.atendindividual.FichaAtendimentoIndividualMasterThrift;
import br.gov.saude.esusab.ras.atendodonto.FichaAtendimentoOdontologicoMasterThrift;
import br.gov.saude.esusab.ras.atendprocedimentos.FichaProcedimentoMasterThrift;
import br.gov.saude.esusab.ras.atividadecoletiva.FichaAtividadeColetivaThrift;
import br.gov.saude.esusab.ras.avaliacaoelegibilidade.FichaAvaliacaoElegibilidadeThrift;
import br.gov.saude.esusab.ras.cadastrodomiciliar.CadastroDomiciliarThrift;
import br.gov.saude.esusab.ras.cadastroindividual.CadastroIndividualThrift;
import br.gov.saude.esusab.ras.complementarzika.FichaComplementarZikaMicrocefaliaThrift;
import br.gov.saude.esusab.ras.consumoalimentar.FichaConsumoAlimentarThrift;
import br.gov.saude.esusab.ras.cuidadocompartilhado.CuidadoCompartilhadoThrift;
import br.gov.saude.esusab.ras.vacinacao.FichaVacinacaoMasterThrift;
import br.gov.saude.esusab.ras.visitadomiciliar.FichaVisitaDomiciliarMasterThrift;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Serializacao LEDI APS 8.7.0 a partir de JSON com os nomes de campo oficiais
 * dos .thrift (ex.: "uuidFicha", "headerTransport", "atendimentosIndividuais").
 *
 * O mapeamento e generico: usa o metaDataMap das classes geradas pelo compilador
 * oficial, entao todo campo de todo modelo de informacao e coberto sem codigo
 * por ficha. Datas (i64) aceitam epoch em ms, "yyyy-MM-dd" ou ISO date-time;
 * enums aceitam o codigo numerico ou o nome da constante.
 */
@ApplicationScoped
public class ApsLediSerializer {

    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    public static final int VERSAO_MAJOR = 8;
    public static final int VERSAO_MINOR = 7;
    public static final int VERSAO_REVISION = 0;

    public static final Map<Integer, Class<? extends TBase<?, ?>>> CLASSE_POR_TIPO = Map.ofEntries(
            Map.entry(2, CadastroIndividualThrift.class),
            Map.entry(3, CadastroDomiciliarThrift.class),
            Map.entry(4, FichaAtendimentoIndividualMasterThrift.class),
            Map.entry(5, FichaAtendimentoOdontologicoMasterThrift.class),
            Map.entry(6, FichaAtividadeColetivaThrift.class),
            Map.entry(7, FichaProcedimentoMasterThrift.class),
            Map.entry(8, FichaVisitaDomiciliarMasterThrift.class),
            Map.entry(10, FichaAtendimentoDomiciliarMasterThrift.class),
            Map.entry(11, FichaAvaliacaoElegibilidadeThrift.class),
            Map.entry(12, FichaConsumoAlimentarThrift.class),
            Map.entry(13, FichaComplementarZikaMicrocefaliaThrift.class),
            Map.entry(14, FichaVacinacaoMasterThrift.class),
            Map.entry(16, CuidadoCompartilhadoThrift.class));

    /** Remetente/originadora (DadoInstalacaoThrift). */
    public record Instalacao(String contraChave, String uuidInstalacao, String cpfOuCnpj,
            String nomeOuRazaoSocial, String fone, String email) {
    }

    /** Dados do envelope de transporte. */
    public record Envelope(String uuid, int tipo, String cnes, String codIbge, String ine, Long numLote) {
    }

    public TBase<?, ?> toThrift(int tipo, Map<String, Object> dados) {
        Class<? extends TBase<?, ?>> clazz = CLASSE_POR_TIPO.get(tipo);
        if (clazz == null) {
            throw new IllegalArgumentException("Tipo de registro LEDI nao suportado: " + tipo);
        }
        return build(clazz, dados, "");
    }

    public byte[] serializarFicha(int tipo, Map<String, Object> dados) {
        TBase<?, ?> ficha = toThrift(tipo, dados);
        try {
            ficha.getClass().getMethod("validate").invoke(ficha);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IllegalArgumentException("Registro LEDI invalido: " + e.getCause().getMessage(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return serialize(ficha);
    }

    /** Conteudo do arquivo "<uuid>.esus" aceito pela API do PEC Centralizador. */
    public byte[] serializarTransporte(Envelope env, Map<String, Object> dados, Instalacao inst) {
        byte[] ficha = serializarFicha(env.tipo(), dados);
        DadoInstalacaoThrift instalacao = new DadoInstalacaoThrift();
        instalacao.setContraChave(inst.contraChave());
        instalacao.setUuidInstalacao(inst.uuidInstalacao());
        instalacao.setCpfOuCnpj(inst.cpfOuCnpj());
        instalacao.setNomeOuRazaoSocial(inst.nomeOuRazaoSocial());
        if (notBlank(inst.fone())) {
            instalacao.setFone(inst.fone());
        }
        if (notBlank(inst.email())) {
            instalacao.setEmail(inst.email());
        }
        instalacao.setVersaoSistema("GHBAM 4.0");
        instalacao.setNomeBancoDados("PostgreSQL");

        DadoTransporteThrift transporte = new DadoTransporteThrift();
        transporte.setUuidDadoSerializado(env.uuid());
        transporte.setTipoDadoSerializado(env.tipo());
        transporte.setCnesDadoSerializado(env.cnes());
        if (notBlank(env.codIbge())) {
            transporte.setCodIbge(env.codIbge());
        }
        if (notBlank(env.ine())) {
            transporte.setIneDadoSerializado(env.ine());
        }
        if (env.numLote() != null) {
            transporte.setNumLote(env.numLote());
        }
        transporte.setDadoSerializado(ficha);
        transporte.setRemetente(instalacao);
        transporte.setOriginadora(instalacao.deepCopy());
        transporte.setVersao(new VersaoThrift(VERSAO_MAJOR, VERSAO_MINOR, VERSAO_REVISION));
        try {
            transporte.validate();
        } catch (TException e) {
            throw new IllegalArgumentException("Envelope LEDI invalido: " + e.getMessage(), e);
        }
        return serialize(transporte);
    }

    private byte[] serialize(TBase<?, ?> base) {
        try {
            return new TSerializer(new TBinaryProtocol.Factory()).serialize(base);
        } catch (TException e) {
            throw new IllegalStateException("Falha ao serializar LEDI: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private TBase<?, ?> build(Class<? extends TBase> clazz, Map<String, Object> json, String caminho) {
        TBase instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Nao foi possivel instanciar " + clazz.getSimpleName(), e);
        }
        Map<? extends TFieldIdEnum, FieldMetaData> meta = FieldMetaData.getStructMetaDataMap(clazz);
        for (Map.Entry<? extends TFieldIdEnum, FieldMetaData> e : meta.entrySet()) {
            String nome = e.getKey().getFieldName();
            if (json == null || !json.containsKey(nome)) {
                continue;
            }
            Object raw = json.get(nome);
            if (raw == null || (raw instanceof String s && s.isBlank())) {
                continue;
            }
            Object valor = convert(e.getValue().valueMetaData, raw, caminho + nome);
            if (valor != null) {
                instance.setFieldValue(e.getKey(), valor);
            }
        }
        return instance;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object convert(FieldValueMetaData meta, Object raw, String caminho) {
        switch (meta.type) {
            case TType.STRUCT: {
                if (!(raw instanceof Map)) {
                    throw new IllegalArgumentException("Campo " + caminho + " deve ser objeto.");
                }
                return build(((StructMetaData) meta).structClass, (Map<String, Object>) raw, caminho + ".");
            }
            case TType.LIST: {
                FieldValueMetaData elem = ((ListMetaData) meta).elemMetaData;
                List<Object> out = new ArrayList<>();
                for (Object item : asCollection(raw, caminho)) {
                    Object v = item == null ? null : convert(elem, item, caminho + "[]");
                    if (v != null) {
                        out.add(v);
                    }
                }
                return out.isEmpty() ? null : out;
            }
            case TType.SET: {
                FieldValueMetaData elem = ((SetMetaData) meta).elemMetaData;
                Set<Object> out = new HashSet<>();
                for (Object item : asCollection(raw, caminho)) {
                    Object v = item == null ? null : convert(elem, item, caminho + "[]");
                    if (v != null) {
                        out.add(v);
                    }
                }
                return out.isEmpty() ? null : out;
            }
            case TType.ENUM:
                return toEnum(((EnumMetaData) meta).enumClass, raw, caminho);
            case TType.BOOL:
                return toBool(raw);
            case TType.I16:
                return (short) toLong(raw, caminho);
            case TType.I32:
                return (int) toLong(raw, caminho);
            case TType.I64:
                return toEpochOrLong(raw, caminho);
            case TType.DOUBLE:
                return toDouble(raw, caminho);
            case TType.STRING:
                if (meta.isBinary()) {
                    return ByteBuffer.wrap(raw instanceof byte[] b ? b : String.valueOf(raw).getBytes());
                }
                return raw instanceof Number n && isInteger(n) ? String.valueOf(n.longValue()) : String.valueOf(raw).trim();
            default:
                throw new IllegalArgumentException("Tipo Thrift nao suportado em " + caminho);
        }
    }

    private Collection<?> asCollection(Object raw, String caminho) {
        if (raw instanceof Collection<?> c) {
            return c;
        }
        if (raw instanceof Object[] arr) {
            return List.of(arr);
        }
        return List.of(raw);
    }

    private Object toEnum(Class<? extends TEnum> enumClass, Object raw, String caminho) {
        try {
            if (raw instanceof Number n) {
                Method find = enumClass.getMethod("findByValue", int.class);
                Object v = find.invoke(null, n.intValue());
                if (v == null) {
                    throw new IllegalArgumentException("Valor " + n + " invalido em " + caminho);
                }
                return v;
            }
            String s = String.valueOf(raw).trim();
            if (s.matches("-?\\d+")) {
                return toEnum(enumClass, Integer.valueOf(s), caminho);
            }
            for (TEnum c : enumClass.getEnumConstants()) {
                if (((Enum<?>) c).name().equalsIgnoreCase(s)) {
                    return c;
                }
            }
            throw new IllegalArgumentException("Valor '" + s + "' invalido em " + caminho);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Boolean toBool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(raw).trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("sim") || s.equals("s");
    }

    private long toLong(Object raw, String caminho) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(raw).trim();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Campo " + caminho + " deve ser numerico (recebido '" + s + "').");
        }
    }

    private long toEpochOrLong(Object raw, String caminho) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(raw).trim();
        if (s.matches("-?\\d+")) {
            return Long.parseLong(s);
        }
        return epochMillis(s, caminho);
    }

    public static long epochMillis(String s, String caminho) {
        try {
            if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(s).atStartOfDay(ZONA).toInstant().toEpochMilli();
            }
            if (s.matches("\\d{4}/\\d{2}/\\d{2}")) {
                return LocalDate.parse(s.replace('/', '-')).atStartOfDay(ZONA).toInstant().toEpochMilli();
            }
            if (s.matches("\\d{2}/\\d{2}/\\d{4}")) {
                String[] p = s.split("/");
                return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]))
                        .atStartOfDay(ZONA).toInstant().toEpochMilli();
            }
            if (s.endsWith("Z") || s.matches(".*[+-]\\d{2}:\\d{2}$")) {
                return OffsetDateTime.parse(s).toInstant().toEpochMilli();
            }
            return LocalDateTime.parse(s.replace(' ', 'T')).atZone(ZONA).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Data invalida em " + caminho + ": '" + s + "'.");
        }
    }

    public static long epochMillis(LocalDate d) {
        return d.atStartOfDay(ZONA).toInstant().toEpochMilli();
    }

    public static long epochMillis(LocalDateTime d) {
        return d.atZone(ZONA).toInstant().toEpochMilli();
    }

    private double toDouble(Object raw, String caminho) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        String s = String.valueOf(raw).trim().replace(',', '.');
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Campo " + caminho + " deve ser decimal (recebido '" + s + "').");
        }
    }

    private boolean isInteger(Number n) {
        return n instanceof Integer || n instanceof Long || n instanceof Short
                || (n.doubleValue() == Math.rint(n.doubleValue()) && !Double.isInfinite(n.doubleValue()));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
