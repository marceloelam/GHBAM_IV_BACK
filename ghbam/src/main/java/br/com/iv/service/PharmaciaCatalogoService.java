package br.com.iv.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import br.com.iv.entity.PharmaciaCategoria;
import br.com.iv.entity.PharmaciaProduto;

/**
 * Catalogo de produtos entre unidades. Cada unidade tem o proprio cadastro;
 * aqui a unidade da sessao consulta o catalogo de outra unidade ou uma
 * planilha e copia os itens que escolher. A copia e independente: editar um
 * lado nao altera o outro.
 */
@ApplicationScoped
public class PharmaciaCatalogoService {
    static final int LIMITE_IMPORTACAO = 5000;
    static final Set<String> LISTAS_344 = Set.of("A1", "A2", "A3", "B1", "B2", "C1", "C2", "C3", "C4", "C5");

    @Inject EntityManager em;

    /** Unidades com produtos ativos, exceto a propria, com a quantidade de cada uma. */
    public List<Map<String, Object>> catalogos(String unidade) {
        List<Object[]> linhas = em.createQuery(
                "select p.unidadeSigla, count(p) from PharmaciaProduto p where p.ativo = true and p.unidadeSigla <> :u "
                        + "group by p.unidadeSigla order by p.unidadeSigla", Object[].class)
                .setParameter("u", unidade).getResultList();
        List<Map<String, Object>> saida = new ArrayList<>();
        for (Object[] l : linhas) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("unidade", l[0]);
            m.put("produtos", ((Number) l[1]).longValue());
            saida.add(m);
        }
        return saida;
    }

    /** Catalogo da origem para conferencia, marcando o que a unidade da sessao ja possui. */
    public List<Map<String, Object>> visualizar(String origem, String destino) {
        exigirOrigemValida(origem, destino);
        Set<String> nomes = nomesDa(destino);
        List<Map<String, Object>> saida = new ArrayList<>();
        for (PharmaciaProduto p : ativosDa(origem)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("nomeComercial", p.getNomeComercial());
            m.put("nomeGenerico", p.getNomeGenerico());
            m.put("categoria", p.getCategoria() != null ? p.getCategoria().getNome() : null);
            m.put("formaFarmaceutica", p.getFormaFarmaceutica());
            m.put("unidadeMedida", p.getUnidadeMedida());
            m.put("principioAtivo", p.getPrincipioAtivo());
            m.put("concentracao", p.getConcentracao());
            m.put("altaVigilancia", Boolean.TRUE.equals(p.getAltaVigilancia()));
            m.put("antimicrobiano", Boolean.TRUE.equals(p.getAntimicrobiano()));
            m.put("listaControlada", p.getListaControlada());
            m.put("jaExiste", nomes.contains(chave(p.getNomeComercial())));
            saida.add(m);
        }
        return saida;
    }

    /** Copia para a unidade da sessao os produtos escolhidos da origem; repetidos sao ignorados. */
    @Transactional
    public Map<String, Object> importar(String origem, String destino, List<Long> ids) {
        exigirOrigemValida(origem, destino);
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Selecione ao menos um produto para importar.");
        if (ids.size() > LIMITE_IMPORTACAO) throw new IllegalArgumentException("Importe no maximo " + LIMITE_IMPORTACAO + " produtos por vez.");
        Set<Long> escolhidos = new HashSet<>(ids);
        Set<String> nomes = nomesDa(destino);
        Set<String> codigos = codigosDa(destino);
        int importados = 0;
        List<String> ignorados = new ArrayList<>();
        for (PharmaciaProduto o : ativosDa(origem)) {
            if (!escolhidos.contains(o.getId())) continue;
            if (!nomes.add(chave(o.getNomeComercial())) || (o.getCodigoBarras() != null && codigos.contains(o.getCodigoBarras()))) {
                ignorados.add(o.getNomeComercial());
                continue;
            }
            em.persist(copia(o, destino));
            if (o.getCodigoBarras() != null) codigos.add(o.getCodigoBarras());
            importados++;
        }
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("origem", origem);
        saida.put("destino", destino);
        saida.put("importados", importados);
        saida.put("ignorados", ignorados);
        return saida;
    }

    /**
     * Linhas de planilha (Excel/CSV) lidas no navegador. Com simular=true so
     * confere e devolve o status de cada linha; sem simular grava, na mesma
     * transacao, apenas as linhas validas.
     */
    @Transactional
    public Map<String, Object> importarPlanilha(String destino, List<Map<String, Object>> linhas, boolean simular) {
        if (linhas == null || linhas.isEmpty()) throw new IllegalArgumentException("A planilha nao tem linhas de produto.");
        if (linhas.size() > LIMITE_IMPORTACAO) throw new IllegalArgumentException("Importe no maximo " + LIMITE_IMPORTACAO + " produtos por vez.");
        Map<String, PharmaciaCategoria> categorias = new HashMap<>();
        for (PharmaciaCategoria c : em.createQuery("from PharmaciaCategoria c where c.ativo = true", PharmaciaCategoria.class).getResultList()) {
            categorias.putIfAbsent(chave(c.getNome()), c);
        }
        Set<String> nomes = nomesDa(destino);
        Set<String> codigos = codigosDa(destino);
        List<Map<String, Object>> resultado = new ArrayList<>();
        int validas = 0;
        for (int i = 0; i < linhas.size(); i++) {
            Map<String, Object> l = linhas.get(i) == null ? Map.of() : linhas.get(i);
            List<String> problemas = new ArrayList<>();
            String nome = texto(l, "nomeComercial", 150);
            String generico = texto(l, "nomeGenerico", 150);
            String forma = texto(l, "formaFarmaceutica", 60);
            String medida = texto(l, "unidadeMedida", 30);
            String categoriaNome = texto(l, "categoria", 100);
            String codigo = texto(l, "codigoBarras", 50);
            String lista = texto(l, "listaControlada", 5);
            if (nome == null) problemas.add("nome comercial vazio");
            if (generico == null) problemas.add("nome generico vazio");
            if (forma == null) problemas.add("forma farmaceutica vazia");
            if (medida == null) problemas.add("unidade de medida vazia");
            PharmaciaCategoria categoria = categoriaNome == null ? null : categorias.get(chave(categoriaNome));
            if (categoria == null) problemas.add(categoriaNome == null ? "categoria vazia" : "categoria '" + categoriaNome + "' nao cadastrada");
            if (lista != null) lista = lista.toUpperCase(Locale.ROOT);
            if (lista != null && !LISTAS_344.contains(lista)) problemas.add("lista controlada '" + lista + "' invalida (use A1 a C5)");
            if (nome != null && nomes.contains(chave(nome))) problemas.add("ja existe na unidade ou repetido na planilha");
            if (codigo != null && codigos.contains(codigo)) problemas.add("codigo de barras ja usado na unidade ou na planilha");

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("linha", i + 2); // linha 1 e o cabecalho
            r.put("nomeComercial", nome);
            r.put("principioAtivo", texto(l, "principioAtivo", 255));
            r.put("valida", problemas.isEmpty());
            r.put("problemas", problemas);
            resultado.add(r);
            if (!problemas.isEmpty()) continue;

            nomes.add(chave(nome));
            if (codigo != null) codigos.add(codigo);
            validas++;
            if (simular) continue;
            PharmaciaProduto p = new PharmaciaProduto();
            p.setNomeComercial(nome);
            p.setNomeGenerico(generico);
            p.setCodigoBarras(codigo);
            p.setFormaFarmaceutica(forma.toUpperCase(Locale.ROOT));
            p.setCategoria(categoria);
            p.setUnidadeMedida(medida);
            p.setFabricante(texto(l, "fabricante", 150));
            p.setRegistroAnvisa(texto(l, "registroAnvisa", 50));
            p.setUnidadeSigla(destino);
            p.setAtivo(true);
            p.setPrincipioAtivo(texto(l, "principioAtivo", 255));
            p.setConcentracao(texto(l, "concentracao", 100));
            p.setViaAdministracao(texto(l, "viaAdministracao", 50));
            String atc = texto(l, "codigoAtc", 10);
            p.setCodigoAtc(atc == null ? null : atc.toUpperCase(Locale.ROOT));
            p.setAltaVigilancia(sim(l.get("altaVigilancia")));
            p.setAntimicrobiano(sim(l.get("antimicrobiano")));
            p.setListaControlada(lista);
            p.setExigeReceita(sim(l.get("exigeReceita")));
            em.persist(p);
        }
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("destino", destino);
        saida.put("simulacao", simular);
        saida.put("validas", validas);
        saida.put("invalidas", linhas.size() - validas);
        saida.put("importados", simular ? 0 : validas);
        saida.put("linhas", resultado);
        return saida;
    }

    static PharmaciaProduto copia(PharmaciaProduto o, String destino) {
        PharmaciaProduto p = new PharmaciaProduto();
        p.setNomeComercial(o.getNomeComercial());
        p.setNomeGenerico(o.getNomeGenerico());
        p.setCodigoBarras(o.getCodigoBarras());
        p.setFormaFarmaceutica(o.getFormaFarmaceutica());
        p.setCategoria(o.getCategoria());
        p.setUnidadeMedida(o.getUnidadeMedida());
        p.setFabricante(o.getFabricante());
        p.setRegistroAnvisa(o.getRegistroAnvisa());
        p.setUnidadeSigla(destino);
        p.setAtivo(true);
        p.setPrincipioAtivo(o.getPrincipioAtivo());
        p.setConcentracao(o.getConcentracao());
        p.setViaAdministracao(o.getViaAdministracao());
        p.setCodigoAtc(o.getCodigoAtc());
        p.setAltaVigilancia(Boolean.TRUE.equals(o.getAltaVigilancia()));
        p.setAntimicrobiano(Boolean.TRUE.equals(o.getAntimicrobiano()));
        p.setListaControlada(o.getListaControlada());
        p.setExigeReceita(Boolean.TRUE.equals(o.getExigeReceita()));
        return p;
    }

    static String chave(String nome) {
        return nome == null ? "" : nome.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    static String texto(Map<String, Object> linha, String campo, int maximo) {
        Object v = linha.get(campo);
        if (v == null) return null;
        String s = String.valueOf(v).trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) return null;
        return s.length() > maximo ? s.substring(0, maximo) : s;
    }

    static boolean sim(Object v) {
        if (v instanceof Boolean b) return b;
        String s = v == null ? "" : chave(String.valueOf(v));
        return Set.of("SIM", "S", "X", "TRUE", "1").contains(s);
    }

    static void exigirOrigemValida(String origem, String destino) {
        if (origem == null || origem.isBlank()) throw new IllegalArgumentException("Informe a unidade de origem do catalogo.");
        if (origem.trim().equalsIgnoreCase(destino)) throw new IllegalArgumentException("Escolha um catalogo de outra unidade.");
    }

    List<PharmaciaProduto> ativosDa(String unidade) {
        return em.createQuery("from PharmaciaProduto p left join fetch p.categoria where p.unidadeSigla = :u and p.ativo = true "
                + "order by p.nomeComercial", PharmaciaProduto.class).setParameter("u", unidade.trim()).getResultList();
    }

    Set<String> nomesDa(String unidade) {
        Set<String> nomes = new HashSet<>();
        for (String n : em.createQuery("select p.nomeComercial from PharmaciaProduto p where p.unidadeSigla = :u", String.class)
                .setParameter("u", unidade).getResultList()) nomes.add(chave(n));
        return nomes;
    }

    Set<String> codigosDa(String unidade) {
        return new HashSet<>(em.createQuery(
                "select p.codigoBarras from PharmaciaProduto p where p.unidadeSigla = :u and p.codigoBarras is not null", String.class)
                .setParameter("u", unidade).getResultList());
    }
}
