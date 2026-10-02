package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.iv.entity.EvolucaoSistema;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class EvolucaoSistemaService implements Serializable {
    private static final long serialVersionUID = 1L;
    private volatile boolean tabelaGarantida;

    @Inject
    EntityManager entityManager;

    @Transactional
    public List<Map<String, Object>> listar(String unidade, String modulo, String tipo, String busca) {
        garantirTabela();

        String unidadeEfetiva = unidadeNormalizada(unidade);
        String moduloEfetivo = texto(modulo).toLowerCase();
        String tipoEfetivo = texto(tipo).toLowerCase();
        String buscaTexto = texto(busca).toLowerCase();
        String buscaEfetiva = "%" + buscaTexto + "%";

        String query = "(?1 = 'TODAS' or upper(unidade) = upper(?1)) "
                + "and (?2 = '' or ?2 = 'todos' or lower(modulo) = ?2) "
                + "and (?3 = '' or ?3 = 'todos' or lower(tipo) = ?3) "
                + "and (?4 = '' or lower(titulo) like ?5 or lower(descricao) like ?5 or lower(impacto) like ?5 "
                + "or lower(modulo) like ?5 or lower(tipo) like ?5 or lower(autor) like ?5 or lower(arquivos) like ?5) "
                + "order by criadoEm desc";

        List<EvolucaoSistema> lista = EvolucaoSistema.list(query, unidadeEfetiva, moduloEfetivo, tipoEfetivo, buscaTexto, buscaEfetiva);
        return lista.stream().limit(500).map(this::toMap).toList();
    }

    @Transactional
    public Map<String, Object> salvar(Map<String, Object> dto) {
        garantirTabela();
        EvolucaoSistema item = new EvolucaoSistema();
        aplicar(item, dto);
        item.setCriadoEm(LocalDateTime.now());
        item.persist();
        return toMap(item);
    }

    @Transactional
    public void excluir(int id) {
        garantirTabela();
        buscar(id).delete();
    }

    public Map<String, Object> toMap(EvolucaoSistema item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getIdevolucaosistema());
        map.put("idevolucaosistema", item.getIdevolucaosistema());
        map.put("unidade", item.getUnidade());
        map.put("modulo", item.getModulo());
        map.put("tipo", item.getTipo());
        map.put("titulo", item.getTitulo());
        map.put("descricao", item.getDescricao());
        map.put("impacto", item.getImpacto());
        map.put("arquivos", arquivos(item.getArquivos()));
        map.put("origem", item.getOrigem());
        map.put("autor", item.getAutor());
        map.put("matricula", item.getMatricula());
        map.put("dataHora", item.getCriadoEm());
        map.put("criadoEm", item.getCriadoEm());
        return map;
    }

    private EvolucaoSistema buscar(int id) {
        Optional<EvolucaoSistema> item = EvolucaoSistema.findByIdOptional(id);
        if (item.isEmpty()) {
            throw new IllegalArgumentException("Registro de evolucao nao encontrado.");
        }
        return item.get();
    }

    private void aplicar(EvolucaoSistema item, Map<String, Object> dto) {
        item.setUnidade(unidadeNormalizada(texto(dto == null ? null : dto.get("unidade"))));
        item.setModulo(limitar(textoOuPadrao(dto == null ? null : dto.get("modulo"), "Sistema"), 80));
        item.setTipo(limitar(textoOuPadrao(dto == null ? null : dto.get("tipo"), "Melhoria"), 40));
        item.setTitulo(limitar(textoOuPadrao(dto == null ? null : dto.get("titulo"), "Evolucao sem titulo"), 180));
        item.setDescricao(texto(dto == null ? null : dto.get("descricao")));
        item.setImpacto(texto(dto == null ? null : dto.get("impacto")));
        item.setArquivos(normalizarArquivos(dto == null ? null : dto.get("arquivos")));
        item.setOrigem(limitar(textoOuPadrao(dto == null ? null : dto.get("origem"), "Manual"), 40));
        item.setAutor(limitar(textoOuPadrao(dto == null ? null : dto.get("autor"), textoOuPadrao(dto == null ? null : dto.get("usuario"), "Sistema")), 120));
        item.setMatricula(limitar(texto(dto == null ? null : dto.get("matricula")), 40));
    }

    private synchronized void garantirTabela() {
        if (tabelaGarantida) {
            return;
        }

        entityManager.createNativeQuery("""
                create sequence if not exists evolucao_sistema_idevolucaosistema_seq
                """).executeUpdate();
        entityManager.createNativeQuery("""
                create table if not exists evolucao_sistema (
                    idevolucaosistema integer primary key default nextval('evolucao_sistema_idevolucaosistema_seq'),
                    unidade varchar(30),
                    modulo varchar(80),
                    tipo varchar(40),
                    titulo varchar(180),
                    descricao text,
                    impacto text,
                    arquivos text,
                    origem varchar(40),
                    autor varchar(120),
                    matricula varchar(40),
                    criado_em timestamp
                )
                """).executeUpdate();
        entityManager.createNativeQuery("""
                create index if not exists idx_evolucao_sistema_unidade_modulo on evolucao_sistema (unidade, modulo)
                """).executeUpdate();
        entityManager.createNativeQuery("""
                create index if not exists idx_evolucao_sistema_criado_em on evolucao_sistema (criado_em desc)
                """).executeUpdate();

        // DDL em toda requisicao gera locks de catalogo e pode causar deadlock
        // quando duas evolucoes sao registradas simultaneamente.
        tabelaGarantida = true;
    }

    private String unidadeNormalizada(String unidade) {
        String value = textoOuPadrao(unidade, "TODAS").split(":")[0].trim().toUpperCase();
        return value.isBlank() ? "TODAS" : limitar(value, 30);
    }

    private List<String> arquivos(String value) {
        String text = String.valueOf(value == null ? "" : value).replace("\r", "").trim();
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("\\n")).stream().map(this::texto).filter(item -> !item.isBlank()).toList();
    }

    private String normalizarArquivos(Object value) {
        if (value instanceof List<?> lista) {
            return lista.stream().map(this::texto).filter(item -> !item.isBlank()).reduce((a, b) -> a + "\n" + b).orElse("");
        }
        return String.valueOf(value == null ? "" : value).replace("\r", "").trim();
    }

    private String textoOuPadrao(Object value, String padrao) {
        String result = texto(value);
        return result.isBlank() ? padrao : result;
    }

    private String texto(Object value) {
        return value == null ? "" : String.valueOf(value).replaceAll("[\\r\\n]+", " ").trim();
    }

    private String limitar(String value, int max) {
        String text = texto(value);
        return text.length() <= max ? text : text.substring(0, max);
    }
}
