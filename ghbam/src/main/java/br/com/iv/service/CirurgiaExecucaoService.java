package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.entity.CirurgiaAgendamento;
import br.com.iv.entity.CirurgiaChecklist;
import br.com.iv.entity.CirurgiaContagem;
import br.com.iv.entity.CirurgiaDescricao;
import br.com.iv.entity.CirurgiaListaEspera;
import br.com.iv.entity.CirurgiaOpme;
import br.com.iv.entity.CirurgiaProcedimento;
import br.com.iv.entity.CirurgiaTempo;
import br.com.iv.enums.CirurgiaStatus;
import br.com.iv.enums.MomentoChecklistCirurgico;
import br.com.iv.repository.CirurgiaAgendamentoRepository;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class CirurgiaExecucaoService {

    @Inject
    CirurgiaAgendamentoRepository agendamentoRepository;

    @Inject
    CirurgiaFaturamentoService cirurgiaFaturamentoService;

    @Inject
    CentroCirurgicoService centroCirurgicoService;

    @Inject
    FluxoCirurgicoAssistencial fluxoCirurgicoAssistencial;

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private CirurgiaAgendamento buscar(int idcirurgia) {
        CirurgiaAgendamento c = agendamentoRepository.findById((long) idcirurgia);
        if (c == null) {
            throw new IllegalArgumentException("Cirurgia nao encontrada.");
        }
        return c;
    }

    private CirurgiaTempo tempos(int idcirurgia) {
        CirurgiaTempo t = CirurgiaTempo.find("idcirurgia", idcirurgia).firstResult();
        if (t == null) {
            t = new CirurgiaTempo();
            t.setIdcirurgia(idcirurgia);
            t.persist();
        }
        return t;
    }

    private boolean checklistConforme(int idcirurgia, MomentoChecklistCirurgico momento) {
        CirurgiaChecklist ck = CirurgiaChecklist
                .find("idcirurgia = ?1 and momento = ?2", idcirurgia, momento.name()).firstResult();
        return ck != null && ck.isConforme();
    }

    @Transactional
    public CirurgiaChecklist salvarChecklist(int idcirurgia, String momento, String respostasJson, boolean conforme,
            String pendencias, AuthenticatedUser user) {
        buscar(idcirurgia);
        MomentoChecklistCirurgico m;
        try {
            m = MomentoChecklistCirurgico.valueOf(texto(momento).toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Momento do checklist invalido. Use SIGN_IN, TIME_OUT ou SIGN_OUT.");
        }
        if (m == MomentoChecklistCirurgico.TIME_OUT && !checklistConforme(idcirurgia, MomentoChecklistCirurgico.SIGN_IN)) {
            throw new IllegalArgumentException("Registre o Sign In conforme antes do Time Out.");
        }
        if (m == MomentoChecklistCirurgico.SIGN_OUT && !checklistConforme(idcirurgia, MomentoChecklistCirurgico.TIME_OUT)) {
            throw new IllegalArgumentException("Registre o Time Out conforme antes do Sign Out.");
        }
        CirurgiaChecklist ck = CirurgiaChecklist
                .find("idcirurgia = ?1 and momento = ?2", idcirurgia, m.name()).firstResult();
        if (ck == null) {
            ck = new CirurgiaChecklist();
            ck.setIdcirurgia(idcirurgia);
            ck.setMomento(m.name());
        }
        ck.setRespostasJson(respostasJson);
        ck.setConforme(conforme);
        ck.setPendencias(texto(pendencias));
        ck.setResponsavel(user == null ? null : user.nome());
        ck.setUsuarioRegistro(user == null ? null : user.matricula());
        ck.setRegistradoEm(LocalDateTime.now());
        ck.persist();
        return ck;
    }

    @Transactional
    public CirurgiaTempo registrarMarco(int idcirurgia, String marco, AuthenticatedUser user) {
        CirurgiaAgendamento c = buscar(idcirurgia);
        if (CirurgiaStatus.SUSPENSA.name().equals(c.getStatus())
                || CirurgiaStatus.CANCELADA.name().equals(c.getStatus())) {
            throw new IllegalArgumentException("Cirurgia suspensa ou cancelada nao aceita registro de tempo.");
        }
        CirurgiaTempo t = tempos(idcirurgia);
        LocalDateTime agora = LocalDateTime.now();
        String chave = texto(marco).toUpperCase();

        switch (chave) {
            case "ENTRADA_SALA" -> {
                if (!checklistConforme(idcirurgia, MomentoChecklistCirurgico.SIGN_IN)) {
                    throw new IllegalArgumentException("Sign In do checklist de cirurgia segura e obrigatorio antes da entrada em sala.");
                }
                t.setEntradaSala(agora);
                c.setStatus(CirurgiaStatus.PACIENTE_EM_SALA.name());
            }
            case "INICIO_ANESTESIA" -> {
                if (t.getEntradaSala() == null) {
                    throw new IllegalArgumentException("Registre a entrada em sala antes do inicio da anestesia.");
                }
                t.setInicioAnestesia(agora);
            }
            case "FIM_ANESTESIA" -> {
                if (t.getInicioAnestesia() == null) {
                    throw new IllegalArgumentException("Registre o inicio da anestesia antes do fim.");
                }
                t.setFimAnestesia(agora);
            }
            case "INICIO_CIRURGIA" -> {
                if (!checklistConforme(idcirurgia, MomentoChecklistCirurgico.TIME_OUT)) {
                    throw new IllegalArgumentException("Time Out do checklist de cirurgia segura e obrigatorio antes da incisao.");
                }
                if (t.getEntradaSala() == null) {
                    throw new IllegalArgumentException("Registre a entrada em sala antes do inicio da cirurgia.");
                }
                fluxoCirurgicoAssistencial.garantirLiberacoesParaInicio(c);
                t.setInicioCirurgia(agora);
                c.setStatus(CirurgiaStatus.EM_ANDAMENTO.name());
            }
            case "FIM_CIRURGIA" -> {
                if (t.getInicioCirurgia() == null) {
                    throw new IllegalArgumentException("Registre o inicio da cirurgia antes do fim.");
                }
                t.setFimCirurgia(agora);
            }
            case "SAIDA_SALA" -> {
                if (t.getFimCirurgia() == null) {
                    throw new IllegalArgumentException("Registre o fim da cirurgia antes da saida de sala.");
                }
                t.setSaidaSala(agora);
            }
            case "INICIO_LIMPEZA" -> t.setInicioLimpeza(agora);
            case "FIM_LIMPEZA" -> {
                if (t.getInicioLimpeza() == null) {
                    throw new IllegalArgumentException("Registre o inicio da limpeza antes do fim.");
                }
                t.setFimLimpeza(agora);
            }
            default -> throw new IllegalArgumentException("Marco de tempo invalido: " + marco);
        }
        t.setAtualizadoEm(agora);
        t.setUsuarioRegistro(user == null ? null : user.matricula());
        c.setAtualizadoEm(agora);
        return t;
    }

    @Transactional
    public CirurgiaContagem registrarContagem(int idcirurgia, CirurgiaContagem dados, AuthenticatedUser user) {
        buscar(idcirurgia);
        if (dados == null || texto(dados.getTipo()).isBlank() || texto(dados.getMomento()).isBlank()) {
            throw new IllegalArgumentException("Tipo e momento da contagem sao obrigatorios.");
        }
        CirurgiaContagem c = new CirurgiaContagem();
        c.setIdcirurgia(idcirurgia);
        c.setTipo(texto(dados.getTipo()).toUpperCase());
        c.setMomento(texto(dados.getMomento()).toUpperCase());
        c.setQuantidade(Math.max(dados.getQuantidade(), 0));
        c.setConferente(texto(dados.getConferente()).isBlank() && user != null ? user.nome() : texto(dados.getConferente()));
        c.setSegundoConferente(texto(dados.getSegundoConferente()));
        c.setDivergencia(texto(dados.getDivergencia()));
        c.setConforme(dados.isConforme());
        if (!c.isConforme() && c.getDivergencia().isBlank()) {
            throw new IllegalArgumentException("Descreva a divergencia quando a contagem nao estiver conforme.");
        }
        c.setUsuarioRegistro(user == null ? null : user.matricula());
        c.setRegistradoEm(LocalDateTime.now());
        c.persist();
        return c;
    }

    @Transactional
    public CirurgiaOpme registrarOpme(int idcirurgia, CirurgiaOpme dados, AuthenticatedUser user) {
        buscar(idcirurgia);
        if (dados == null || texto(dados.getDescricao()).isBlank()) {
            throw new IllegalArgumentException("Descricao do material OPME e obrigatoria.");
        }
        CirurgiaOpme o = new CirurgiaOpme();
        o.setIdcirurgia(idcirurgia);
        o.setCodigo(texto(dados.getCodigo()));
        o.setDescricao(texto(dados.getDescricao()));
        o.setFabricante(texto(dados.getFabricante()));
        o.setFornecedor(texto(dados.getFornecedor()));
        o.setLote(texto(dados.getLote()));
        o.setRegistroAnvisa(texto(dados.getRegistroAnvisa()));
        o.setValidade(texto(dados.getValidade()));
        o.setQuantidade(dados.getQuantidade() < 1 ? 1 : dados.getQuantidade());
        o.setValorUnitario(dados.getValorUnitario());
        o.setUtilizado(dados.isUtilizado());
        o.setDevolvido(dados.isDevolvido());
        if (o.isUtilizado() && o.getLote().isBlank()) {
            throw new IllegalArgumentException("Informe o lote do material OPME utilizado (rastreabilidade obrigatoria).");
        }
        o.setUsuarioRegistro(user == null ? null : user.matricula());
        o.persist();
        return o;
    }

    @Transactional
    public void removerOpme(int idcirurgia, int idopme) {
        CirurgiaOpme o = CirurgiaOpme.findById((long) idopme);
        if (o == null || o.getIdcirurgia() != idcirurgia) {
            throw new IllegalArgumentException("Material OPME nao encontrado nesta cirurgia.");
        }
        if (o.isUtilizado()) {
            throw new IllegalArgumentException("Material ja registrado como utilizado nao pode ser removido.");
        }
        o.delete();
    }

    public Map<String, Object> pendenciasParaEncerrar(int idcirurgia) {
        CirurgiaTempo t = CirurgiaTempo.find("idcirurgia", idcirurgia).firstResult();
        List<String> pendencias = new java.util.ArrayList<>();
        if (t == null || t.getFimCirurgia() == null) {
            pendencias.add("Registrar o fim da cirurgia.");
        }
        if (t == null || t.getSaidaSala() == null) {
            pendencias.add("Registrar a saida de sala.");
        }
        if (!checklistConforme(idcirurgia, MomentoChecklistCirurgico.SIGN_OUT)) {
            pendencias.add("Registrar o Sign Out do checklist de cirurgia segura.");
        }
        CirurgiaContagem contagemFinal = CirurgiaContagem
                .find("idcirurgia = ?1 and momento = ?2", idcirurgia, "FINAL").firstResult();
        if (contagemFinal == null) {
            pendencias.add("Registrar a contagem final de compressas e instrumentais.");
        } else if (!contagemFinal.isConforme()) {
            pendencias.add("Resolver a divergencia da contagem final antes de encerrar.");
        }
        if (CirurgiaProcedimento.count("idcirurgia = ?1 and realizado = true", idcirurgia) == 0) {
            pendencias.add("Confirmar quais procedimentos foram realizados.");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pode", pendencias.isEmpty());
        m.put("pendencias", pendencias);
        return m;
    }

    /**
     * Confirma quais procedimentos foram de fato realizados.
     *
     * Separado do agendamento de proposito: o que entra no faturamento e o ato
     * executado, nao o planejado. Procedimento cancelado no intraoperatorio fica
     * registrado na cirurgia, mas nao vira producao.
     */
    @Transactional
    public List<CirurgiaProcedimento> confirmarRealizados(int idcirurgia, Map<String, Boolean> realizados,
            AuthenticatedUser user) {

        buscar(idcirurgia);
        List<CirurgiaProcedimento> procedimentos = CirurgiaProcedimento.list("idcirurgia", idcirurgia);
        if (realizados == null || realizados.isEmpty()) {
            return procedimentos;
        }
        for (CirurgiaProcedimento p : procedimentos) {
            Boolean valor = realizados.get(String.valueOf(p.getIdprocedimento()));
            if (valor != null) {
                p.setRealizado(Boolean.TRUE.equals(valor));
            }
        }
        return procedimentos;
    }

    /**
     * Encerra a cirurgia e gera a producao dos procedimentos realizados.
     *
     * Devolve um mapa em vez da entidade porque o encerramento passou a ter dois
     * resultados que a tela precisa mostrar: o registro assistencial e quantas
     * linhas foram para o faturamento.
     *
     * Falha ao gerar a producao nao desfaz o encerramento: o registro do que foi
     * feito no paciente e assistencial e nao pode ficar refem do faturamento,
     * que pode ser refeito depois pela mesma chave de controle.
     */
    @Transactional
    public Map<String, Object> encerrar(int idcirurgia, AuthenticatedUser user) {
        CirurgiaAgendamento c = encerrarRegistro(idcirurgia, user);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("cirurgia", c);
        try {
            int linhas = cirurgiaFaturamentoService.gerarProducao(c, user);
            resultado.put("producaoGerada", linhas);
            if (linhas == 0) {
                resultado.put("avisoFaturamento",
                        "Nenhum procedimento marcado como realizado: nada foi enviado ao faturamento.");
            }
        } catch (RuntimeException e) {
            resultado.put("producaoGerada", 0);
            resultado.put("avisoFaturamento",
                    "Cirurgia encerrada, mas a producao nao foi gerada: " + e.getMessage());
        }
        return resultado;
    }

    private void registrarLogEncerramento(CirurgiaAgendamento c, AuthenticatedUser user) {
        centroCirurgicoService.registrarLog(
                "CIRURGIA ENCERRADA - " + c.getNumero() + " (" + c.getNomepaciente() + ")",
                c.getUnidade(), user);
    }

    /**
     * Regera a producao de uma cirurgia ja encerrada.
     *
     * Existe porque o encerramento nao desfaz nada quando o faturamento falha: a
     * cirurgia fica ENCERRADA sem linha no afaturar e, sem este caminho, nao havia
     * como recuperar. gerarProducao e reentrante pela chave de controle, entao
     * chamar de novo nao duplica o que ja foi gerado.
     */
    public Map<String, Object> reprocessarProducao(int idcirurgia, AuthenticatedUser user) {
        CirurgiaAgendamento c = buscar(idcirurgia);
        if (!CirurgiaStatus.ENCERRADA.name().equals(c.getStatus())) {
            throw new IllegalArgumentException("So e possivel reprocessar a producao de cirurgia encerrada.");
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("cirurgia", c);
        try {
            int linhas = cirurgiaFaturamentoService.gerarProducao(c, user);
            resultado.put("producaoGerada", linhas);
            if (linhas == 0) {
                resultado.put("avisoFaturamento",
                        "Nenhum procedimento marcado como realizado: nada foi enviado ao faturamento.");
            }
        } catch (RuntimeException e) {
            resultado.put("producaoGerada", 0);
            resultado.put("avisoFaturamento", "A producao nao foi gerada: " + e.getMessage());
        }
        return resultado;
    }

    private CirurgiaAgendamento encerrarRegistro(int idcirurgia, AuthenticatedUser user) {
        CirurgiaAgendamento c = buscar(idcirurgia);
        if (CirurgiaStatus.ENCERRADA.name().equals(c.getStatus())) {
            throw new IllegalArgumentException("Cirurgia ja encerrada.");
        }
        Map<String, Object> validacao = pendenciasParaEncerrar(idcirurgia);
        if (!Boolean.TRUE.equals(validacao.get("pode"))) {
            @SuppressWarnings("unchecked")
            List<String> pendencias = (List<String>) validacao.get("pendencias");
            throw new IllegalArgumentException("Nao e possivel encerrar: " + String.join(" ", pendencias));
        }
        c.setStatus(CirurgiaStatus.ENCERRADA.name());
        c.setAtualizadoEm(LocalDateTime.now());
        c.setUsuarioAtualizacao(user == null ? null : user.matricula());

        CirurgiaDescricao descricao = CirurgiaDescricao.find("idcirurgia", idcirurgia).firstResult();
        if (descricao == null) {
            CirurgiaDescricao nova = new CirurgiaDescricao();
            nova.setIdcirurgia(idcirurgia);
            nova.persist();
        }

        // Fecha o ciclo da fila: quem entrou na lista de espera sai dela como
        // realizado, sem depender de alguem lembrar de atualizar as duas telas.
        CirurgiaListaEspera espera = CirurgiaListaEspera.find("idcirurgia", idcirurgia).firstResult();
        if (espera != null && !"REALIZADA".equals(espera.getStatus())) {
            espera.setStatus("REALIZADA");
            espera.setAtualizadoEm(LocalDateTime.now());
            centroCirurgicoService.registrarLog(
                    "LISTA DE ESPERA CIRURGICA REALIZADA - item " + espera.getIdespera() + " ("
                            + espera.getNomepaciente() + ") pela cirurgia " + c.getNumero(),
                    c.getUnidade(), user);
        }
        registrarLogEncerramento(c, user);
        return c;
    }
}
