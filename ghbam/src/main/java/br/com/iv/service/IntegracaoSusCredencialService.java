package br.com.iv.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import br.com.iv.dto.IntegracaoSusCredencialDto;
import br.com.iv.dto.LogDto;
import br.com.iv.entity.IntegracaoSusAuditoria;
import br.com.iv.entity.IntegracaoSusCredencial;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class IntegracaoSusCredencialService {

    @Inject
    IntegracaoSusCryptoService cryptoService;

    @Inject
    LogService logService;

    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    /**
     * A "unidade" desta tabela pode ser, na verdade, o codigo do GESTOR (municipio) que
     * agrupa varias unidades - ver ConfiguracaoUnidade#codigoGestor. Isso espelha o
     * proprio credenciamento oficial do DATASUS: o certificado fica associado a um
     * "estabelecimento cessionario" (o gestor), com as unidades como "estabelecimentos
     * filhos" do mesmo credenciamento, nao um certificado por unidade.
     *
     * Sem codigoGestor configurado para a unidade (caso comum, e o unico ate esta
     * mudanca), a chave efetiva e a propria sigla - comportamento identico ao anterior.
     */
    private String chaveEfetiva(String unidade) {
        String sigla = normalizar(unidade);
        if (sigla.isBlank()) {
            return sigla;
        }
        String gestor = configuracaoUnidadeRepository.buscarCodigoGestorPorSigla(sigla);
        return gestor != null && !gestor.isBlank() ? gestor.trim().toUpperCase() : sigla;
    }

    public Optional<IntegracaoSusCredencialDto> buscar(String unidade) {
        return IntegracaoSusCredencial.<IntegracaoSusCredencial>find("lower(unidade) = ?1", chaveEfetiva(unidade).toLowerCase())
                .stream()
                .max(Comparator.comparing(IntegracaoSusCredencial::getAtualizadoEm, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toSafeDto);
    }

    /**
     * Entidade completa (com segredos ainda criptografados) para uso interno de
     * servicos que precisam de fato chamar o DATASUS/RNDS - ex.: CadsusPdqClienteService.
     * Nunca expor isto direto num controller: use sempre {@link #buscar(String)} (DTO seguro)
     * para qualquer coisa que volte para o frontend.
     */
    public Optional<IntegracaoSusCredencial> buscarEntidadeAtiva(String unidade) {
        return IntegracaoSusCredencial.<IntegracaoSusCredencial>find("lower(unidade) = ?1 and ativo = true", chaveEfetiva(unidade).toLowerCase())
                .stream()
                .max(Comparator.comparing(IntegracaoSusCredencial::getAtualizadoEm, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    public List<IntegracaoSusAuditoria> auditoria(String unidade) {
        return IntegracaoSusAuditoria.list("lower(unidade) = ?1 order by criadoEm desc", normalizar(unidade).toLowerCase());
    }

    @Transactional
    public IntegracaoSusCredencialDto salvar(IntegracaoSusCredencialDto dto, AuthenticatedUser user) {
        String unidadeInformada = normalizar(dto == null ? null : dto.getUnidade());
        if (unidadeInformada.isBlank()) {
            unidadeInformada = normalizar(user == null ? "" : user.unidade());
        }
        if (unidadeInformada.isBlank()) {
            throw new IllegalArgumentException("Unidade obrigatoria para configurar integracao SUS.");
        }
        // Se a unidade informada pertence a um gestor (municipio), a credencial e salva
        // sob o codigo do GESTOR - assim, cadastrar uma vez ja vale para todas as
        // unidades daquele municipio, em vez de repetir o cadastro unidade por unidade.
        String unidade = chaveEfetiva(unidadeInformada);

        IntegracaoSusCredencial credencial = IntegracaoSusCredencial
                .<IntegracaoSusCredencial>find("lower(unidade) = ?1", unidade.toLowerCase())
                .firstResultOptional()
                .orElseGet(IntegracaoSusCredencial::new);

        validarAntesDeSalvar(dto, credencial);

        boolean novo = credencial.getIdintegracaosuscredencial() == 0;
        credencial.setUnidade(unidade);
        credencial.setAmbiente(limpar(dto.getAmbiente(), "HOMOLOGACAO").toUpperCase());
        credencial.setOrgaoGestor(limpar(dto.getOrgaoGestor(), null));
        credencial.setUsuarioTecnico(limpar(dto.getUsuarioTecnico(), null));
        credencial.setClientId(limpar(dto.getClientId(), null));
        credencial.setCertificadoAlias(limpar(dto.getCertificadoAlias(), null));
        credencial.setEndpointBase(limpar(dto.getEndpointBase(), null));
        credencial.setAtivo(dto.getAtivo() == null ? Boolean.TRUE : dto.getAtivo());
        credencial.setAtualizadoPor(user == null ? "" : user.matricula());
        credencial.setAtualizadoEm(LocalDateTime.now());
        if (novo) {
            credencial.setCriadoPor(user == null ? "" : user.matricula());
            credencial.setCriadoEm(LocalDateTime.now());
        }

        if (preenchido(dto.getClientSecret())) {
            credencial.setClientSecretCriptografado(cryptoService.encrypt(dto.getClientSecret()));
        }
        if (preenchido(dto.getCertificadoPfxBase64())) {
            credencial.setCertificadoPfxCriptografado(cryptoService.encrypt(dto.getCertificadoPfxBase64()));
        }
        if (preenchido(dto.getCertificadoSenha())) {
            credencial.setCertificadoSenhaCriptografada(cryptoService.encrypt(dto.getCertificadoSenha()));
        }

        credencial.persist();
        registrarAuditoria(unidade, credencial.getAmbiente(), "SALVAR_CREDENCIAL", "SUCESSO",
                "Credencial SUS atualizada. Segredos recebidos: clientSecret=" + preenchido(dto.getClientSecret())
                        + ", certificado=" + preenchido(dto.getCertificadoPfxBase64()),
                user);
        return toSafeDto(credencial);
    }

    private void validarAntesDeSalvar(IntegracaoSusCredencialDto dto, IntegracaoSusCredencial existente) {
        List<String> erros = new ArrayList<>();
        String ambiente = limpar(dto.getAmbiente(), "HOMOLOGACAO").toUpperCase();
        if (!"HOMOLOGACAO".equals(ambiente) && !"PRODUCAO".equals(ambiente)) {
            erros.add("Ambiente deve ser HOMOLOGACAO ou PRODUCAO.");
        }
        if (!preenchido(dto.getOrgaoGestor())) {
            erros.add("Informe o orgao gestor/autorizador.");
        }
        if (!preenchido(dto.getUsuarioTecnico())) {
            erros.add("Informe o usuario tecnico autorizado.");
        }

        boolean possuiSecret = preenchido(dto.getClientSecret())
                || (existente != null && preenchido(existente.getClientSecretCriptografado()));
        boolean possuiCertificado = preenchido(dto.getCertificadoPfxBase64())
                || (existente != null && preenchido(existente.getCertificadoPfxCriptografado()));

        // Endpoint so e obrigatorio quando NAO ha certificado: a consulta oficial do
        // CNS/PDQ usa enderecos fixos definidos pelo DATASUS (ver CadsusPdqClienteService)
        // e ignora este campo. Se vier preenchido mesmo assim, precisa ser uma URL valida.
        if (preenchido(dto.getEndpointBase())) {
            if (!urlValida(dto.getEndpointBase())) {
                erros.add("Endpoint base preenchido, mas invalido - deve comecar com http:// ou https://.");
            }
        } else if (!possuiCertificado) {
            erros.add("Informe um Endpoint base valido (obrigatorio quando nao ha certificado digital cadastrado).");
        }

        if (!possuiSecret && !possuiCertificado) {
            erros.add("Informe pelo menos um meio de autenticacao: Client secret/token ou certificado PFX/A1.");
        }
        if (preenchido(dto.getClientSecret()) && !preenchido(dto.getClientId())) {
            erros.add("Informe o Client ID quando preencher Client secret/token.");
        }
        if (preenchido(dto.getCertificadoPfxBase64()) && !preenchido(dto.getCertificadoSenha())) {
            erros.add("Informe a senha do certificado PFX/A1.");
        }
        if (preenchido(dto.getCertificadoSenha()) && !preenchido(dto.getCertificadoPfxBase64())
                && (existente == null || !preenchido(existente.getCertificadoPfxCriptografado()))) {
            erros.add("Selecione ou cole o certificado PFX/A1 antes de informar a senha.");
        }
        if ((preenchido(dto.getClientSecret()) || preenchido(dto.getCertificadoPfxBase64()) || preenchido(dto.getCertificadoSenha()))
                && !cryptoService.isConfigured()) {
            erros.add("Configure GHBAM_SUS_CREDENTIALS_KEY no servidor antes de salvar segredos oficiais.");
        }
        if (!erros.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", erros));
        }
    }

    private boolean urlValida(String valor) {
        if (!preenchido(valor)) {
            return false;
        }
        try {
            URI uri = new URI(valor.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && preenchido(uri.getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * @param testeReal executa a chamada de verdade contra o DATASUS quando ha certificado
     *                  digital configurado - ver {@code CadsusPdqClienteService#testarConexao}.
     *                  Recebido por parametro (em vez de injetado) para nao criar dependencia
     *                  circular: aquele servico ja injeta este para ler a credencial da unidade.
     */
    @Transactional
    public Map<String, Object> testar(String unidade, AuthenticatedUser user, Function<String, Map<String, Object>> testeReal) {
        String unidadeNormalizada = normalizar(unidade);
        Optional<IntegracaoSusCredencial> credencialOpt = IntegracaoSusCredencial
                .<IntegracaoSusCredencial>find("lower(unidade) = ?1 and ativo = true", chaveEfetiva(unidadeNormalizada).toLowerCase())
                .firstResultOptional();

        if (credencialOpt.isEmpty()) {
            registrarAuditoria(unidadeNormalizada, "NAO_CONFIGURADO", "TESTAR_CONEXAO", "FALHA",
                    "Nenhuma credencial ativa encontrada para a unidade.", user);
            return Map.of("ok", false, "mensagem", "Nenhuma credencial ativa encontrada para a unidade.");
        }

        IntegracaoSusCredencial credencial = credencialOpt.get();
        boolean possuiCertificado = preenchido(credencial.getCertificadoPfxCriptografado());
        boolean possuiSegredo = preenchido(credencial.getClientSecretCriptografado()) || possuiCertificado;
        boolean possuiEndpoint = preenchido(credencial.getEndpointBase());

        boolean ok;
        String status;
        String detalhes;
        if (possuiCertificado) {
            // So o certificado digital da para testar de fato: a API oficial do CNS/PDQ
            // exige Two-way SSL, client secret isolado nao autentica nela.
            Map<String, Object> resultado = testeReal.apply(unidadeNormalizada);
            ok = Boolean.TRUE.equals(resultado.get("ok"));
            status = String.valueOf(resultado.getOrDefault("status", ok ? "CONECTADO" : "FALHA_CONEXAO"));
            detalhes = String.valueOf(resultado.getOrDefault("mensagem", "Teste executado."));
        } else if (possuiSegredo) {
            ok = false;
            status = "PENDENTE_HOMOLOGACAO";
            detalhes = "Client secret configurado, mas a API oficial do CNS/PDQ exige certificado digital ICP-Brasil (PFX/A1) "
                    + "para autenticar via Two-way SSL. Cadastre o certificado para testar a conexao real.";
        } else {
            ok = false;
            status = "PENDENTE_CONFIGURACAO";
            detalhes = "Nenhum certificado ou client secret cadastrado ainda. endpoint=" + possuiEndpoint + ".";
        }

        registrarAuditoria(unidadeNormalizada, credencial.getAmbiente(), "TESTAR_CONEXAO", status, detalhes, user);
        return Map.of("ok", ok, "status", status, "mensagem", detalhes);
    }

    @Transactional
    public void registrarEvento(Map<String, Object> dto, AuthenticatedUser user) {
        String unidade = normalizar(texto(dto, "unidade"));
        if (unidade.isBlank()) {
            unidade = normalizar(user == null ? "" : user.unidade());
        }
        String ambiente = limpar(texto(dto, "ambiente"), "HOMOLOGACAO");
        String acao = normalizar(texto(dto, "acao"));
        if (acao.isBlank()) {
            acao = "ACESSO_TELA";
        }
        String status = normalizar(texto(dto, "status"));
        if (status.isBlank()) {
            status = "REGISTRADO";
        }
        String detalhes = limpar(texto(dto, "detalhes"), "Evento de auditoria da Integracao SUS.");
        if (detalhes.length() > 500) {
            detalhes = detalhes.substring(0, 500);
        }
        registrarAuditoria(unidade, ambiente, acao, status, detalhes, user);
    }

    private IntegracaoSusCredencialDto toSafeDto(IntegracaoSusCredencial credencial) {
        IntegracaoSusCredencialDto dto = new IntegracaoSusCredencialDto();
        dto.setIdintegracaosuscredencial(credencial.getIdintegracaosuscredencial());
        dto.setUnidade(credencial.getUnidade());
        dto.setAmbiente(credencial.getAmbiente());
        dto.setOrgaoGestor(credencial.getOrgaoGestor());
        dto.setUsuarioTecnico(credencial.getUsuarioTecnico());
        dto.setClientId(credencial.getClientId());
        dto.setCertificadoAlias(credencial.getCertificadoAlias());
        dto.setEndpointBase(credencial.getEndpointBase());
        dto.setAtivo(credencial.getAtivo());
        dto.setPossuiClientSecret(preenchido(credencial.getClientSecretCriptografado()));
        dto.setPossuiCertificado(preenchido(credencial.getCertificadoPfxCriptografado()));
        return dto;
    }

    private void registrarAuditoria(String unidade, String ambiente, String acao, String status, String detalhes, AuthenticatedUser user) {
        IntegracaoSusAuditoria auditoria = new IntegracaoSusAuditoria();
        auditoria.setUnidade(normalizar(unidade));
        auditoria.setAmbiente(limpar(ambiente, "HOMOLOGACAO"));
        auditoria.setAcao(acao);
        auditoria.setStatus(status);
        auditoria.setUsuario(user == null ? "" : user.nome());
        auditoria.setMatricula(user == null ? "" : user.matricula());
        auditoria.setDetalhes(detalhes);
        auditoria.setCriadoEm(LocalDateTime.now());
        auditoria.persist();

        LogDto log = new LogDto();
        log.setData(LocalDate.now());
        log.setHora(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        log.setUnidade(normalizar(unidade));
        log.setEvento("INTEGRACAO_SUS " + acao + " " + status + " - " + detalhes);
        logService.salvarLogAsync(log);
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase();
    }

    private String limpar(String valor, String padrao) {
        if (valor == null || valor.trim().isBlank()) {
            return padrao;
        }
        return valor.trim();
    }

    private String texto(Map<String, Object> dto, String chave) {
        if (dto == null || !dto.containsKey(chave) || dto.get(chave) == null) {
            return "";
        }
        return String.valueOf(dto.get(chave));
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.trim().isBlank();
    }
}
