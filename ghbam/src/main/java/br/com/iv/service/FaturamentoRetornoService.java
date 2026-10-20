package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import br.com.iv.dto.ImportarRetornoDto;
import br.com.iv.dto.RetornoItemImportDto;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.FaturamentoRemessa;
import br.com.iv.entity.FaturamentoRetorno;
import br.com.iv.entity.FaturamentoRetornoItem;
import br.com.iv.enums.RetornoResultado;
import br.com.iv.enums.RemessaStatus;
import br.com.iv.repository.FaturamentoRetornoItemRepository;
import br.com.iv.repository.FaturamentoRetornoRepository;
import br.com.iv.security.AuthenticatedUser;

// Importacao de retorno da Fase 5. IMPORTANTE: nao ha parser de arquivo oficial
// de retorno/processamento do DATASUS aqui (mesma restricao das outras
// importacoes desta implementacao - secao 82, nao inventar layout). Aceita uma
// lista de itens ja no formato interno; quando o item traz idafaturar, cruza
// com a producao original e marca faturado de acordo com o resultado (secao 36
// do escopo). Rejeicao/glosa fica registrada no proprio item do retorno
// (resultado + motivo + codigo_critica).
@ApplicationScoped
public class FaturamentoRetornoService {

    @Inject
    FaturamentoRetornoRepository retornoRepository;

    @Inject
    FaturamentoRetornoItemRepository itemRepository;

    @Inject
    FaturamentoAuditService auditService;

    public List<FaturamentoRetorno> listar() {
        return retornoRepository.listar();
    }

    public List<FaturamentoRetorno> listar(String unidade) {
        return retornoRepository.listar(unidade);
    }

    public List<FaturamentoRetornoItem> listarItens(int idretorno) {
        return itemRepository.listarPorRetorno(idretorno);
    }

    public List<FaturamentoRetornoItem> listarItens(int idretorno, String unidade) {
        obrigatorio(idretorno, unidade);
        return itemRepository.listarPorRetorno(idretorno);
    }

    @Transactional
    public FaturamentoRetorno importar(ImportarRetornoDto dto, AuthenticatedUser user) {
        if (dto == null || dto.getItens() == null || dto.getItens().isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um item de retorno.");
        }
        if (dto.getIdremessa() == null || dto.getIdremessa() <= 0) {
            throw new IllegalArgumentException("Vincule o retorno a uma remessa transmitida.");
        }
        String unidade = resolverUnidade(dto, user);
        // Trava a remessa ate o fim da transacao: sem isso duas importacoes simultaneas
        // passariam juntas pela checagem de "ja existe retorno" abaixo.
        FaturamentoRemessa remessa = FaturamentoRemessa.findById(dto.getIdremessa(), LockModeType.PESSIMISTIC_WRITE);
        if (remessa == null || !mesmaUnidade(remessa.getUnidade(), unidade)) {
            throw new IllegalArgumentException("Remessa nao encontrada para a unidade informada.");
        }
        exigirRemessaTransmitida(remessa);
        if (FaturamentoRetorno.count("idremessa", dto.getIdremessa()) > 0) {
            throw new IllegalArgumentException("Ja existe retorno importado para a remessa " + remessa.getNumero()
                    + ". Um segundo retorno deixaria producao glosada marcada como faturada.");
        }
        if (dto.getCompetencia() != null && !dto.getCompetencia().isBlank()
                && !dto.getCompetencia().equals(remessa.getIdcompetencia() > 0
                        ? competenciaDaRemessa(remessa) : "")) {
            throw new IllegalArgumentException("A competencia do retorno nao corresponde a remessa.");
        }

        FaturamentoRetorno retorno = new FaturamentoRetorno();
        retorno.setIdremessa(dto.getIdremessa());
        retorno.setCompetencia(competenciaDaRemessa(remessa));
        retorno.setUnidade(unidade);
        retorno.setDataImportacao(LocalDateTime.now());
        retorno.setUsuario(user == null ? "" : user.matricula());
        retorno.setObservacao(dto.getObservacao());
        retorno.setQuantidadeRegistros(dto.getItens().size());
        retorno.persist();

        int aprovados = 0;
        int rejeitados = 0;
        Set<Integer> producoesDoRetorno = new HashSet<>();
        for (RetornoItemImportDto itemDto : dto.getItens()) {
            if (itemDto == null) {
                throw new IllegalArgumentException("O retorno contem um item vazio.");
            }
            String resultado = normalizarResultado(itemDto.getResultado());
            Afaturar producao = null;
            if (itemDto.getIdafaturar() != null) {
                if (!producoesDoRetorno.add(itemDto.getIdafaturar())) {
                    throw new IllegalArgumentException("A producao " + itemDto.getIdafaturar()
                            + " esta duplicada no retorno.");
                }
                producao = Afaturar.find(
                        "idafaturar = ?1 and numeroRemessa = ?2 and idcompetencia = ?3 and upper(unidade) = upper(?4)",
                        itemDto.getIdafaturar(), remessa.getNumero(), remessa.getIdcompetencia(), unidade)
                        .firstResult();
                if (producao == null) {
                    Afaturar semVinculo = Afaturar.findById(itemDto.getIdafaturar());
                    if (semVinculo != null && texto(semVinculo.getNumeroRemessa()).isBlank()) {
                        throw new IllegalArgumentException("A producao " + itemDto.getIdafaturar()
                                + " foi exportada antes do GHBAM gravar o numero da remessa em cada linha;"
                                + " confira o retorno manualmente.");
                    }
                    throw new IllegalArgumentException("A producao " + itemDto.getIdafaturar()
                            + " nao pertence a remessa/unidade/competencia informada.");
                }
            } else if (RetornoResultado.APROVADO.name().equals(resultado)) {
                throw new IllegalArgumentException("Todo item aprovado precisa identificar a producao do GHBAM.");
            }

            FaturamentoRetornoItem item = new FaturamentoRetornoItem();
            item.setIdretorno(retorno.getIdretorno());
            item.setIdafaturar(itemDto.getIdafaturar());
            item.setCodigoExterno(itemDto.getCodigoExterno());
            item.setValorApresentado(itemDto.getValorApresentado());
            item.setValorAprovado(itemDto.getValorAprovado());
            item.setResultado(resultado);
            item.setMotivo(itemDto.getMotivo());
            item.setCodigoCritica(itemDto.getCodigoCritica());
            item.persist();

            if (RetornoResultado.APROVADO.name().equals(resultado)) {
                aprovados++;
                producao.setFaturado(true);
            } else if (RetornoResultado.REJEITADO.name().equals(resultado) || RetornoResultado.GLOSADO.name().equals(resultado)) {
                rejeitados++;
            }
        }

        retorno.setQuantidadeAprovados(aprovados);
        retorno.setQuantidadeRejeitados(rejeitados);
        remessa.setStatus(RemessaStatus.RETORNO_IMPORTADO.name());

        auditService.registrar(unidade, "IMPORTAR_RETORNO", "SUCESSO",
                "FaturamentoRetorno", String.valueOf(retorno.getIdretorno()), null, null, null,
                "registros=" + dto.getItens().size() + " aprovados=" + aprovados + " rejeitados=" + rejeitados, user);
        return retorno;
    }

    private FaturamentoRetorno obrigatorio(int idretorno, String unidade) {
        FaturamentoRetorno retorno = FaturamentoRetorno.findById(idretorno);
        if (retorno == null) {
            throw new IllegalArgumentException("Retorno nao encontrado para o id: " + idretorno);
        }
        if (unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade)
                && (retorno.getUnidade() == null || !retorno.getUnidade().equalsIgnoreCase(unidade.trim()))) {
            throw new IllegalArgumentException("Retorno nao encontrado para a unidade informada.");
        }
        return retorno;
    }

    // Retorno so faz sentido para remessa que saiu do GHBAM: GERADA/VALIDADA/
    // DISPONIBILIZADA ainda nao foram transmitidas, e CANCELADA/SUBSTITUIDA/REJEITADA
    // ja devolveram a producao - aprovar ali marcaria faturado o que nao foi pago.
    // RETORNO_IMPORTADO fica de fora: um retorno por remessa (glosa nao desfaz faturado).
    static void exigirRemessaTransmitida(FaturamentoRemessa remessa) {
        if (remessa == null || !Set.of(RemessaStatus.TRANSMITIDA.name(), RemessaStatus.PROCESSADA.name())
                .contains(remessa.getStatus())) {
            throw new IllegalArgumentException("O retorno so pode ser importado para uma remessa transmitida.");
        }
    }

    private String resolverUnidade(ImportarRetornoDto dto, AuthenticatedUser user) {
        String unidade = dto == null ? "" : texto(dto.getUnidade());
        if (unidade.isBlank() && dto != null && dto.getIdremessa() != null) {
            FaturamentoRemessa remessa = FaturamentoRemessa.findById(dto.getIdremessa());
            unidade = remessa == null ? "" : texto(remessa.getUnidade());
        }
        if (unidade.isBlank() && user != null) {
            unidade = texto(user.unidade());
        }
        if (unidade.isBlank() || "TODAS".equalsIgnoreCase(unidade)) {
            throw new IllegalArgumentException("Informe a unidade para importar o retorno SUS.");
        }
        return unidade.toUpperCase();
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String competenciaDaRemessa(FaturamentoRemessa remessa) {
        br.com.iv.entity.FaturamentoCompetencia competencia =
                br.com.iv.entity.FaturamentoCompetencia.findById(remessa.getIdcompetencia());
        if (competencia == null) {
            throw new IllegalArgumentException("A competencia da remessa nao foi encontrada.");
        }
        return competencia.getCompetencia();
    }

    private boolean mesmaUnidade(String primeira, String segunda) {
        return primeira != null && segunda != null && primeira.trim().equalsIgnoreCase(segunda.trim());
    }

    private String normalizarResultado(String valor) {
        try {
            return RetornoResultado.valueOf(String.valueOf(valor).trim().toUpperCase()).name();
        } catch (IllegalArgumentException | NullPointerException e) {
            return RetornoResultado.PENDENTE.name();
        }
    }
}
