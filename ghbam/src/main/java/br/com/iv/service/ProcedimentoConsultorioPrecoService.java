package br.com.iv.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.ProcedimentoConsultorioPrecoDto;
import br.com.iv.entity.ProcedimentoConsultorioPreco;
import br.com.iv.repository.ProcedimentoConsultorioPrecoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ProcedimentoConsultorioPrecoService {

    @Inject
    ProcedimentoConsultorioPrecoRepository repository;

    public List<ProcedimentoConsultorioPreco> listarPorProcedimento(int idprocedimentoConsultorio, String unidade) {
        return repository.listarPorProcedimento(idprocedimentoConsultorio, unidade);
    }

    public Optional<ProcedimentoConsultorioPreco> buscarMelhorPreco(int idprocedimentoConsultorio, String unidade,
            String tipoCobranca, String convenio, String plano) {
        return repository.buscarMelhorPreco(idprocedimentoConsultorio, unidade, tipoCobranca, convenio, plano);
    }

    @Transactional
    public ProcedimentoConsultorioPreco salvar(ProcedimentoConsultorioPrecoDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Dados do preco ausentes");
        }
        ProcedimentoConsultorioPreco preco = new ProcedimentoConsultorioPreco();
        aplicar(preco, dto);
        preco.setCriadoEm(LocalDateTime.now());
        preco.setCriadoPor(valorOuPadrao(dto.getUsuario(), "-"));
        repository.persist(preco);
        return preco;
    }

    @Transactional
    public ProcedimentoConsultorioPreco atualizar(int id, ProcedimentoConsultorioPrecoDto dto) {
        ProcedimentoConsultorioPreco preco = buscar(id);
        aplicar(preco, dto);
        preco.setAtualizadoEm(LocalDateTime.now());
        preco.setAtualizadoPor(valorOuPadrao(dto.getUsuario(), "-"));
        return preco;
    }

    @Transactional
    public ProcedimentoConsultorioPreco alterarAtivo(int id, ProcedimentoConsultorioPrecoDto dto) {
        ProcedimentoConsultorioPreco preco = buscar(id);
        preco.setAtivo(normalizarAtivo(dto == null ? null : dto.getAtivo()));
        preco.setAtualizadoEm(LocalDateTime.now());
        preco.setAtualizadoPor(valorOuPadrao(dto == null ? null : dto.getUsuario(), "-"));
        return preco;
    }

    private ProcedimentoConsultorioPreco buscar(int id) {
        Optional<ProcedimentoConsultorioPreco> preco = ProcedimentoConsultorioPreco.findByIdOptional(id);
        if (preco.isEmpty()) {
            throw new IllegalArgumentException("Preco nao encontrado para o id: " + id);
        }
        return preco.get();
    }

    private void aplicar(ProcedimentoConsultorioPreco preco, ProcedimentoConsultorioPrecoDto dto) {
        preco.setUnidade(valorOuPadrao(dto.getUnidade(), "-").toUpperCase());
        preco.setIdprocedimentoConsultorio(inteiro(dto.getIdprocedimentoConsultorio()));
        preco.setTipoCobranca(normalizarTipoCobranca(dto.getTipoCobranca()));
        preco.setConvenio(normalizarEscopo(dto.getConvenio()));
        preco.setPlano(normalizarEscopo(dto.getPlano()));
        preco.setValor(valor(dto.getValor()));
        preco.setValorRepasse(valor(dto.getValorRepasse()));
        preco.setAtivo(normalizarAtivo(dto.getAtivo()));
        preco.setAtualizadoEm(LocalDateTime.now());
        preco.setAtualizadoPor(valorOuPadrao(dto.getUsuario(), "-"));
    }

    private int inteiro(Integer valor) {
        return valor == null ? 0 : valor;
    }

    private BigDecimal valor(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private String valorOuPadrao(String valor, String padrao) {
        return valor == null || valor.trim().isEmpty() ? padrao : valor.trim();
    }

    private String normalizarEscopo(String valor) {
        String escopo = valorOuPadrao(valor, "TODOS").toUpperCase();
        return escopo.equals("-") ? "TODOS" : escopo;
    }

    private String normalizarAtivo(String valor) {
        return "N".equalsIgnoreCase(valorOuPadrao(valor, "S")) ? "N" : "S";
    }

    private String normalizarTipoCobranca(String valor) {
        String tipo = valorOuPadrao(valor, "PARTICULAR").toUpperCase();
        if (tipo.equals("CONVENIO") || tipo.equals("COPARTICIPACAO") || tipo.equals("CORTESIA")) {
            return tipo;
        }
        return "PARTICULAR";
    }
}
