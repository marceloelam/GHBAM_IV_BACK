package br.com.iv.service;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.dto.LogDto;
import br.com.iv.dto.ZamedEnvioRespostaDto;
import br.com.iv.dto.ZamedItemSolicitacaoDto;
import br.com.iv.dto.ZamedSolicitacaoDto;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Internacao;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.Prescricao;
import br.com.iv.entity.SetorCenso;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ZamedEnvioService implements Serializable {
    private static final long serialVersionUID = 1L;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @ConfigProperty(name = "zamed.endpoint", defaultValue = "https://zamed.tec.br/itaguai/apiservice/ghbam/prescricoes.php")
    String endpoint;

    @ConfigProperty(name = "zamed.autenticacao", defaultValue = "wmH0OocASo")
    String autenticacao;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EntityManager entityManager;

    @Inject
    LogService logService;

    @Transactional
    public ZamedEnvioRespostaDto enviar(ZamedSolicitacaoDto solicitacao) {
        if (solicitacao == null) {
            throw new IllegalArgumentException("Solicitacao Zamed nao informada.");
        }

        List<ZamedItemSolicitacaoDto> itensValidos = itensValidos(solicitacao);
        if (itensValidos.isEmpty()) {
            registrarLog("ENVIO_BLOQUEADO_SEM_ITENS", solicitacao,
                    "itensRecebidos=" + (solicitacao.getItens() == null ? 0 : solicitacao.getItens().size()));
            throw new IllegalArgumentException("Nenhum item com medicamento e retirada maior que zero foi informado.");
        }

        Map<String, Object> payload = montarPayloadZamed(solicitacao, itensValidos);
        registrarLog("TENTAR_ENVIO", solicitacao,
                "itens=" + itensValidos.size()
                        + " | endpoint=" + compact(endpoint)
                        + " | medicamentos=" + medicamentosResumo(itensValidos));

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            registrarLog("ERRO_MONTAR_JSON", solicitacao, "erro=" + e.getMessage());
            throw new IllegalStateException("Nao foi possivel montar o JSON da Zamed.", e);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ZamedEnvioRespostaDto resposta = new ZamedEnvioRespostaDto();
            resposta.setEndpoint(endpoint);
            resposta.setStatusCode(response.statusCode());
            resposta.setRespostaZamed(response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                resposta.setEnviado(false);
                resposta.setMensagem("Zamed retornou HTTP " + response.statusCode());
                registrarLog("FALHA_HTTP", solicitacao,
                        "status=" + response.statusCode()
                                + " | resposta=" + limitar(response.body(), 350));
                return resposta;
            }

            List<Integer> idsMarcados = marcarPrescricoesEnviadas(itensValidos);
            resposta.setEnviado(true);
            resposta.setMensagem("Pedido enviado para a Zamed.");
            registrarLog("ENVIO_SUCESSO", solicitacao,
                    "status=" + response.statusCode()
                            + " | itens=" + itensValidos.size()
                            + " | prescricoesMarcadas=" + idsMarcados
                            + " | resposta=" + limitar(response.body(), 350));
            return resposta;
        } catch (Exception e) {
            ZamedEnvioRespostaDto resposta = new ZamedEnvioRespostaDto();
            resposta.setEnviado(false);
            resposta.setStatusCode(0);
            resposta.setEndpoint(endpoint);
            resposta.setMensagem("Falha ao chamar a Zamed: " + e.getMessage());
            resposta.setRespostaZamed("");
            registrarLog("ERRO_ENVIO", solicitacao, "erro=" + e.getMessage());
            return resposta;
        }
    }

    private List<ZamedItemSolicitacaoDto> itensValidos(ZamedSolicitacaoDto solicitacao) {
        List<ZamedItemSolicitacaoDto> itens = solicitacao.getItens() == null ? List.of() : solicitacao.getItens();
        List<ZamedItemSolicitacaoDto> validos = new ArrayList<>();
        for (ZamedItemSolicitacaoDto item : itens) {
            if (item == null) continue;
            if (compact(item.getMedicamentoSolicitado()).isEmpty()) continue;
            if (item.getRetirada() <= 0) continue;
            validos.add(item);
        }
        return validos;
    }

    private Map<String, Object> montarPayloadZamed(ZamedSolicitacaoDto solicitacao, List<ZamedItemSolicitacaoDto> itens) {
        int bamNumero = parseInt(solicitacao.getBam(), 0);
        Bam bam = bamNumero > 0 ? Bam.findById(bamNumero) : null;
        Pacientes paciente = bam != null ? bam.getPaciente() : null;
        Internacao internacao = buscarInternacao(bamNumero);
        Prescricao prescricaoBase = buscarPrescricaoBase(itens, bamNumero);

        String setor = primeiroTexto(solicitacao.getSetor(), prescricaoBase != null ? prescricaoBase.getLocalsolicitante() : null);
        String leito = primeiroTexto(solicitacao.getLeito(), internacao != null ? internacao.getLocal() : null);
        String nomeSecao = montarNomeSecao(setor, leito);
        int idSetor = buscarIdSetor(setor, solicitacao.getUnidade());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("autenticacao", autenticacao);
        payload.put("nome_medico", prescricaoBase != null ? compact(prescricaoBase.getMedicoq()) : "");
        payload.put("registro_medico", prescricaoBase != null ? compact(prescricaoBase.getMediccrm()) : "");
        payload.put("BAM", String.valueOf(bamNumero > 0 ? bamNumero : compact(solicitacao.getBam())));
        payload.put("data_solicitacao_prescricao", dataHoraSolicitacao(prescricaoBase, itens.get(0)));
        payload.put("id_setor", idSetor);
        payload.put("nome_secao_solicitante_prescricao", nomeSecao);
        payload.put("nome_paciente", primeiroTexto(paciente != null ? paciente.getNome() : null, solicitacao.getPacienteNome()));
        payload.put("cpf_paciente", paciente != null ? compact(paciente.getCpf()) : "");
        payload.put("logradouro_paciente", paciente != null ? compact(paciente.getEndereco()) : "");
        payload.put("bairro_paciente", paciente != null ? compact(paciente.getBairro()) : "");
        payload.put("cep_paciente", cepValido(paciente != null ? paciente.getCep() : null));
        payload.put("cidade_paciente", paciente != null ? compact(paciente.getMunicipio()) : "");
        payload.put("uf_paciente", paciente != null ? compact(paciente.getEstado()) : "");
        payload.put("dt_nascimento_paciente", paciente != null ? compact(paciente.getNascimento()) : "");
        payload.put("produto", montarProdutos(itens));
        return payload;
    }

    private List<Map<String, Object>> montarProdutos(List<ZamedItemSolicitacaoDto> itens) {
        List<Map<String, Object>> produtos = new ArrayList<>();
        for (ZamedItemSolicitacaoDto item : itens) {
            Map<String, Object> produto = new LinkedHashMap<>();
            produto.put("aprazamento", compact(item.getPrescricao()));
            produto.put("idcatalogo", idCatalogo(item));
            produto.put("nome_completo", compact(item.getMedicamentoSolicitado()));
            produto.put("quantidade", item.getRetirada());
            produto.put("observacao", compact(item.getObservacao()));
            produtos.add(produto);
        }
        return produtos;
    }

    private List<Integer> marcarPrescricoesEnviadas(List<ZamedItemSolicitacaoDto> itens) {
        List<Integer> idsMarcados = new ArrayList<>();
        for (ZamedItemSolicitacaoDto item : itens) {
            int id = parseInt(item.getIdPrescricao(), 0);
            if (id <= 0) continue;

            int atualizados = entityManager.createNativeQuery(
                    "UPDATE prescricao SET farmfez = true, medicamentopedido = :medicamento, retirada = :retirada, observacao3 = :observacao WHERE idprescricao = :id")
                    .setParameter("id", id)
                    .setParameter("medicamento", compact(item.getMedicamentoSolicitado()))
                    .setParameter("retirada", item.getRetirada())
                    .setParameter("observacao", compact(item.getObservacao()))
                    .executeUpdate();
            if (atualizados > 0) {
                idsMarcados.add(id);
            }
        }
        return idsMarcados;
    }

    private Prescricao buscarPrescricaoBase(List<ZamedItemSolicitacaoDto> itens, int bam) {
        for (ZamedItemSolicitacaoDto item : itens) {
            int id = parseInt(item.getIdPrescricao(), 0);
            if (id > 0) {
                Prescricao prescricao = Prescricao.findById(id);
                if (prescricao != null) return prescricao;
            }
        }
        if (bam > 0) {
            return Prescricao.find("bam = ?1 order by idprescricao desc", bam).firstResult();
        }
        return null;
    }

    private Internacao buscarInternacao(int bam) {
        if (bam <= 0) return null;
        return Internacao.find("bam = ?1", String.valueOf(bam)).firstResult();
    }

    private int buscarIdSetor(String setor, String unidade) {
        String setorLimpo = compact(setor);
        if (setorLimpo.isEmpty()) return 0;

        SetorCenso encontrado = null;
        String unidadeLimpa = compact(unidade);
        if (!unidadeLimpa.isEmpty()) {
            encontrado = SetorCenso.find("upper(setor) = ?1 and upper(unidade) = ?2 and excluido = false",
                    setorLimpo.toUpperCase(), unidadeLimpa.toUpperCase()).firstResult();
        }
        if (encontrado == null) {
            encontrado = SetorCenso.find("upper(setor) = ?1 and excluido = false", setorLimpo.toUpperCase()).firstResult();
        }
        return encontrado != null ? encontrado.getIdsetorcenso() : 0;
    }

    private String dataHoraSolicitacao(Prescricao prescricao, ZamedItemSolicitacaoDto item) {
        String data = primeiroTexto(prescricao != null ? prescricao.getData() : null, item.getData());
        String hora = primeiroTexto(prescricao != null ? prescricao.getHora() : null, item.getHora());
        if (!data.isEmpty()) {
            return hora.isEmpty() ? data : data + " " + hora;
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String montarNomeSecao(String setor, String leito) {
        String setorLimpo = compact(setor);
        String leitoLimpo = compact(leito);
        if (setorLimpo.isEmpty()) return leitoLimpo;
        if (leitoLimpo.isEmpty()) return setorLimpo;
        return setorLimpo + " / leito : " + leitoLimpo;
    }

    private int idCatalogo(ZamedItemSolicitacaoDto item) {
        int id = parseInt(item.getIdzamed(), 0);
        if (id > 0) return id;

        String medicamento = compact(item.getMedicamentoSolicitado());
        String apenasDigitos = medicamento.replaceAll("\\D+", "");
        if (apenasDigitos.length() >= 4) {
            return parseInt(apenasDigitos.substring(apenasDigitos.length() - 4), 0);
        }
        return 0;
    }

    private String cepValido(String cep) {
        String valor = compact(cep);
        return valor.isEmpty() ? "0" : valor;
    }

    private String primeiroTexto(String primeiro, String segundo) {
        String a = compact(primeiro);
        return !a.isEmpty() ? a : compact(segundo);
    }

    private String compact(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private void registrarLog(String acao, ZamedSolicitacaoDto solicitacao, String detalhes) {
        try {
            LogDto dto = new LogDto();
            dto.setData(LocalDate.now());
            dto.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            dto.setUnidade(limitar(compact(solicitacao != null ? solicitacao.getUnidade() : ""), 20));
            dto.setEvento(limitar("ZAMED_BACKEND | " + acao
                    + " | bam=" + compact(solicitacao != null ? solicitacao.getBam() : "")
                    + " | paciente=" + compact(solicitacao != null ? solicitacao.getPacienteNome() : "")
                    + " | usuario=" + compact(solicitacao != null ? solicitacao.getUsuario() : "")
                    + " | " + compact(detalhes), 600));
            logService.salvarLogAsync(dto);
        } catch (Exception ignored) {
            // Auditoria nao deve bloquear o envio da farmacia.
        }
    }

    private String medicamentosResumo(List<ZamedItemSolicitacaoDto> itens) {
        List<String> nomes = new ArrayList<>();
        for (ZamedItemSolicitacaoDto item : itens) {
            String nome = compact(item.getMedicamentoSolicitado());
            if (!nome.isEmpty()) {
                nomes.add(limitar(nome, 80));
            }
        }
        return limitar(String.join(", ", nomes), 280);
    }

    private String limitar(String valor, int tamanho) {
        String texto = valor == null ? "" : valor.trim();
        return texto.length() <= tamanho ? texto : texto.substring(0, tamanho);
    }

    private int parseInt(String valor, int fallback) {
        try {
            String limpo = compact(valor);
            if (limpo.isEmpty()) return fallback;
            return Integer.parseInt(limpo);
        } catch (Exception e) {
            return fallback;
        }
    }
}
