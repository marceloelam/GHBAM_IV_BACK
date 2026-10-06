package br.com.iv.producaoassistencial;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import br.com.iv.producaomedica.ProducaoMedicaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class ProducaoAssistencialService {
    public enum Tipo {
        MEDICO, MULTIDISCIPLINAR, PARECER_RESPONDIDO, ANAMNESE,
        EVOLUCAO_MEDICA, EVOLUCAO_MULTIDISCIPLINAR, EVOLUCAO_ENFERMAGEM,
        ODONTOLOGICO, APS_INDIVIDUAL, APS_ODONTOLOGICO, APS_DOMICILIAR,
        APS_VISITA, APS_PROCEDIMENTO, APS_VACINACAO, APS_COLETIVA
    }
    public record Linha(String tipo, String categoria, String profissional, String registro, String matricula, LocalDate data, long total) {}
    public record Relatorio(LocalDate inicio, LocalDate fim, String unidade, List<String> tipos,
            long totalAtendimentos, long totalDocumentos, long totalGeral, List<Linha> linhas, OpcoesFiltros opcoesFiltros) {
        public Relatorio(LocalDate inicio, LocalDate fim, String unidade, List<String> tipos,
                long totalAtendimentos, long totalDocumentos, long totalGeral, List<Linha> linhas) {
            this(inicio, fim, unidade, tipos, totalAtendimentos, totalDocumentos, totalGeral, linhas, new OpcoesFiltros(List.of()));
        }
    }
    public record FiltroProfissional(String profissional, String registro, String matricula, List<String> categorias) {}
    public record OpcoesFiltros(List<FiltroProfissional> profissionais) {}

    public record Detalhe(String id, Integer bam, String prontuario, LocalDate data, String hora,
            String paciente, String descricao, String situacao, long quantidade) {}
    public record RegistroProducao(Linha linha, Detalhe detalhe) {}
    public record Secao(String titulo, String conteudo, String formato) {}
    public record ConteudoRegistro(String tipo, Detalhe registro, List<Secao> secoes) {}
    public record Detalhamento(long totalRegistros, long totalProducao, int pagina, int tamanho, List<Detalhe> registros) {}

    private final ProducaoAssistencialRepository repository;
    private final ProducaoMedicaRepository medica;
    @Inject
    public ProducaoAssistencialService(ProducaoAssistencialRepository repository, ProducaoMedicaRepository medica) {
        this.repository = repository;
        this.medica = medica;
    }

    public Relatorio gerar(LocalDate inicio, LocalDate fim, String unidade, String tipos) {
        validarPeriodo(inicio, fim, unidade);
        EnumSet<Tipo> selecionados = selecionar(tipos);
        List<Linha> linhas = new ArrayList<>();
        if (selecionados.contains(Tipo.MEDICO)) {
            var grupos = medica.buscar(inicio, fim, unidade, null).stream().collect(Collectors.groupingBy(
                    l -> List.of(texto(l.medico()), texto(l.matricula()), l.data().toString()), Collectors.counting()));
            grupos.forEach((chave, total) -> linhas.add(new Linha("MEDICO", "Medicina", chave.get(0), chave.get(1), chave.get(1), LocalDate.parse(chave.get(2)), total)));
        }
        EnumSet<Tipo> documentos = EnumSet.copyOf(selecionados);
        documentos.remove(Tipo.MEDICO);
        if (!documentos.isEmpty()) linhas.addAll(repository.buscar(inicio, fim, unidade, documentos));
        List<Linha> normalizadas = repository.normalizarAutores(linhas, unidade);
        linhas.clear(); linhas.addAll(normalizadas);
        linhas.sort(Comparator.comparing(Linha::tipo).thenComparing(Linha::categoria)
                .thenComparing(Linha::profissional).thenComparing(Linha::registro).thenComparing(Linha::data));
        long atendimentos = linhas.stream().filter(l -> atendimento(l.tipo())).mapToLong(Linha::total).sum();
        long total = linhas.stream().mapToLong(Linha::total).sum();
        return new Relatorio(inicio, fim, unidade, selecionados.stream().map(Enum::name).toList(),
                atendimentos, total - atendimentos, total, List.copyOf(linhas), repository.opcoesFiltros(unidade, selecionados));
    }


    private static void validarPeriodo(LocalDate inicio, LocalDate fim, String unidade) {
        if (inicio == null || fim == null || inicio.isAfter(fim))
            throw new BadRequestException("Informe um periodo valido: inicio deve ser anterior ou igual ao fim.");
        if (ChronoUnit.DAYS.between(inicio, fim) > 1826)
            throw new BadRequestException("O periodo maximo permitido e de 5 anos.");
        if (unidade == null || unidade.isBlank()) throw new BadRequestException("Unidade da sessao nao informada.");
    }

    public Detalhamento detalhes(LocalDate inicio, LocalDate fim, String unidade, String tipo,
            String profissional, String registro, String matricula, String categoria, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) throw new BadRequestException("Paginacao invalida.");
        List<Detalhe> registros = registrosDaLinha(inicio, fim, unidade, tipo, profissional, registro, matricula, categoria);
        long total = registros.stream().mapToLong(Detalhe::quantidade).sum();
        long offset = (long) pagina * tamanho;
        return new Detalhamento(registros.size(), total, pagina, tamanho,
                registros.stream().skip(offset).limit(tamanho).toList());
    }

    private List<Detalhe> registrosDaLinha(LocalDate inicio, LocalDate fim, String unidade, String tipo,
            String profissional, String registro, String matricula, String categoria) {
        validarPeriodo(inicio, fim, unidade);
        if (profissional == null || profissional.isBlank() || categoria == null || categoria.isBlank())
            throw new BadRequestException("Informe o profissional e a categoria da linha.");
        Tipo selecionado;
        try { selecionado = Tipo.valueOf(tipo == null ? "" : tipo); }
        catch (IllegalArgumentException e) { throw new BadRequestException("Tipo de producao invalido."); }
        List<RegistroProducao> candidatos;
        if (selecionado == Tipo.MEDICO) {
            candidatos = medica.buscar(inicio, fim, unidade, vazio(matricula) ? null : matricula.trim()).stream()
                    .map(l -> new RegistroProducao(new Linha("MEDICO", "Medicina", texto(l.medico()), texto(l.matricula()),
                            texto(l.matricula()), l.data(), 1), new Detalhe(Integer.toString(l.bam()), l.bam(), "",
                            l.data(), l.hora(), l.paciente(), l.setor(), l.desfecho(), 1))).toList();
        } else {
            candidatos = repository.buscarDetalhes(inicio, fim, unidade, selecionado, categoria, registro == null ? "" : registro);
        }
        List<Linha> autores = repository.normalizarAutores(candidatos.stream().map(RegistroProducao::linha).toList(), unidade);
        List<Detalhe> registros = new ArrayList<>();
        for (int i = 0; i < candidatos.size(); i++) {
            Linha linha = autores.get(i);
            boolean autor = vazio(matricula) ? texto(linha.profissional()).equalsIgnoreCase(profissional.trim())
                    : linha.matricula().equals(matricula.trim());
            if (autor && linha.categoria().equals(categoria) && linha.registro().equals(registro == null ? "" : registro))
                registros.add(candidatos.get(i).detalhe());
        }
        return registros;
    }

    public ConteudoRegistro conteudoRegistro(LocalDate inicio, LocalDate fim, String unidade, String tipo,
            String profissional, String registro, String matricula, String categoria, String id) {
        if (id == null || id.isBlank() || id.length() > 200) throw new BadRequestException("Registro invalido.");
        Detalhe autorizado = registrosDaLinha(inicio, fim, unidade, tipo, profissional, registro, matricula, categoria).stream()
                .filter(r -> r.id().equals(id)).findFirst().orElseThrow(() -> new NotFoundException("Registro nao localizado nesta producao."));
        return new ConteudoRegistro(tipo, autorizado, repository.conteudoRegistro(Tipo.valueOf(tipo), autorizado, inicio, fim, unidade));
    }

    private static boolean vazio(String value) { return value == null || value.isBlank(); }

    static EnumSet<Tipo> selecionar(String tipos) {
        if (tipos == null || tipos.isBlank()) throw new BadRequestException("Selecione ao menos uma categoria.");
        try {
            return Arrays.stream(tipos.split(",", -1)).map(String::trim).map(Tipo::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Tipo.class)));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Categoria de producao invalida.");
        }
    }
    private static boolean atendimento(String tipo) {
        return List.of("MEDICO", "MULTIDISCIPLINAR", "ODONTOLOGICO", "APS_INDIVIDUAL", "APS_ODONTOLOGICO", "APS_DOMICILIAR").contains(tipo);
    }
    private static String texto(String value) { return value == null || value.isBlank() ? "Nao informado" : value.trim(); }
}
