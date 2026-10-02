package br.com.iv.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.ProcedimentoConsultorioDto;
import br.com.iv.entity.ProcedimentoConsultorio;
import br.com.iv.entity.ProcedimentoConsultorioPreco;
import br.com.iv.repository.ProcedimentoConsultorioPrecoRepository;
import br.com.iv.repository.ProcedimentoConsultorioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ProcedimentoConsultorioService {

    @Inject
    ProcedimentoConsultorioRepository repository;

    @Inject
    ProcedimentoConsultorioPrecoRepository precoRepository;

    public List<ProcedimentoConsultorio> listarPorUnidade(String unidade, boolean apenasAtivos) {
        return repository.listarPorUnidade(unidade, apenasAtivos);
    }

    @Transactional
    public List<ProcedimentoConsultorio> popularCatalogoPadrao(String unidade, String usuario) {
        String unidadeNormalizada = valorOuPadrao(unidade, "-").toUpperCase();
        String usuarioNormalizado = valorOuPadrao(usuario, "CARGA_PADRAO");
        List<ProcedimentoConsultorio> criados = new ArrayList<>();
        for (ServicoPadrao item : catalogoPadrao()) {
            Optional<ProcedimentoConsultorio> existente = repository.buscarPorCodigoNaUnidade(unidadeNormalizada, item.codigo());
            if (existente.isPresent()) {
                criarPrecosPadrao(existente.get(), item, usuarioNormalizado);
                continue;
            }
            ProcedimentoConsultorio procedimento = new ProcedimentoConsultorio();
            procedimento.setUnidade(unidadeNormalizada);
            procedimento.setCodigo(item.codigo());
            procedimento.setNome(item.nome());
            procedimento.setEspecialidade(item.especialidade());
            procedimento.setTipoCobranca(item.tipoCobranca());
            procedimento.setValorPadrao(item.valor());
            procedimento.setValorRepassePadrao(item.repasse());
            procedimento.setAtivo("S");
            procedimento.setCriadoEm(LocalDateTime.now());
            procedimento.setCriadoPor(usuarioNormalizado);
            procedimento.setAtualizadoEm(LocalDateTime.now());
            procedimento.setAtualizadoPor(usuarioNormalizado);
            repository.persistAndFlush(procedimento);
            criarPrecosPadrao(procedimento, item, usuarioNormalizado);
            criados.add(procedimento);
        }
        return criados;
    }

    @Transactional
    public ProcedimentoConsultorio salvar(ProcedimentoConsultorioDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Dados do procedimento ausentes");
        }
        ProcedimentoConsultorio procedimento = new ProcedimentoConsultorio();
        aplicar(procedimento, dto);
        procedimento.setCriadoEm(LocalDateTime.now());
        procedimento.setCriadoPor(valorOuPadrao(dto.getUsuario(), "-"));
        repository.persist(procedimento);
        return procedimento;
    }

    @Transactional
    public ProcedimentoConsultorio atualizar(int id, ProcedimentoConsultorioDto dto) {
        ProcedimentoConsultorio procedimento = buscar(id);
        aplicar(procedimento, dto);
        procedimento.setAtualizadoEm(LocalDateTime.now());
        procedimento.setAtualizadoPor(valorOuPadrao(dto.getUsuario(), "-"));
        return procedimento;
    }

    @Transactional
    public ProcedimentoConsultorio alterarAtivo(int id, ProcedimentoConsultorioDto dto) {
        ProcedimentoConsultorio procedimento = buscar(id);
        procedimento.setAtivo(normalizarAtivo(dto == null ? null : dto.getAtivo()));
        procedimento.setAtualizadoEm(LocalDateTime.now());
        procedimento.setAtualizadoPor(valorOuPadrao(dto == null ? null : dto.getUsuario(), "-"));
        return procedimento;
    }

    private ProcedimentoConsultorio buscar(int id) {
        Optional<ProcedimentoConsultorio> procedimento = ProcedimentoConsultorio.findByIdOptional(id);
        if (procedimento.isEmpty()) {
            throw new IllegalArgumentException("Procedimento nao encontrado para o id: " + id);
        }
        return procedimento.get();
    }

    private void aplicar(ProcedimentoConsultorio procedimento, ProcedimentoConsultorioDto dto) {
        procedimento.setUnidade(valorOuPadrao(dto.getUnidade(), "-").toUpperCase());
        procedimento.setCodigo(valorOuPadrao(dto.getCodigo(), "-").toUpperCase());
        procedimento.setNome(valorOuPadrao(dto.getNome(), "-").toUpperCase());
        procedimento.setEspecialidade(valorOuPadrao(dto.getEspecialidade(), "-").toUpperCase());
        procedimento.setTipoCobranca(normalizarTipoCobranca(dto.getTipoCobranca()));
        procedimento.setValorPadrao(valor(dto.getValorPadrao()));
        procedimento.setValorRepassePadrao(valor(dto.getValorRepassePadrao()));
        procedimento.setAtivo(normalizarAtivo(dto.getAtivo()));
        procedimento.setAtualizadoEm(LocalDateTime.now());
        procedimento.setAtualizadoPor(valorOuPadrao(dto.getUsuario(), "-"));
    }

    private BigDecimal valor(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private String valorOuPadrao(String valor, String padrao) {
        return valor == null || valor.trim().isEmpty() ? padrao : valor.trim();
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

    private void criarPrecosPadrao(ProcedimentoConsultorio procedimento, ServicoPadrao item, String usuario) {
        criarPrecoSeAusente(procedimento, "PARTICULAR", "TODOS", "TODOS", item.valor(), item.repasse(), usuario);
        criarPrecoSeAusente(procedimento, "CONVENIO", "TODOS", "TODOS", percentual(item.valor(), "0.80"), percentual(item.repasse(), "0.84"), usuario);
        criarPrecoSeAusente(procedimento, "CONVENIO", "UNIMED", "BASICO", percentual(item.valor(), "0.82"), percentual(item.repasse(), "0.86"), usuario);
        criarPrecoSeAusente(procedimento, "CONVENIO", "UNIMED", "EXECUTIVO", percentual(item.valor(), "0.90"), percentual(item.repasse(), "0.92"), usuario);
        criarPrecoSeAusente(procedimento, "CONVENIO", "AMIL", "S380", percentual(item.valor(), "0.78"), percentual(item.repasse(), "0.82"), usuario);
        criarPrecoSeAusente(procedimento, "CONVENIO", "BRADESCO SAUDE", "NACIONAL", percentual(item.valor(), "0.88"), percentual(item.repasse(), "0.90"), usuario);
        criarPrecoSeAusente(procedimento, "CONVENIO", "SULAMERICA", "ESPECIAL", percentual(item.valor(), "0.85"), percentual(item.repasse(), "0.88"), usuario);
        criarPrecoSeAusente(procedimento, "COPARTICIPACAO", "TODOS", "TODOS", percentual(item.valor(), "0.35"), percentual(item.repasse(), "0.35"), usuario);
        if (item.valor().compareTo(BigDecimal.ZERO) == 0) {
            criarPrecoSeAusente(procedimento, "CORTESIA", "TODOS", "TODOS", BigDecimal.ZERO, BigDecimal.ZERO, usuario);
        }
    }

    private void criarPrecoSeAusente(ProcedimentoConsultorio procedimento, String tipo, String convenio, String plano,
            BigDecimal valor, BigDecimal repasse, String usuario) {
        if (precoRepository.existeEscopo(procedimento.getIdprocedimentoconsultorio(), procedimento.getUnidade(), tipo, convenio, plano)) {
            return;
        }
        ProcedimentoConsultorioPreco preco = new ProcedimentoConsultorioPreco();
        preco.setUnidade(procedimento.getUnidade());
        preco.setIdprocedimentoConsultorio(procedimento.getIdprocedimentoconsultorio());
        preco.setTipoCobranca(tipo);
        preco.setConvenio(convenio);
        preco.setPlano(plano);
        preco.setValor(valor(valor));
        preco.setValorRepasse(valor(repasse));
        preco.setAtivo("S");
        preco.setCriadoEm(LocalDateTime.now());
        preco.setCriadoPor(usuario);
        preco.setAtualizadoEm(LocalDateTime.now());
        preco.setAtualizadoPor(usuario);
        precoRepository.persist(preco);
    }

    private BigDecimal percentual(BigDecimal valor, String percentual) {
        return valor(valor).multiply(new BigDecimal(percentual)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private List<ServicoPadrao> catalogoPadrao() {
        return List.of(
                new ServicoPadrao("CONS-CLIN", "CONSULTA CLINICA GERAL", "CLINICA MEDICA", "PARTICULAR", bd("180.00"), bd("105.00")),
                new ServicoPadrao("RET-CONS", "RETORNO DE CONSULTA", "CLINICA MEDICA", "PARTICULAR", bd("0.00"), bd("0.00")),
                new ServicoPadrao("TELE-CONS", "TELECONSULTA", "CLINICA MEDICA", "PARTICULAR", bd("140.00"), bd("80.00")),
                new ServicoPadrao("CONS-PED", "CONSULTA PEDIATRICA", "PEDIATRIA", "PARTICULAR", bd("220.00"), bd("135.00")),
                new ServicoPadrao("CONS-GIN", "CONSULTA GINECOLOGICA", "GINECOLOGIA", "PARTICULAR", bd("240.00"), bd("145.00")),
                new ServicoPadrao("CONS-PRE", "CONSULTA PRE-NATAL", "OBSTETRICIA", "PARTICULAR", bd("240.00"), bd("145.00")),
                new ServicoPadrao("CONS-CARD", "CONSULTA CARDIOLOGICA", "CARDIOLOGIA", "PARTICULAR", bd("280.00"), bd("170.00")),
                new ServicoPadrao("CONS-DERM", "CONSULTA DERMATOLOGICA", "DERMATOLOGIA", "PARTICULAR", bd("280.00"), bd("170.00")),
                new ServicoPadrao("CONS-ORT", "CONSULTA ORTOPEDICA", "ORTOPEDIA", "PARTICULAR", bd("260.00"), bd("155.00")),
                new ServicoPadrao("CONS-PSIQ", "CONSULTA PSIQUIATRICA", "PSIQUIATRIA", "PARTICULAR", bd("320.00"), bd("200.00")),
                new ServicoPadrao("SESS-PSI", "SESSAO DE PSICOLOGIA", "PSICOLOGIA", "PARTICULAR", bd("160.00"), bd("100.00")),
                new ServicoPadrao("CONS-NUT", "CONSULTA NUTRICIONAL", "NUTRICAO", "PARTICULAR", bd("180.00"), bd("110.00")),
                new ServicoPadrao("PROC-ECG", "ELETROCARDIOGRAMA", "CARDIOLOGIA", "PARTICULAR", bd("120.00"), bd("35.00")),
                new ServicoPadrao("PROC-COL", "COLETA DE EXAMES LABORATORIAIS", "LABORATORIO", "PARTICULAR", bd("35.00"), bd("12.00")),
                new ServicoPadrao("PROC-CUR", "CURATIVO AMBULATORIAL", "ENFERMAGEM", "PARTICULAR", bd("90.00"), bd("35.00")),
                new ServicoPadrao("PROC-PONT", "RETIRADA DE PONTOS", "ENFERMAGEM", "PARTICULAR", bd("80.00"), bd("30.00")),
                new ServicoPadrao("PROC-INJ", "APLICACAO DE MEDICAMENTO INJETAVEL", "ENFERMAGEM", "PARTICULAR", bd("45.00"), bd("18.00")),
                new ServicoPadrao("PROC-NEB", "NEBULIZACAO", "ENFERMAGEM", "PARTICULAR", bd("50.00"), bd("18.00")),
                new ServicoPadrao("PROC-GLI", "TESTE DE GLICEMIA CAPILAR", "ENFERMAGEM", "PARTICULAR", bd("25.00"), bd("8.00")),
                new ServicoPadrao("PROC-PA", "AFERICAO DE PRESSAO ARTERIAL", "ENFERMAGEM", "PARTICULAR", bd("20.00"), bd("6.00")),
                new ServicoPadrao("PROC-PREV", "COLETA DE PREVENTIVO", "GINECOLOGIA", "PARTICULAR", bd("110.00"), bd("45.00")),
                new ServicoPadrao("PROC-BIOP", "PEQUENO PROCEDIMENTO AMBULATORIAL", "CIRURGIA AMBULATORIAL", "PARTICULAR", bd("350.00"), bd("160.00")),
                new ServicoPadrao("DOC-ATEST", "ATESTADO OU DECLARACAO MEDICA", "DOCUMENTOS", "PARTICULAR", bd("30.00"), bd("10.00")),
                new ServicoPadrao("DOC-LAUDO", "RELATORIO MEDICO SIMPLES", "DOCUMENTOS", "PARTICULAR", bd("80.00"), bd("30.00")));
    }

    private BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private record ServicoPadrao(String codigo, String nome, String especialidade, String tipoCobranca,
            BigDecimal valor, BigDecimal repasse) {
    }
}
