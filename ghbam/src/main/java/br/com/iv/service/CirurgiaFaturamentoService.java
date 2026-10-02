package br.com.iv.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.AfaturarDto;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.CirurgiaAgendamento;
import br.com.iv.entity.CirurgiaEquipe;
import br.com.iv.entity.CirurgiaProcedimento;
import br.com.iv.entity.CirurgiaTempo;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.ProcedimentosSus;
import br.com.iv.entity.Servidor;
import br.com.iv.repository.ProcedimentosSusRepository;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.security.AuthenticatedUser;

/**
 * Transforma a cirurgia encerrada em producao para o faturamento SUS.
 *
 * A producao nasce no encerramento porque e ali que se sabe o que de fato foi
 * feito: so procedimento marcado como realizado vira linha. Cada linha entra
 * pelo mesmo caminho de todas as outras origens (AfaturarService.salvarFatura),
 * que classifica o instrumento e o status - nao ha regra de faturamento
 * duplicada aqui.
 *
 * O que falta no cadastro (CNS do paciente, CBO do cirurgiao) nao e inventado
 * nem impede a gravacao: a producao e criada com o campo vazio e o motor de
 * criticas aponta a pendencia na tela de faturamento, que e onde ela pode ser
 * corrigida com rastro.
 */
@ApplicationScoped
public class CirurgiaFaturamentoService {

    private static final DateTimeFormatter DATA_PRODUCAO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Origem gravada na producao, para separar o que veio do centro cirurgico. */
    private static final String ORIGEM = "CIR";

    private static final String LOCAL = "CENTRO CIRURGICO";

    @Inject
    AfaturarService afaturarService;

    @Inject
    ProcedimentosSusRepository procedimentosSusRepository;

    @Inject
    ServidorRepository servidorRepository;

    /**
     * Gera a producao dos procedimentos realizados e devolve quantas linhas criou.
     *
     * Reentrante de proposito: a chave de controle e CIR-idcirurgia-idprocedimento-sequencia,
     * entao encerrar de novo (ou reprocessar) nao duplica nada.
     *
     * REQUIRES_NEW para isolar a falha: se o insert no afaturar estourar, quem
     * chama trata o erro sem levar junto a transacao do encerramento.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int gerarProducao(CirurgiaAgendamento cirurgia, AuthenticatedUser user) {
        if (cirurgia == null) {
            return 0;
        }
        List<CirurgiaProcedimento> procedimentos = CirurgiaProcedimento
                .list("idcirurgia = ?1 and realizado = true", cirurgia.getIdcirurgia());
        if (procedimentos.isEmpty()) {
            return 0;
        }

        Pacientes paciente = Pacientes.find("idpaciente", cirurgia.getIdpaciente()).firstResult();
        CirurgiaEquipe cirurgiao = localizarCirurgiao(cirurgia.getIdcirurgia());
        Servidor servidor = localizarServidor(cirurgiao);
        String dataAto = dataDoAto(cirurgia);

        int gravadas = 0;
        for (CirurgiaProcedimento procedimento : procedimentos) {
            String codigo = texto(procedimento.getCodigo());
            if (codigo.isEmpty()) {
                continue;
            }
            // O SIGTAP cobra por quantidade, mas afaturar nao tem essa coluna:
            // cada unidade executada vira uma linha, como nas demais origens.
            int quantidade = Math.max(1, procedimento.getQuantidade());
            ProcedimentosSus sigtap = procedimentosSusRepository.filtrarPorUnicoProcedimentoSus(codigo);
            for (int sequencia = 1; sequencia <= quantidade; sequencia++) {
                String controle = chaveControle(cirurgia.getIdcirurgia(), procedimento.getIdprocedimento(), sequencia);
                if (Afaturar.count("controleid", controle) > 0) {
                    continue;
                }
                afaturarService.salvarFatura(
                        montar(cirurgia, procedimento, paciente, cirurgiao, servidor, sigtap, dataAto, controle, user),
                        user);
                gravadas++;
            }
        }
        return gravadas;
    }

    String chaveControle(int idcirurgia, Integer idprocedimento, int sequencia) {
        return ORIGEM + "-" + idcirurgia + "-" + idprocedimento + "-" + sequencia;
    }

    /**
     * Monta a linha de producao. O SIGTAP entra resolvido por parametro: a busca
     * fica no laco, uma vez por procedimento em vez de uma por unidade de
     * quantidade, e o mapeamento continua testavel sem banco.
     */
    AfaturarDto montar(CirurgiaAgendamento c, CirurgiaProcedimento p, Pacientes paciente,
            CirurgiaEquipe cirurgiao, Servidor servidor, ProcedimentosSus sigtap, String dataAto, String controle,
            AuthenticatedUser user) {

        AfaturarDto dto = new AfaturarDto();
        dto.setProcedimento(texto(p.getCodigo(), 10));
        dto.setDescricao(texto(p.getDescricao(), 200));
        dto.setControleid(controle);
        dto.setOrigeminfo(ORIGEM);
        dto.setLocal(LOCAL);
        dto.setLocalsolicitante(LOCAL);
        dto.setUnidade(texto(c.getUnidade(), 25));
        dto.setRealizado(true);

        dto.setIdpaciente(c.getIdpaciente());
        dto.setNomepaciente(texto(c.getNomepaciente(), 30));
        dto.setCnsdopciente(texto(c.getCnspaciente(), 16));
        dto.setBam(c.getIdbam() == null ? 0 : c.getIdbam());
        dto.setIdregistro(c.getIdinternacao() == null ? 0 : c.getIdinternacao());
        dto.setIdtratamento(c.getIdcirurgia() == null ? 0 : c.getIdcirurgia());

        dto.setData(dataAto);
        dto.setDtatendimento(dataAto);
        dto.setCid10(texto(c.getCid(), 4));
        dto.setCarateratend(carater(c.getCarater()));

        aplicarPaciente(dto, paciente);
        aplicarProfissional(dto, c, cirurgiao, servidor, user);
        aplicarValoresSigtap(dto, sigtap);

        return dto;
    }

    /**
     * Dados do paciente exigidos pelo BPA individualizado.
     *
     * Sao lidos do cadastro no momento do encerramento: o agendamento guarda so
     * nome, prontuario e CNS, e copiar o resto para a cirurgia criaria uma
     * segunda versao do cadastro para envelhecer sozinha.
     */
    private void aplicarPaciente(AfaturarDto dto, Pacientes paciente) {
        if (paciente == null) {
            return;
        }
        if (texto(dto.getCnsdopciente()).isEmpty()) {
            dto.setCnsdopciente(texto(paciente.getCns(), 16));
        }
        if (texto(dto.getNomepaciente()).isEmpty()) {
            dto.setNomepaciente(texto(paciente.getNome(), 30));
        }
        dto.setSexo(texto(paciente.getSexo(), 1));
        dto.setDtnascimento(texto(paciente.getNascimento(), 12));
        dto.setIdade(paciente.getIdade());
        dto.setEndereco(texto(paciente.getEndereco(), 30));
        dto.setNumerocasa(texto(paciente.getNumero(), 5));
        dto.setBairro(texto(paciente.getBairro(), 30));
        dto.setCep(somenteDigitosInt(paciente.getCep()));
        dto.setTelefone(somenteDigitosLong(paciente.getTelefone()));
    }

    /**
     * Profissional responsavel pela linha.
     *
     * O CNS vem do cadastro do servidor, nao do que foi digitado na equipe: e o
     * dado que o BPA valida. Sem cirurgiao identificado a linha ainda e gravada,
     * com a matricula de quem encerrou, e a critica aponta o que falta.
     */
    private void aplicarProfissional(AfaturarDto dto, CirurgiaAgendamento c, CirurgiaEquipe cirurgiao,
            Servidor servidor, AuthenticatedUser user) {

        dto.setNomeservidor(cirurgiao != null ? texto(cirurgiao.getNome(), 55) : texto(c.getNomecirurgiao(), 55));
        dto.setMatriculaservidor(cirurgiao != null ? texto(cirurgiao.getMatricula(), 10) : "");
        dto.setCbo(cirurgiao != null ? texto(cirurgiao.getCbo(), 55) : "");

        if (servidor != null) {
            dto.setCnsdoprofissional(texto(servidor.getCns(), 16));
            if (texto(dto.getCbo()).isEmpty()) {
                dto.setCbo(texto(servidor.getCbo(), 55));
            }
            if (texto(dto.getMatriculaservidor()).isEmpty()) {
                dto.setMatriculaservidor(texto(servidor.getMatricula(), 10));
            }
            if (texto(dto.getNomeservidor()).isEmpty()) {
                dto.setNomeservidor(texto(servidor.getNome(), 55));
            }
        }
        if (texto(dto.getMatriculaservidor()).isEmpty() && user != null) {
            dto.setMatriculaservidor(texto(user.matricula(), 10));
        }
    }

    /**
     * Valores e tipo de registro vem da tabela SIGTAP.
     *
     * Sem eles a producao seria classificada como NAO_FATURAVEL
     * (AfaturarService/classificarInstrumento trata valor zerado como nao
     * faturavel), entao um codigo fora do SIGTAP vigente aparece na tela de
     * faturamento em vez de sumir sem aviso.
     */
    private void aplicarValoresSigtap(AfaturarDto dto, ProcedimentosSus sigtap) {
        if (sigtap == null) {
            return;
        }
        dto.setPa_sa(sigtap.getPa_sa());
        dto.setPa_sp(sigtap.getPa_sp());
        dto.setPa_sh(sigtap.getPa_sh());
        dto.setPa_tpreg(texto(sigtap.getPa_tpreg()));
        dto.setPa_eletiva(texto(sigtap.getPa_eletiva(), 1));
    }

    /** Cirurgiao da equipe; sem ele nao ha responsavel para a linha do BPA. */
    private CirurgiaEquipe localizarCirurgiao(int idcirurgia) {
        CirurgiaEquipe principal = CirurgiaEquipe
                .find("idcirurgia = ?1 and upper(funcao) like ?2", idcirurgia, "CIRURGIAO%")
                .firstResult();
        return principal != null
                ? principal
                : CirurgiaEquipe.find("idcirurgia", idcirurgia).firstResult();
    }

    private Servidor localizarServidor(CirurgiaEquipe cirurgiao) {
        if (cirurgiao == null) {
            return null;
        }
        if (cirurgiao.getIdservidor() != null) {
            Servidor porId = servidorRepository.filtrarPorID(cirurgiao.getIdservidor());
            if (porId != null) {
                return porId;
            }
        }
        String matricula = texto(cirurgiao.getMatricula());
        return matricula.isEmpty() ? null : servidorRepository.localizarMatricula(matricula);
    }

    /**
     * Data do ato, nao a do agendamento.
     *
     * A competencia do BPA e definida pela data do procedimento: usar a data
     * prevista jogaria para o mes errado toda cirurgia remarcada.
     */
    private String dataDoAto(CirurgiaAgendamento c) {
        CirurgiaTempo tempo = CirurgiaTempo.find("idcirurgia", c.getIdcirurgia()).firstResult();
        LocalDateTime data = null;
        if (tempo != null) {
            data = tempo.getInicioCirurgia() != null ? tempo.getInicioCirurgia() : tempo.getEntradaSala();
        }
        if (data == null) {
            data = c.getDataPrevista();
        }
        return data == null ? "" : data.format(DATA_PRODUCAO);
    }

    /** Carater do atendimento no padrao SUS: 1 eletivo, 2 urgencia. */
    int carater(String carater) {
        return "ELETIVA".equalsIgnoreCase(texto(carater)) ? 1 : 2;
    }

    /** Producao ja gerada para a cirurgia, para conferencia na tela. */
    public List<Afaturar> producaoDaCirurgia(int idcirurgia) {
        return Afaturar.list("controleid like ?1", ORIGEM + "-" + idcirurgia + "-%");
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    /**
     * Recorta o texto no limite da coluna do afaturar.
     *
     * O cadastro de pacientes aceita campos maiores que a tabela legada de
     * faturamento (numerocasa, por exemplo, e varchar(5)). Sem o corte o insert
     * estoura e a cirurgia encerra sem gerar producao.
     */
    private String texto(String valor, int limite) {
        String limpo = texto(valor);
        return limpo.length() <= limite ? limpo : limpo.substring(0, limite).trim();
    }

    private int somenteDigitosInt(String valor) {
        String digitos = texto(valor).replaceAll("[^0-9]", "");
        try {
            return digitos.isEmpty() ? 0 : Integer.parseInt(digitos);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long somenteDigitosLong(String valor) {
        String digitos = texto(valor).replaceAll("[^0-9]", "");
        try {
            return digitos.isEmpty() ? 0L : Long.parseLong(digitos);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
