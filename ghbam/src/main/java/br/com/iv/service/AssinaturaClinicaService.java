package br.com.iv.service;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.dto.AssinaturaClinicaDto;
import br.com.iv.entity.AssinaturaClinica;
import br.com.iv.entity.Servidor;
import br.com.iv.repository.AssinaturaClinicaRepository;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.util.UnidadeUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AssinaturaClinicaService implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String PRESCRICAO_ESTRUTURADA_BAM = "PRESCRICAO_ESTRUTURADA_BAM";
    public static final String PRESCRICAO_MEDICA = "PRESCRICAO_MEDICA";
    public static final String RECEITUARIO_MEDICO = "RECEITUARIO_MEDICO";
    public static final String RESUMO_ALTA = "RESUMO_ALTA";
    public static final String PARECER_ESPECIALISTA = "PARECER_ESPECIALISTA";
    public static final String PEDIDO_MEDICO = "PEDIDO_MEDICO";
    public static final String ANAMNESE_INTERNACAO = "ANAMNESE_INTERNACAO";
    public static final String LAUDO_AUTORIZACAO = "LAUDO_AUTORIZACAO";
    public static final String SAE_ENFERMAGEM = "SAE_ENFERMAGEM";
    public static final String AVALIACAO_ODONTOLOGICA = "AVALIACAO_ODONTOLOGICA";
    public static final String ATENDIMENTO_MULTIDISCIPLINAR = "ATENDIMENTO_MULTIDISCIPLINAR";

    @Inject
    AssinaturaClinicaRepository repository;

    @Inject
    EntityManager entityManager;

    @Inject
    ServidorRepository servidorRepository;

    @Inject
    FhirDispatchService fhirDispatchService;

    public List<AssinaturaClinica> listarPorBam(String tipoDocumento, Integer idbam) {
        return repository.listarPorBam(tipoDocumento, idbam);
    }

    public List<AssinaturaClinica> listarPorBam(Integer idbam) {
        return repository.listarPorBam(idbam);
    }

    public List<AssinaturaClinica> listarPorDocumento(String tipoDocumento, Integer iddocumento) {
        return repository.listarPorDocumento(tipoDocumento, iddocumento);
    }

    public AssinaturaClinica assinaturaAtivaPorBam(String tipoDocumento, Integer idbam) {
        if (idbam == null || idbam <= 0 || vazio(tipoDocumento)) {
            return null;
        }
        return repository.assinaturaAtivaPorBam(tipoDocumento, idbam);
    }

    public boolean bloqueadoPorBam(String tipoDocumento, Integer idbam) {
        return assinaturaAtivaPorBam(tipoDocumento, idbam) != null;
    }

    public AssinaturaClinica assinaturaAtivaPorDocumento(String tipoDocumento, Integer iddocumento) {
        if (iddocumento == null || iddocumento <= 0 || vazio(tipoDocumento)) {
            return null;
        }
        return repository.assinaturaAtivaPorDocumento(tipoDocumento, iddocumento);
    }

    public boolean bloqueadoPorDocumento(String tipoDocumento, Integer iddocumento) {
        return assinaturaAtivaPorDocumento(tipoDocumento, iddocumento) != null;
    }

    @Transactional
    public AssinaturaClinica assinar(AssinaturaClinicaDto dto) {
        return assinar(dto, AuthenticatedUser.empty());
    }

    @Transactional
    public AssinaturaClinica assinar(AssinaturaClinicaDto dto, AuthenticatedUser user) {
        if (dto == null || vazio(dto.getTipoDocumento())) {
            throw new IllegalArgumentException("Tipo do documento e obrigatorio.");
        }
        if (dto.getIdbam() == null && dto.getIddocumento() == null) {
            throw new IllegalArgumentException("Informe BAM ou documento.");
        }
        validarEscopoDaAssinatura(dto, user);
        String tipoDocumento = safe(dto.getTipoDocumento()).toUpperCase();
        String conteudoHash = hashConteudo(dto);
        if (dto.getIddocumento() != null) {
            AssinaturaClinica assinaturaAtiva = assinaturaAtivaPorDocumento(tipoDocumento, dto.getIddocumento());
            if (assinaturaAtiva != null) {
                boolean parecerComNovoConteudo = PARECER_ESPECIALISTA.equals(tipoDocumento)
                        && !safe(assinaturaAtiva.getConteudoHash()).equalsIgnoreCase(conteudoHash);
                boolean anamneseComNovoConteudo = ANAMNESE_INTERNACAO.equals(tipoDocumento)
                        && !safe(assinaturaAtiva.getConteudoHash()).equalsIgnoreCase(conteudoHash);
                boolean prescricaoEstruturadaComNovoConteudo = PRESCRICAO_ESTRUTURADA_BAM.equals(tipoDocumento)
                        && !safe(assinaturaAtiva.getConteudoHash()).equalsIgnoreCase(conteudoHash);
                if (!parecerComNovoConteudo && !anamneseComNovoConteudo && !prescricaoEstruturadaComNovoConteudo) {
                    throw new IllegalStateException("Documento ja assinado.");
                }
            }
        }
        if (dto.getIddocumento() == null && dto.getIdbam() != null) {
            AssinaturaClinica assinaturaAtiva = assinaturaAtivaPorBam(tipoDocumento, dto.getIdbam());
            if (assinaturaAtiva != null) {
                boolean prescricaoEstruturadaComNovoConteudo = PRESCRICAO_ESTRUTURADA_BAM.equals(tipoDocumento)
                        && !safe(assinaturaAtiva.getConteudoHash()).equalsIgnoreCase(conteudoHash);
                if (!prescricaoEstruturadaComNovoConteudo) {
                    throw new IllegalStateException("Documento ja assinado.");
                }
            }
        }

        AssinaturaClinica assinatura = new AssinaturaClinica();
        assinatura.setTipoDocumento(tipoDocumento);
        assinatura.setIddocumento(dto.getIddocumento());
        assinatura.setIdbam(dto.getIdbam());
        assinatura.setIdpaciente(dto.getIdpaciente());
        assinatura.setUnidade(safe(dto.getUnidade()));
        assinatura.setStatus("ASSINADO");
        assinatura.setConteudoResumo(safeText(dto.getConteudoResumo()));
        assinatura.setConteudoHash(conteudoHash);
        assinatura.setAssinadoPor(resolveNome(dto.getAssinadoPor(), user));
        assinatura.setAssinadoPorMatricula(resolveMatricula(dto.getAssinadoPorMatricula(), user));
        assinatura.setAssinaturaDigital(safeText(dto.getAssinaturaDigital()));
        assinatura.setAlgoritmoAssinatura(safe(dto.getAlgoritmoAssinatura()));
        assinatura.setChavePublica(safeText(dto.getChavePublica()));
        assinatura.setAssinadoEm(LocalDateTime.now());
        assinatura.persist();
        if (RESUMO_ALTA.equals(tipoDocumento)) {
            fhirDispatchService.enfileirarAposAssinatura(assinatura);
            // Resumo assinado atende o pedido do NIR (resumo_alta_solicitacao), seja quem for o medico.
            if (assinatura.getIdbam() != null) {
                AssinaturaClinica.getEntityManager().createNativeQuery(
                        "UPDATE resumo_alta_solicitacao SET resolvido_em = now() WHERE idbam = :idbam AND resolvido_em IS NULL")
                        .setParameter("idbam", assinatura.getIdbam())
                        .executeUpdate();
            }
        }
        return assinatura;
    }

    private void validarEscopoDaAssinatura(AssinaturaClinicaDto dto, AuthenticatedUser user) {
        String unidadeLogada = UnidadeUtil.normalizar(user == null ? "" : user.unidade());
        if (vazio(unidadeLogada)) {
            throw new IllegalStateException("Unidade do usuario nao identificada.");
        }

        String tipoDocumento = safe(dto.getTipoDocumento()).toUpperCase();
        if (PRESCRICAO_ESTRUTURADA_BAM.equals(tipoDocumento) || PRESCRICAO_MEDICA.equals(tipoDocumento) || PEDIDO_MEDICO.equals(tipoDocumento)) {
            Integer idbam = dto.getIdbam() != null ? dto.getIdbam() : idbamDoDocumento(tipoDocumento, dto.getIddocumento());
            validarEscopoDoBam(idbam, unidadeLogada, dto);
            return;
        }

        Integer iddocumento = dto.getIddocumento();
        if (iddocumento == null || iddocumento <= 0) {
            throw new IllegalArgumentException("Documento invalido para assinatura.");
        }

        String unidadeDocumento = unidadeDoDocumento(tipoDocumento, iddocumento);
        if (vazio(unidadeDocumento)) {
            throw new IllegalStateException("Unidade do documento nao localizada.");
        }
        if (UnidadeUtil.normalizar(unidadeDocumento).equals(unidadeLogada)) {
            return;
        }
        throw new IllegalStateException("Documento fora da unidade logada.");
    }

    private void validarEscopoDoBam(Integer idbam, String unidadeLogada, AssinaturaClinicaDto dto) {
        if (idbam == null || idbam <= 0) {
            throw new IllegalArgumentException("BAM invalido para assinatura.");
        }

        String unidadeDocumento = unidadeDoBam(idbam);
        if (vazio(unidadeDocumento)) {
            return;
        }
        if (UnidadeUtil.normalizar(unidadeDocumento).equals(unidadeLogada)) {
            return;
        }
        throw new IllegalStateException("Documento fora da unidade logada.");
    }

    private Integer idbamDoDocumento(String tipoDocumento, Integer iddocumento) {
        if (vazio(tipoDocumento) || iddocumento == null || iddocumento <= 0) {
            return null;
        }
        String sql = null;
        if (PRESCRICAO_MEDICA.equals(tipoDocumento)) {
            sql = "select bam from prescricao where idprescricao = :iddocumento";
        }
        if (PEDIDO_MEDICO.equals(tipoDocumento)) {
            sql = "select bam from pedidomedico2 where idpedidomedico = :iddocumento";
        }
        if (sql == null) {
            return null;
        }
        try {
            List<?> rows = entityManager.createNativeQuery(sql)
                    .setParameter("iddocumento", iddocumento)
                    .getResultList();
            if (rows.isEmpty()) return null;
            Object value = rows.get(0);
            return value == null ? null : Integer.valueOf(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private void registraUnidadeBam(Integer idbam, String unidade) {
        try {
            entityManager.createNativeQuery("update bam set unidade = :unidade where idbam = :idbam")
                    .setParameter("unidade", unidade)
                    .setParameter("idbam", idbam)
                    .executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel atualizar a unidade do BAM.", e);
        }
    }

    private String unidadeDoBam(Integer idbam) {
        if (idbam == null || idbam <= 0) {
            return "";
        }

        List<?> rows = entityManager.createNativeQuery("select coalesce(b.unidade, '') from bam b where b.idbam = :idbam")
                .setParameter("idbam", idbam)
                .getResultList();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("BAM nao localizado para assinatura.");
        }
        Object value = rows.get(0);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String unidadeDoDocumento(String tipoDocumento, Integer iddocumento) {
        if (vazio(tipoDocumento) || iddocumento == null || iddocumento <= 0) {
            return "";
        }

        String sql = switch (tipoDocumento) {
            case "EVOLUCAO_MEDICA" -> "select coalesce(e.unidade, '') from evolucaopaciente e where e.idevolucao = :iddocumento";
            case "EVOLUCAO_ENF", "EVOLUCAO_MULTIDISCIPLINAR" -> "select coalesce(e.unidade, '') from evolucaopacienteenf e where e.idevolucao = :iddocumento";
            case PRESCRICAO_MEDICA -> "select coalesce(p.localsolicitante, '') from prescricao p where p.idprescricao = :iddocumento";
            case RECEITUARIO_MEDICO -> "select coalesce(r.unidade, '') from evolucaopacientereceituario r where r.idevolucaoreceituario = :iddocumento";
            case PEDIDO_MEDICO -> """
                    select coalesce(nullif(p.unidade, ''), b.unidade, '')
                    from pedidomedico2 p
                    left join bam b on b.idbam = p.bam
                    where p.idpedidomedico = :iddocumento
                    """;
            case RESUMO_ALTA -> """
                    select coalesce(b.unidade, '')
                    from resumodealta r
                    left join bam b on b.idbam = case when r.bam ~ '^\\d+$' then cast(r.bam as integer) else null end
                    where r.idresumoalta = :iddocumento
                    """;
            case PARECER_ESPECIALISTA -> "select coalesce(p.unidade, '') from parecerespecialista p where p.idparecerespecialista = :iddocumento";
            case ANAMNESE_INTERNACAO -> """
                    select coalesce(b.unidade, '')
                    from anamneseinternacao a
                    left join bam b on b.idbam = a.nbam
                    where a.idanamneseinternacao = :iddocumento
                    """;
            case LAUDO_AUTORIZACAO -> """
                    select coalesce(b.unidade, '')
                    from laudoautorizacao l
                    left join bam b on b.idbam = l.bam
                    where l.idlaudo = :iddocumento
                    """;
            case SAE_ENFERMAGEM -> "select coalesce(unidade, '') from sae_enfermagem where id = :iddocumento";
            case AVALIACAO_ODONTOLOGICA ->
                    "select coalesce(unidade, '') from odontologia_atendimento where idatendimento = :iddocumento";
            case ATENDIMENTO_MULTIDISCIPLINAR ->
                    "select coalesce(unidade, '') from atendimentomultidisciplinar where id = :iddocumento";
            case "DOCUMENTO_ASSISTENCIAL_PASSAGEM_PLANTAO", "DOCUMENTO_ASSISTENCIAL_NOTIFICACAO_COMPULSORIA",
                    "DOCUMENTO_ASSISTENCIAL_TERMO_CONSENTIMENTO", "DOCUMENTO_ASSISTENCIAL_REGISTRO_OBITO",
                    "DOCUMENTO_ASSISTENCIAL_BUNDLE_CVD", "DOCUMENTO_ASSISTENCIAL_BUNDLE_CVC" ->
                    "select coalesce(unidade, '') from documento_assistencial where id = :iddocumento";
            default -> null;
        };

        if (sql == null) {
            throw new IllegalArgumentException("Tipo de documento nao suportado para assinatura.");
        }

        List<?> rows = entityManager.createNativeQuery(sql)
                .setParameter("iddocumento", iddocumento)
                .getResultList();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Documento nao localizado para assinatura.");
        }
        Object value = rows.get(0);
        return value == null ? "" : String.valueOf(value).trim();
    }

    @Transactional
    public AssinaturaClinica adendar(Integer idassinatura, AssinaturaClinicaDto dto) {
        return adendar(idassinatura, dto, AuthenticatedUser.empty());
    }

    @Transactional
    public AssinaturaClinica adendar(Integer idassinatura, AssinaturaClinicaDto dto, AuthenticatedUser user) {
        AssinaturaClinica origem = repository.findById(idassinatura);
        if (origem == null || !"ASSINADO".equalsIgnoreCase(origem.getStatus())) {
            return null;
        }
        if (dto == null || vazio(dto.getAdendoTexto())) {
            throw new IllegalArgumentException("Texto do adendo e obrigatorio.");
        }

        AssinaturaClinica adendo = new AssinaturaClinica();
        adendo.setTipoDocumento(origem.getTipoDocumento());
        adendo.setIddocumento(origem.getIddocumento());
        adendo.setIdbam(origem.getIdbam());
        adendo.setIdpaciente(origem.getIdpaciente());
        adendo.setUnidade(origem.getUnidade());
        adendo.setStatus("ADENDO");
        adendo.setConteudoHash(hash(safeText(dto.getAdendoTexto())));
        adendo.setConteudoResumo("Adendo de documento assinado");
        adendo.setAdendoTexto(safeText(dto.getAdendoTexto()));
        adendo.setAdendoPor(resolveNome(dto.getAdendoPor(), user));
        adendo.setAdendoPorMatricula(resolveMatricula(dto.getAdendoPorMatricula(), user));
        adendo.setAdendoEm(LocalDateTime.now());
        adendo.setAssinaturaOrigemId(origem.getIdassinatura());
        adendo.persist();
        return adendo;
    }

    public Map<String, Object> verificar(Integer idassinatura) {
        AssinaturaClinica assinatura = repository.findById(idassinatura);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("encontrada", assinatura != null);
        if (assinatura == null) {
            result.put("valida", false);
            result.put("motivo", "Assinatura nao localizada.");
            return result;
        }

        boolean hashPresente = !vazio(assinatura.getConteudoHash()) && assinatura.getConteudoHash().matches("^[0-9a-fA-F]{64}$");
        boolean assinadaOuAdendo = "ASSINADO".equalsIgnoreCase(assinatura.getStatus())
                || "ADENDO".equalsIgnoreCase(assinatura.getStatus());

        result.put("valida", hashPresente && assinadaOuAdendo);
        result.put("idassinatura", assinatura.getIdassinatura());
        result.put("tipoDocumento", assinatura.getTipoDocumento());
        result.put("status", assinatura.getStatus());
        result.put("conteudoHash", assinatura.getConteudoHash());
        result.put("algoritmo", "SHA-256");
        result.put("assinaturaDigital", assinatura.getAssinaturaDigital());
        result.put("algoritmoAssinatura", assinatura.getAlgoritmoAssinatura());
        result.put("chavePublica", assinatura.getChavePublica());
        result.put("assinadoPor", assinatura.getAssinadoPor());
        result.put("assinadoPorMatricula", assinatura.getAssinadoPorMatricula());
        result.put("assinadoEm", assinatura.getAssinadoEm());
        result.put("assinaturaOrigemId", assinatura.getAssinaturaOrigemId());

        if (!hashPresente) {
            result.put("motivo", "Hash ausente ou fora do padrao SHA-256 hexadecimal.");
        }

        // verificação criptográfica por algoritmo
        String algoritmoAssinatura = assinatura.getAlgoritmoAssinatura();
        String assinaturaDigital  = assinatura.getAssinaturaDigital();
        String conteudoHash       = assinatura.getConteudoHash();
        String chavePublica       = assinatura.getChavePublica();
        String matricula          = assinatura.getAssinadoPorMatricula();

        if (!hashPresente || !assinadaOuAdendo || vazio(assinaturaDigital)) {
            result.put("verificacaoCriptografica", false);
            result.put("motivoCripto", "Sem assinatura digital para verificar.");
        } else if ("ECDSA_P256_KEYPAIR_LOCAL".equals(algoritmoAssinatura)) {
            boolean ok = verificarEcdsaLocal(assinaturaDigital, conteudoHash, chavePublica);
            result.put("verificacaoCriptografica", ok);
            result.put("motivoCripto", ok ? "Assinatura ECDSA P-256 verificada com sucesso." : "Falha na verificacao ECDSA P-256.");
        } else if ("ICP_BRASIL_PFX_BANCO".equals(algoritmoAssinatura)) {
            boolean ok = verificarRsaPfx(assinaturaDigital, conteudoHash, matricula);
            result.put("verificacaoCriptografica", ok);
            result.put("motivoCripto", ok ? "Assinatura RSA ICP-Brasil verificada com sucesso." : "Falha na verificacao RSA com certificado do banco.");
        } else if ("ICP_BRASIL_WEBPKI".equals(algoritmoAssinatura)) {
            result.put("verificacaoCriptografica", null);
            result.put("motivoCripto", "Certificado ICP-Brasil validado pelo middleware no momento da assinatura (verificacao externa).");
        } else if ("SOMENTE_HASH".equals(algoritmoAssinatura)) {
            result.put("verificacaoCriptografica", false);
            result.put("motivoCripto", "Documento registrado apenas com hash — sem assinatura criptografica.");
        } else {
            result.put("verificacaoCriptografica", null);
            result.put("motivoCripto", "Algoritmo desconhecido: " + algoritmoAssinatura);
        }

        return result;
    }

    private boolean verificarEcdsaLocal(String assinaturaHex, String conteudoHash, String chavePublicaJwk) {
        try {
            if (vazio(chavePublicaJwk)) return false;
            PublicKey publicKey = jwkToEcPublicKey(chavePublicaJwk);
            byte[] sigP1363 = HexFormat.of().parseHex(assinaturaHex);
            byte[] sigDer   = p1363ToDer(sigP1363);
            byte[] hashBytes = HexFormat.of().parseHex(conteudoHash);

            // WebCrypto sign({ name:'ECDSA', hash:'SHA-256' }, key, hashBytes) → SHA256(hashBytes) internamente
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initVerify(publicKey);
            sig.update(hashBytes);
            return sig.verify(sigDer);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verificarRsaPfx(String assinaturaHex, String conteudoHash, String matricula) {
        try {
            if (vazio(matricula)) return false;
            Servidor servidor = servidorRepository.find("matricula", matricula).firstResult();
            if (servidor == null || vazio(servidor.getCertificadoPfxBase64())) return false;

            byte[] pfxBytes = Base64.getDecoder().decode(servidor.getCertificadoPfxBase64());
            char[] senha = vazio(servidor.getCertificadoPfxSenha()) ? new char[0] : servidor.getCertificadoPfxSenha().toCharArray();

            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(pfxBytes), senha);
            String alias = ks.aliases().nextElement();
            Certificate cert = ks.getCertificate(alias);
            PublicKey publicKey = cert.getPublicKey();

            byte[] sigBytes  = HexFormat.of().parseHex(assinaturaHex);
            // frontend: forge.md.sha256.update(hashHex) → SHA256(hashHex) → sign com RSA
            // backend : SHA256withRSA.update(hashHex.getBytes(UTF8)) → mesmo SHA256(hashHex)
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(conteudoHash.getBytes(StandardCharsets.UTF_8));
            return sig.verify(sigBytes);
        } catch (Exception e) {
            return false;
        }
    }

    private PublicKey jwkToEcPublicKey(String jwkJson) throws Exception {
        JsonObject jwk = Json.createReader(new StringReader(jwkJson)).readObject();
        byte[] x = Base64.getUrlDecoder().decode(jwk.getString("x"));
        byte[] y = Base64.getUrlDecoder().decode(jwk.getString("y"));

        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecParams = params.getParameterSpec(ECParameterSpec.class);

        ECPublicKeySpec spec = new ECPublicKeySpec(new ECPoint(new BigInteger(1, x), new BigInteger(1, y)), ecParams);
        return KeyFactory.getInstance("EC").generatePublic(spec);
    }

    // Converte assinatura ECDSA P1363 (WebCrypto) → DER (Java)
    private byte[] p1363ToDer(byte[] p1363) {
        int half = p1363.length / 2;
        byte[] r = trimZeros(Arrays.copyOfRange(p1363, 0, half));
        byte[] s = trimZeros(Arrays.copyOfRange(p1363, half, p1363.length));
        if ((r[0] & 0x80) != 0) r = prependZero(r);
        if ((s[0] & 0x80) != 0) s = prependZero(s);
        int total = 2 + r.length + 2 + s.length;
        byte[] der = new byte[2 + total];
        int i = 0;
        der[i++] = 0x30; der[i++] = (byte) total;
        der[i++] = 0x02; der[i++] = (byte) r.length;
        System.arraycopy(r, 0, der, i, r.length); i += r.length;
        der[i++] = 0x02; der[i++] = (byte) s.length;
        System.arraycopy(s, 0, der, i, s.length);
        return der;
    }

    private byte[] trimZeros(byte[] b) {
        int s = 0;
        while (s < b.length - 1 && b[s] == 0) s++;
        return s == 0 ? b : Arrays.copyOfRange(b, s, b.length);
    }

    private byte[] prependZero(byte[] b) {
        byte[] r = new byte[b.length + 1];
        System.arraycopy(b, 0, r, 1, b.length);
        return r;
    }

    private String hashConteudo(AssinaturaClinicaDto dto) {
        if (!vazio(dto.getConteudoHash())) {
            return safe(dto.getConteudoHash());
        }
        String base = String.join("|",
                safe(dto.getTipoDocumento()),
                String.valueOf(dto.getIddocumento()),
                String.valueOf(dto.getIdbam()),
                String.valueOf(dto.getIdpaciente()),
                safeText(dto.getConteudoResumo()));
        return hash(base);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel gerar hash da assinatura.", e);
        }
    }

    private String resolveNome(String fallback, AuthenticatedUser user) {
        if (user != null && !vazio(user.nome())) {
            return safe(user.nome());
        }
        return safe(fallback);
    }

    private String resolveMatricula(String fallback, AuthenticatedUser user) {
        if (user != null && !vazio(user.matricula())) {
            return safe(user.matricula());
        }
        return safe(fallback);
    }

    private boolean vazio(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
