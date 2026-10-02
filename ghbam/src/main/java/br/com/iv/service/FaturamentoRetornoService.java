package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.ImportarRetornoDto;
import br.com.iv.dto.RetornoItemImportDto;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.FaturamentoRemessa;
import br.com.iv.entity.FaturamentoRetorno;
import br.com.iv.entity.FaturamentoRetornoItem;
import br.com.iv.enums.RetornoResultado;
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
        String unidade = resolverUnidade(dto, user);

        FaturamentoRetorno retorno = new FaturamentoRetorno();
        retorno.setIdremessa(dto.getIdremessa());
        retorno.setCompetencia(dto.getCompetencia());
        retorno.setUnidade(unidade);
        retorno.setDataImportacao(LocalDateTime.now());
        retorno.setUsuario(user == null ? "" : user.matricula());
        retorno.setObservacao(dto.getObservacao());
        retorno.setQuantidadeRegistros(dto.getItens().size());
        retorno.persist();

        int aprovados = 0;
        int rejeitados = 0;
        for (RetornoItemImportDto itemDto : dto.getItens()) {
            String resultado = normalizarResultado(itemDto.getResultado());

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
                marcarFaturado(itemDto.getIdafaturar());
            } else if (RetornoResultado.REJEITADO.name().equals(resultado) || RetornoResultado.GLOSADO.name().equals(resultado)) {
                rejeitados++;
            }
        }

        retorno.setQuantidadeAprovados(aprovados);
        retorno.setQuantidadeRejeitados(rejeitados);

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

    private void marcarFaturado(Integer idafaturar) {
        if (idafaturar == null) {
            return;
        }
        Afaturar producao = Afaturar.findById(idafaturar);
        if (producao != null) {
            producao.setFaturado(true);
        }
    }

    private String normalizarResultado(String valor) {
        try {
            return RetornoResultado.valueOf(String.valueOf(valor).trim().toUpperCase()).name();
        } catch (IllegalArgumentException | NullPointerException e) {
            return RetornoResultado.PENDENTE.name();
        }
    }
}
