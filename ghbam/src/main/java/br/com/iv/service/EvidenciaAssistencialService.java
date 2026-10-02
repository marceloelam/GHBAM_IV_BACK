package br.com.iv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.iv.entity.EvidenciaAssistencial;
import br.com.iv.repository.EvidenciaAssistencialRepository;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.dto.LogDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class EvidenciaAssistencialService {

    @Inject
    EvidenciaAssistencialRepository repository;

    @Inject
    IntegracaoSusCredencialService integracaoSusCredencialService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    LogService logService;

    public Map<String, Object> listar(String modulo, String unidade, String status, String origemModulo, String origemId) {
        String moduloNormalizado = normalizarModulo(modulo);
        List<EvidenciaAssistencial> registros = repository.listar(moduloNormalizado, unidade, status, origemModulo, origemId);
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("modulo", moduloNormalizado);
        resposta.put("total", repository.contar(moduloNormalizado, unidade, null, origemModulo, origemId));
        resposta.put("abertos", repository.contar(moduloNormalizado, unidade, "ABERTO", origemModulo, origemId));
        resposta.put("registros", registros);
        return resposta;
    }

    @Transactional
    public EvidenciaAssistencial criar(String modulo, EvidenciaAssistencial registro) {
        registro.idevidencia = null;
        registro.modulo = normalizarModulo(modulo);
        if (vazio(registro.tipo)) {
            throw new IllegalArgumentException("Informe o tipo do registro.");
        }
        // O apoio assistencial e por unidade: a listagem filtra por igualdade, entao
        // registro gravado sem unidade some de todas as telas filtradas em vez de
        // aparecer na unidade certa. O controller ja preenche com a unidade do
        // usuario, mas quem opera como TODAS nao tem unidade propria - nesse caso a
        // sigla precisa vir no corpo da requisicao.
        if (vazio(registro.unidade) || "TODAS".equalsIgnoreCase(registro.unidade.trim())) {
            throw new IllegalArgumentException("Unidade e obrigatoria para registrar no apoio assistencial.");
        }
        registro.unidade = registro.unidade.trim();
        if (vazio(registro.status)) {
            registro.status = "ABERTO";
        }
        repository.persist(registro);
        if ("RNDS".equals(registro.modulo)) {
            prepararDetalhesIniciaisRnds(registro);
        }
        return registro;
    }

    @Transactional
    public EvidenciaAssistencial atualizarStatus(String modulo, Long id, String status, String usuarioAtualizacao) {
        String moduloNormalizado = normalizarModulo(modulo);
        String statusNormalizado = normalizarStatus(status);
        EvidenciaAssistencial registro = repository.buscarDoModulo(id, moduloNormalizado);
        if (registro == null) {
            return null;
        }
        registro.status = statusNormalizado;
        registro.dataAtualizacao = LocalDateTime.now();
        registro.usuarioAtualizacao = usuarioAtualizacao;
        registrarLogFluxoCirurgico(registro, "DEMANDA " + moduloNormalizado + " " + statusNormalizado, usuarioAtualizacao);
        return registro;
    }

    @Transactional
    public EvidenciaAssistencial processarRnds(Long id, AuthenticatedUser user) {
        EvidenciaAssistencial registro = repository.buscarDoModulo(id, "RNDS");
        if (registro == null) {
            return null;
        }
        String unidade = registro.unidade == null || registro.unidade.isBlank()
                ? user == null ? "" : user.unidade()
                : registro.unidade;
        var credencial = integracaoSusCredencialService.buscar(unidade);
        if (credencial.isEmpty()) {
            registro.dataAtualizacao = LocalDateTime.now();
            registro.usuarioAtualizacao = user == null ? "" : user.matricula();
            atualizarDetalhesRnds(registro, Map.of(
                    "statusIntegracao", "ERRO",
                    "codigoRetorno", "SEM_CREDENCIAL",
                    "mensagemRetorno", "Credencial SUS/RNDS ativa nao configurada para a unidade."));
            integracaoSusCredencialService.registrarEvento(Map.of(
                    "unidade", unidade,
                    "ambiente", "NAO_CONFIGURADO",
                    "acao", "PROCESSAR_RNDS",
                    "status", "FALHA",
                    "detalhes", "Pendencia RNDS " + id + " sem credencial ativa."), user);
            throw new IllegalArgumentException("Configure uma credencial SUS/RNDS ativa para processar esta pendencia.");
        }

        String protocoloExistente = textoDetalhe(lerDetalhes(registro), "protocolo");
        String protocolo = vazio(protocoloExistente) ? gerarProtocoloRnds(id) : protocoloExistente;
        registro.status = "CONCLUIDO";
        registro.dataAtualizacao = LocalDateTime.now();
        registro.usuarioAtualizacao = user == null ? "" : user.matricula();
        atualizarDetalhesRnds(registro, Map.of(
                "protocolo", protocolo,
                "statusIntegracao", "PENDENTE_HOMOLOGACAO",
                "dataEnvio", LocalDateTime.now().toString(),
                "codigoRetorno", "PROCESSADO_LOCAL",
                "mensagemRetorno", "Fila RNDS processada localmente. Chamada externa DATASUS/RNDS permanece aguardando homologacao oficial.",
                "ambiente", credencial.get().getAmbiente()));
        integracaoSusCredencialService.registrarEvento(Map.of(
                "unidade", unidade,
                "ambiente", credencial.get().getAmbiente(),
                "acao", "PROCESSAR_RNDS",
                "status", "PROCESSADO_LOCAL",
                "detalhes", "Pendencia RNDS " + id + " processada com protocolo " + protocolo + "."), user);
        return registro;
    }

    private void prepararDetalhesIniciaisRnds(EvidenciaAssistencial registro) {
        Map<String, Object> detalhes = lerDetalhes(registro);
        Map<String, String> novosDetalhes = new HashMap<>();
        if (vazio(textoDetalhe(detalhes, "protocolo"))) {
            novosDetalhes.put("protocolo", gerarProtocoloRnds(registro.idevidencia));
        }
        if (vazio(textoDetalhe(detalhes, "identificadorExterno"))) {
            novosDetalhes.put("identificadorExterno", "urn:uuid:" + UUID.randomUUID());
        }
        if (!novosDetalhes.isEmpty()) {
            atualizarDetalhesRnds(registro, novosDetalhes);
        }
    }

    private String gerarProtocoloRnds(Long id) {
        return "GHBAM-RNDS-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + id;
    }

    private Map<String, Object> lerDetalhes(EvidenciaAssistencial registro) {
        Map<String, Object> detalhes = new HashMap<>();
        if (registro.detalhesJson != null && !registro.detalhesJson.isBlank()) {
            try {
                detalhes.putAll(objectMapper.readValue(registro.detalhesJson, new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ignored) {
                detalhes.put("detalhesAnteriores", registro.detalhesJson);
            }
        }
        return detalhes;
    }

    private String textoDetalhe(Map<String, Object> detalhes, String chave) {
        Object valor = detalhes.get(chave);
        return valor == null ? "" : valor.toString().trim();
    }

    private void atualizarDetalhesRnds(EvidenciaAssistencial registro, Map<String, String> novosDetalhes) {
        Map<String, Object> detalhes = lerDetalhes(registro);
        detalhes.putAll(novosDetalhes);
        try {
            registro.detalhesJson = objectMapper.writeValueAsString(detalhes);
        } catch (Exception e) {
            registro.detalhesJson = novosDetalhes.toString();
        }
    }

    private String normalizarModulo(String modulo) {
        if (vazio(modulo)) {
            throw new IllegalArgumentException("Modulo assistencial nao informado.");
        }
        String valor = modulo.trim().toUpperCase();
        if (!valor.matches("CME|HEMOTERAPIA|SND|RNDS")) {
            throw new IllegalArgumentException("Modulo assistencial invalido.");
        }
        return valor;
    }

    private String normalizarStatus(String status) {
        if (vazio(status)) {
            throw new IllegalArgumentException("Situacao nao informada.");
        }
        String valor = status.trim().toUpperCase();
        if (!valor.matches("ABERTO|CONCLUIDO|CANCELADO")) {
            throw new IllegalArgumentException("Situacao invalida.");
        }
        return valor;
    }

    private boolean vazio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }

    private void registrarLogFluxoCirurgico(EvidenciaAssistencial registro, String evento, String usuario) {
        if (!"CENTRO_CIRURGICO".equals(registro.origemModulo)) return;
        LogDto log = new LogDto();
        log.setData(LocalDate.now());
        log.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        log.setUnidade(vazio(registro.unidade) ? "GERAL" : registro.unidade);
        log.setEvento(evento + " - cirurgia " + registro.origemId + " - paciente " + (registro.nomepaciente == null ? "" : registro.nomepaciente)
                + (vazio(usuario) ? "" : " - por matricula " + usuario));
        logService.salvarLogAsync(log);
    }
}
