package br.com.iv.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import br.com.iv.dto.FhirEnvioDestinoDto;
import br.com.iv.entity.FhirEnvioDestino;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FhirEnvioDestinoService {

    private static final List<String> TIPOS_AUTENTICACAO = List.of("NENHUMA", "BEARER_TOKEN", "BASIC", "API_KEY_HEADER", "RNDS_CERTIFICADO");

    @Inject
    FhirCredencialCryptoService cryptoService;

    public Optional<FhirEnvioDestinoDto> buscar(String unidade, String finalidade) {
        String unidadeNormalizada = normalizar(unidade);
        String finalidadeNormalizada = limpar(finalidade, "SUMARIO_ALTA").toUpperCase(Locale.ROOT);
        return FhirEnvioDestino
                .<FhirEnvioDestino>find("lower(unidade) = ?1 and finalidade = ?2", unidadeNormalizada.toLowerCase(Locale.ROOT), finalidadeNormalizada)
                .stream()
                .max(Comparator.comparing(FhirEnvioDestino::getAtualizadoEm, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toSafeDto);
    }

    public Optional<FhirEnvioDestino> buscarAtivoParaEnvio(String unidade, String finalidade) {
        String unidadeNormalizada = normalizar(unidade);
        String finalidadeNormalizada = limpar(finalidade, "SUMARIO_ALTA").toUpperCase(Locale.ROOT);
        return FhirEnvioDestino
                .<FhirEnvioDestino>find("lower(unidade) = ?1 and finalidade = ?2 and ativo = true",
                        unidadeNormalizada.toLowerCase(Locale.ROOT), finalidadeNormalizada)
                .firstResultOptional();
    }

    @Transactional
    public FhirEnvioDestinoDto salvar(FhirEnvioDestinoDto dto, AuthenticatedUser user) {
        String unidade = normalizar(dto == null ? null : dto.getUnidade());
        if (unidade.isBlank()) {
            unidade = normalizar(user == null ? "" : user.unidade());
        }
        if (unidade.isBlank()) {
            throw new IllegalArgumentException("Unidade obrigatoria para configurar destino de envio FHIR.");
        }
        String finalidade = limpar(dto.getFinalidade(), "SUMARIO_ALTA").toUpperCase(Locale.ROOT);

        FhirEnvioDestino destino = FhirEnvioDestino
                .<FhirEnvioDestino>find("lower(unidade) = ?1 and finalidade = ?2", unidade.toLowerCase(Locale.ROOT), finalidade)
                .firstResultOptional()
                .orElseGet(FhirEnvioDestino::new);

        validarAntesDeSalvar(dto);

        boolean novo = destino.getIdfhirenviodestino() == null;
        destino.setUnidade(unidade);
        destino.setFinalidade(finalidade);
        destino.setAmbiente(limpar(dto.getAmbiente(), "HOMOLOGACAO").toUpperCase(Locale.ROOT));
        destino.setEndpointBase(limpar(dto.getEndpointBase(), null));
        destino.setTipoAutenticacao(limpar(dto.getTipoAutenticacao(), "NENHUMA").toUpperCase(Locale.ROOT));
        destino.setApiKeyHeaderNome(limpar(dto.getApiKeyHeaderNome(), null));
        destino.setAtivo(dto.getAtivo() == null ? Boolean.FALSE : dto.getAtivo());
        destino.setAtualizadoPor(user == null ? "" : user.matricula());
        destino.setAtualizadoEm(LocalDateTime.now());
        if (novo) {
            destino.setCriadoPor(user == null ? "" : user.matricula());
            destino.setCriadoEm(LocalDateTime.now());
        }
        if (preenchido(dto.getCredencial())) {
            destino.setCredencialCriptografada(cryptoService.encrypt(dto.getCredencial()));
        }

        destino.persist();
        return toSafeDto(destino);
    }

    public String credencialDecifrada(FhirEnvioDestino destino) {
        if (destino == null || !preenchido(destino.getCredencialCriptografada())) {
            return null;
        }
        return cryptoService.decrypt(destino.getCredencialCriptografada());
    }

    private void validarAntesDeSalvar(FhirEnvioDestinoDto dto) {
        List<String> erros = new ArrayList<>();
        String ambiente = limpar(dto.getAmbiente(), "HOMOLOGACAO").toUpperCase(Locale.ROOT);
        if (!"HOMOLOGACAO".equals(ambiente) && !"PRODUCAO".equals(ambiente)) {
            erros.add("Ambiente deve ser HOMOLOGACAO ou PRODUCAO.");
        }
        String tipoAutenticacao = limpar(dto.getTipoAutenticacao(), "NENHUMA").toUpperCase(Locale.ROOT);
        if (!TIPOS_AUTENTICACAO.contains(tipoAutenticacao)) {
            erros.add("Tipo de autenticacao invalido.");
        }
        if (Boolean.TRUE.equals(dto.getAtivo()) && !urlValida(dto.getEndpointBase())) {
            erros.add("Informe um Endpoint base valido (http:// ou https://) para ativar o envio.");
        }
        if (Boolean.TRUE.equals(dto.getAtivo()) && "RNDS_CERTIFICADO".equals(tipoAutenticacao)
                && !"https".equalsIgnoreCase(esquema(dto.getEndpointBase()))) {
            erros.add("Destinos RNDS devem usar HTTPS, inclusive em homologacao.");
        }
        if (Boolean.TRUE.equals(dto.getAtivo()) && "PRODUCAO".equals(ambiente)
                && !"https".equalsIgnoreCase(esquema(dto.getEndpointBase()))) {
            erros.add("Destinos de producao devem usar HTTPS.");
        }
        if (Boolean.TRUE.equals(dto.getAtivo()) && "NENHUMA".equals(tipoAutenticacao)) {
            erros.add("Destino ativo exige autenticacao configurada.");
        }
        if (Boolean.TRUE.equals(dto.getAtivo()) && !"NENHUMA".equals(tipoAutenticacao)
                && !preenchido(dto.getCredencial()) && !cryptoService.isConfigured()) {
            erros.add("Configure GHBAM_FHIR_CREDENTIALS_KEY no servidor antes de salvar credenciais.");
        }
        // Na RNDS a Credencial e o CNS do profissional requisitante (header Authorization).
        if ("RNDS_CERTIFICADO".equals(tipoAutenticacao) && preenchido(dto.getCredencial())
                && !dto.getCredencial().trim().matches("\\d{15}")) {
            erros.add("Para RNDS, a Credencial deve ser o CNS do profissional requisitante (15 digitos).");
        }
        if (!erros.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", erros));
        }
    }

    private FhirEnvioDestinoDto toSafeDto(FhirEnvioDestino destino) {
        FhirEnvioDestinoDto dto = new FhirEnvioDestinoDto();
        dto.setIdfhirenviodestino(destino.getIdfhirenviodestino());
        dto.setUnidade(destino.getUnidade());
        dto.setFinalidade(destino.getFinalidade());
        dto.setAmbiente(destino.getAmbiente());
        dto.setEndpointBase(destino.getEndpointBase());
        dto.setTipoAutenticacao(destino.getTipoAutenticacao());
        dto.setApiKeyHeaderNome(destino.getApiKeyHeaderNome());
        dto.setAtivo(destino.getAtivo());
        dto.setPossuiCredencial(preenchido(destino.getCredencialCriptografada()));
        return dto;
    }

    private boolean urlValida(String valor) {
        if (!preenchido(valor)) {
            return false;
        }
        try {
            URI uri = new URI(valor.trim());
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || !preenchido(uri.getHost()) || uri.getUserInfo() != null || uri.getFragment() != null) {
                return false;
            }
            InetAddress endereco = InetAddress.getByName(uri.getHost());
            return !endereco.isAnyLocalAddress() && !endereco.isLoopbackAddress()
                    && !endereco.isLinkLocalAddress() && !endereco.isSiteLocalAddress();
        } catch (URISyntaxException e) {
            return false;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private String esquema(String valor) {
        if (!preenchido(valor)) return "";
        try { return new URI(valor.trim()).getScheme(); } catch (URISyntaxException e) { return ""; }
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private String limpar(String valor, String fallback) {
        return preenchido(valor) ? valor.trim() : fallback;
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
