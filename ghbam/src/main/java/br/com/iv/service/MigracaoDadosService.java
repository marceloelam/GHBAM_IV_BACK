package br.com.iv.service;

import br.com.iv.dto.MigracaoDadosPlanoDto;
import br.com.iv.dto.MigracaoDadosExportacaoDto;
import br.com.iv.dto.MigracaoDadosBancoExternoDto;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class MigracaoDadosService {

    private static final int LIMITE_PREVIEW_STAGING = 25;
    private static final int LIMITE_VALIDACOES = 100;
    private static final List<String> DOMINIOS_PERMITIDOS = List.of(
            "pacientes", "atendimentos", "prontuarios", "prescricoes", "exames", "profissionais", "unidades"
    );

    public Map<String, Object> analisarPlano(MigracaoDadosPlanoDto plano, AuthenticatedUser usuario) {
        if (plano == null) {
            throw new IllegalArgumentException("Plano de migracao ausente");
        }
        if (plano.mapeamento == null || plano.mapeamento.isEmpty()) {
            throw new IllegalArgumentException("Mapeamento de campos ausente");
        }

        List<String> pendencias = new ArrayList<>();
        validarCampoObrigatorio(plano.mapeamento, "paciente.codigoOrigem", pendencias);
        validarCampoObrigatorio(plano.mapeamento, "paciente.nome", pendencias);
        validarCampoObrigatorio(plano.mapeamento, "atendimento.codigoOrigem", pendencias);

        int totalColunas = plano.colunasOrigem == null ? 0 : plano.colunasOrigem.size();
        int totalMapeados = (int) plano.mapeamento.values().stream()
                .filter(valor -> valor != null && !valor.isBlank())
                .count();
        int totalValidacoes = plano.validacoes == null ? 0 : plano.validacoes.size();
        int totalPreview = plano.previewStaging == null ? 0 : plano.previewStaging.size();

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("status", pendencias.isEmpty() && totalValidacoes == 0 ? "PRONTO_PARA_STAGING" : "PENDENTE_REVISAO");
        resposta.put("arquivoOrigem", texto(plano.arquivoOrigem));
        resposta.put("recebidoEm", Instant.now().toString());
        resposta.put("usuario", usuario == null ? "" : texto(usuario.nome()));
        resposta.put("unidade", usuario == null ? "" : texto(usuario.unidade()));
        resposta.put("estrategia", "staging-validacao-transformacao-importacao");
        resposta.put("totalColunasOrigem", totalColunas);
        resposta.put("totalCamposMapeados", totalMapeados);
        resposta.put("totalValidacoes", totalValidacoes);
        resposta.put("totalPreviewStaging", totalPreview);
        resposta.put("pendencias", pendencias);
        resposta.put("validacoes", limitar(plano.validacoes, LIMITE_VALIDACOES));
        resposta.put("previewStaging", limitar(plano.previewStaging, LIMITE_PREVIEW_STAGING));
        resposta.put("proximasEtapas", List.of(
                "Persistir o arquivo original em storage controlado",
                "Criar lote de staging com id externo e metadados de origem",
                "Executar importacao em blocos pequenos com logs e rollback por lote",
                "Aplicar no modelo oficial somente apos conferencia do lote de staging"
        ));
        return resposta;
    }

    public Map<String, Object> prepararExportacao(MigracaoDadosExportacaoDto pedido, AuthenticatedUser usuario) {
        if (pedido == null) {
            throw new IllegalArgumentException("Pedido de exportacao ausente");
        }
        String formato = texto(pedido.formato).isBlank() ? "JSON" : texto(pedido.formato).toUpperCase();
        if (!List.of("JSON", "CSV").contains(formato)) {
            throw new IllegalArgumentException("Formato de exportacao invalido. Use JSON ou CSV");
        }

        List<String> dominios = pedido.dominios == null || pedido.dominios.isEmpty()
                ? List.of("pacientes", "atendimentos")
                : pedido.dominios.stream().filter(DOMINIOS_PERMITIDOS::contains).distinct().toList();
        if (dominios.isEmpty()) {
            throw new IllegalArgumentException("Nenhum dominio de exportacao valido informado");
        }

        boolean anonimizar = Boolean.TRUE.equals(pedido.anonimizar);
        boolean incluirClinico = Boolean.TRUE.equals(pedido.incluirDadosClinicos);
        List<String> alertas = new ArrayList<>();
        if (incluirClinico && !anonimizar) {
            alertas.add("Exportacao contem dados clinicos identificados; exigir autorizacao formal e registro LGPD.");
        }
        if (texto(pedido.dataInicio).isBlank() || texto(pedido.dataFim).isBlank()) {
            alertas.add("Periodo nao informado; recomenda-se exportar por janela para evitar lote grande demais.");
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("status", alertas.isEmpty() ? "PRONTO_PARA_GERAR_ARQUIVO" : "PENDENTE_CONFIRMACAO");
        resposta.put("tipo", "EXPORTACAO_GHBAM");
        resposta.put("sistemaDestino", texto(pedido.sistemaDestino));
        resposta.put("formato", formato);
        resposta.put("unidade", texto(pedido.unidade).isBlank() && usuario != null ? texto(usuario.unidade()) : texto(pedido.unidade));
        resposta.put("dataInicio", texto(pedido.dataInicio));
        resposta.put("dataFim", texto(pedido.dataFim));
        resposta.put("anonimizar", anonimizar);
        resposta.put("incluirDadosClinicos", incluirClinico);
        resposta.put("dominios", dominios);
        resposta.put("mapeamentoDestino", pedido.mapeamentoDestino == null ? Map.of() : pedido.mapeamentoDestino);
        resposta.put("alertas", alertas);
        resposta.put("preparadoEm", Instant.now().toString());
        resposta.put("usuario", usuario == null ? "" : texto(usuario.nome()));
        resposta.put("proximasEtapas", List.of(
                "Gerar arquivo por dominio em lotes paginados",
                "Registrar trilha LGPD da exportacao",
                "Entregar arquivo acompanhado do dicionario de campos",
                "Validar importacao no sistema destino antes de apagar ou encerrar qualquer base"
        ));
        return resposta;
    }

    public Map<String, Object> prepararBancoExterno(MigracaoDadosBancoExternoDto pedido, AuthenticatedUser usuario) {
        if (pedido == null) {
            throw new IllegalArgumentException("Dados de acesso ao banco externo ausentes");
        }
        String motor = texto(pedido.motor).toUpperCase();
        if (!List.of("POSTGRESQL", "MYSQL", "SQLSERVER", "ORACLE", "FIREBIRD").contains(motor)) {
            throw new IllegalArgumentException("Motor de banco externo invalido");
        }
        if (texto(pedido.host).isBlank() || texto(pedido.banco).isBlank() || texto(pedido.usuario).isBlank()) {
            throw new IllegalArgumentException("Host, banco e usuario sao obrigatorios");
        }
        if (!Boolean.TRUE.equals(pedido.somenteLeitura)) {
            throw new IllegalArgumentException("Acesso ao banco externo deve ser somente leitura");
        }

        int limiteAmostra = pedido.limiteAmostra == null || pedido.limiteAmostra < 1 ? 100 : Math.min(pedido.limiteAmostra, 1000);
        List<String> tabelas = pedido.tabelasPrioritarias == null ? List.of() : pedido.tabelasPrioritarias.stream()
                .map(this::texto)
                .filter(valor -> !valor.isBlank())
                .distinct()
                .limit(30)
                .toList();
        List<String> alertas = new ArrayList<>();
        if (texto(pedido.secretRef).isBlank()) {
            alertas.add("Senha/secret nao deve vir no payload; configure secretRef ou variavel de ambiente no servidor.");
        }
        if (tabelas.isEmpty()) {
            alertas.add("Sem tabelas prioritarias; a primeira execucao deve inventariar metadados antes de amostrar dados.");
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("status", alertas.isEmpty() ? "PRONTO_PARA_INVENTARIO" : "PENDENTE_CONFIGURACAO");
        resposta.put("tipo", "IMPORTACAO_BANCO_EXTERNO");
        resposta.put("sistemaOrigem", texto(pedido.sistemaOrigem));
        resposta.put("motor", motor);
        resposta.put("host", mascararHost(texto(pedido.host)));
        resposta.put("porta", pedido.porta);
        resposta.put("banco", texto(pedido.banco));
        resposta.put("schema", texto(pedido.schema));
        resposta.put("usuario", texto(pedido.usuario));
        resposta.put("secretRefConfigurado", !texto(pedido.secretRef).isBlank());
        resposta.put("somenteLeitura", true);
        resposta.put("limiteAmostra", limiteAmostra);
        resposta.put("tabelasPrioritarias", tabelas);
        resposta.put("alertas", alertas);
        resposta.put("preparadoEm", Instant.now().toString());
        resposta.put("usuarioGhbam", usuario == null ? "" : texto(usuario.nome()));
        resposta.put("proximasEtapas", List.of(
                "Testar conexao read-only sem registrar senha em log",
                "Inventariar schemas, tabelas, colunas, chaves e contagens aproximadas",
                "Amostrar poucos registros por tabela prioritaria",
                "Gerar mapeamento para campos canonicos do GHBAM",
                "Importar para staging e validar duplicidades, datas, CPF/CNS e relacionamentos",
                "Liberar gravacao no modelo oficial somente por lote aprovado"
        ));
        return resposta;
    }

    private void validarCampoObrigatorio(Map<String, String> mapeamento, String campo, List<String> pendencias) {
        String valor = mapeamento.get(campo);
        if (valor == null || valor.isBlank()) {
            pendencias.add("Campo obrigatorio sem mapeamento: " + campo);
        }
    }

    private List<Map<String, Object>> limitar(List<Map<String, Object>> origem, int limite) {
        if (origem == null || origem.isEmpty()) {
            return List.of();
        }
        return origem.size() <= limite ? origem : origem.subList(0, limite);
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String mascararHost(String host) {
        if (host.isBlank() || host.length() < 5) {
            return host;
        }
        return host.substring(0, Math.min(4, host.length())) + "***";
    }
}
