package br.com.iv.service;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.entity.Servidor;
import br.com.iv.entity.Servidor2;

/**
 * Regras da coordenacao de setor. O coordenador so altera acessos (colunas booleanas de servidor2) da
 * propria equipe, nunca os seus, nunca acima do que ele mesmo tem e nunca os reservados a administracao.
 * Sem acesso a banco, para poder ser testado.
 */
public final class RegrasCoordenacaoSetor {

    private RegrasCoordenacaoSetor() {}

    public static final String COORDENADOR = "COORDENADOR";
    public static final String MEMBRO = "MEMBRO";

    /**
     * Administracao, seguranca, dados de todos os pacientes ou configuracao da unidade: so a administracao
     * concede, pela tela de servidores. Chaves terminadas em "_adm" tambem ficam aqui.
     */
    static final Set<String> RESERVADAS_ADMINISTRACAO = Set.of(
            "superusuario", "cadsalas", "cadsetores", "cadunidades", "cadusuarios", "caddrives", "listausuarios",
            "logs", "personalizacaovisual", "integracaosus", "migracaodados", "exportardadoslgpd", "prontuariocompleto",
            "auditoriaclinica", "configuracaovigilanciaepidemiologica", "consultorioadmin", "correcaobam",
            "restaurarpaciente", "nirrestaurarinternacao", "cadleitos", "cadsetor", "limparpainel");

    /** Campos de login/situação do cadastro: a coordenação NUNCA altera, nem que a coluna venha a existir em servidor2. */
    static final Set<String> PROIBIDAS_COORDENACAO = Set.of("ativo", "usuarioad", "pwd", "senha", "ksuperusuario");

    private static final Set<String> CHAVES_SERVIDOR2 = lerChaves();

    /** Colunas booleanas de servidor2 (os acessos da tela de permissoes). Nome do campo = nome da coluna. */
    private static Set<String> lerChaves() {
        Set<String> chaves = new TreeSet<>();
        for (Field campo : Servidor2.class.getDeclaredFields()) {
            if (Modifier.isStatic(campo.getModifiers())) continue;
            // Campos injetados pelo Hibernate (ex.: $$_hibernate_useTracker) não são colunas nem acessos.
            if (campo.isSynthetic() || campo.getName().contains("$") || Modifier.isTransient(campo.getModifiers())) continue;
            if (PROIBIDAS_COORDENACAO.contains(campo.getName().toLowerCase())) continue;
            if (campo.getType() == Boolean.class || campo.getType() == boolean.class) chaves.add(campo.getName());
        }
        return chaves;
    }

    public static Set<String> chavesServidor2() {
        return CHAVES_SERVIDOR2;
    }

    /** Perfis da tabela servidor que a coordenação liga e desliga (nunca categoria do cargo nem administração). */
    public static Set<String> perfisConcediveis() {
        return PERFIS_CONCEDIVEIS;
    }

    public static boolean reservada(String chave) {
        return chave == null || RESERVADAS_ADMINISTRACAO.contains(chave) || chave.endsWith("_adm")
                || PROIBIDAS_COORDENACAO.contains(chave.toLowerCase());
    }

    /**
     * Acessos que o autor pode ligar ou desligar na equipe. Coordenador: so o que ele proprio tem.
     * Administracao: tudo que nao e reservado (o reservado continua na tela de servidores).
     */
    public static List<String> concediveis(Collection<String> todas, Set<String> doAutor, boolean administrador) {
        List<String> out = new ArrayList<>();
        for (String chave : todas) {
            if (reservada(chave)) continue;
            if (administrador || doAutor.contains(chave)) out.add(chave);
        }
        return out;
    }

    /** Acessos ligados de um servidor2 (para o calculo do que o coordenador pode conceder). */
    public static Set<String> ligados(Servidor2 s2) {
        Set<String> out = new TreeSet<>();
        if (s2 == null) return out;
        for (String chave : CHAVES_SERVIDOR2) {
            if (Boolean.TRUE.equals(valor(s2, chave))) out.add(chave);
        }
        return out;
    }

    public static Boolean valor(Servidor2 s2, String chave) {
        if (s2 == null || !CHAVES_SERVIDOR2.contains(chave)) return null;
        try {
            Field campo = Servidor2.class.getDeclaredField(chave);
            campo.setAccessible(true);
            return (Boolean) campo.get(s2);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Quem altera e quem e alterado: motivo da recusa ou null. Vale para acessos e perfis. */
    public static String recusaVinculo(boolean administrador, boolean autorCoordenaSetor, String papelAlvo,
                                       String matriculaAutor, String matriculaAlvo) {
        if (!administrador && !autorCoordenaSetor) return "Você não coordena este setor.";
        if (papelAlvo == null) return "O servidor não faz parte da equipe deste setor.";
        if (matriculaAutor != null && matriculaAutor.equalsIgnoreCase(matriculaAlvo)) {
            return "Ninguém altera os próprios acessos por aqui. Peça à administração.";
        }
        if (!administrador && !MEMBRO.equals(papelAlvo)) return "Coordenador não altera acessos de outro coordenador.";
        return null;
    }

    /** Motivo da recusa ou null quando a alteracao e permitida. */
    public static String recusa(boolean administrador, boolean autorCoordenaSetor, String papelAlvo,
                                String matriculaAutor, String matriculaAlvo, String chave, List<String> concediveis) {
        String vinculo = recusaVinculo(administrador, autorCoordenaSetor, papelAlvo, matriculaAutor, matriculaAlvo);
        if (vinculo != null) return vinculo;
        if (!CHAVES_SERVIDOR2.contains(chave)) return "Acesso desconhecido.";
        if (reservada(chave)) return "Este acesso é reservado à administração.";
        if (!concediveis.contains(chave)) return "Você só pode conceder acessos que você mesmo possui.";
        return null;
    }

    // ---- setores = grupos do menu (mapa gerado do AppSidebar pelo frontend) ----------------------

    /** Chave de perfil (tabela servidor) na API, para nao misturar com as colunas de servidor2. */
    public static final String PREFIXO_PERFIL = "perfil:";

    /**
     * Perfis que a coordenacao pode ligar na equipe. Fora: categorias profissionais (medico, enfermagem,
     * dentista, multidisciplinar), que o cargo define, e administracao/subadm.
     */
    static final Set<String> PERFIS_CONCEDIVEIS = Set.of(
            "recepcao", "triagem", "medicacao", "laboratorio", "rx", "tomografia", "ultrassonografia",
            "faturamento", "remedios", "farmcentral", "farmhmsfx", "rh");
    /** Atendimento direto ao paciente: o cargo SERVIDOR (administrativo) nao recebe. */
    static final Set<String> PERFIS_ASSISTENCIAIS = Set.of("triagem", "medicacao");
    static final Set<String> CARGOS_MULTIDISCIPLINARES = Set.of("ASSISTENTE", "PSIC", "NUTRICIONISTA", "FISIOTERAPEUTA", "FONOAUDIOLOGIA");

    private record MapaMenus(Map<String, List<String>> acessos, Map<String, Map<String, List<String>>> menus,
                             Map<String, List<String>> modulos, Map<String, Map<String, List<String>>> rotas) {}

    /** Itens que aparecem em todos os grupos (chat, pedir material...): não são ligados por grupo. */
    private static final Set<String> ROTAS_GERAIS = Set.of("/dashboard", "/base2/coordenacao-setor");

    /**
     * Grupo do menu -> rotas do link deste acesso (sem os itens gerais). Cada menu liga o seu (decisão do Marcelo,
     * 11/11/2026): o que fica desligado num grupo vai para servidor_menu_item_oculto. Mesma regra de acessoPorMenu.js.
     */
    public static Map<String, List<String>> rotasDoAcesso(String chave) {
        Map<String, List<String>> out = new java.util.LinkedHashMap<>();
        mapa().rotas().getOrDefault(chave, Map.of()).forEach((grupo, lista) -> {
            List<String> rotas = lista.stream().filter(r -> !ROTAS_GERAIS.contains(r)).toList();
            if (!rotas.isEmpty()) out.put(grupo, rotas);
        });
        return out;
    }

    private static volatile MapaMenus mapa;
    private static volatile long mapaLidoEm;
    private static final long MAPA_VALIDADE_MS = 60_000;

    /**
     * coordenacao/menus-por-acesso.json, gerado por `npm run gerar:menus-acessos`. Sem o arquivo: sem filtro por grupo.
     * Relido a cada minuto: um mapa regerado vale sem reiniciar o backend.
     */
    @SuppressWarnings("unchecked")
    private static MapaMenus mapa() {
        MapaMenus atual = mapa;
        if (atual != null && System.currentTimeMillis() - mapaLidoEm < MAPA_VALIDADE_MS) return atual;
        mapaLidoEm = System.currentTimeMillis();
        Map<String, List<String>> acessos = Map.of();
        Map<String, Map<String, List<String>>> menus = Map.of();
        Map<String, List<String>> modulos = Map.of();
        Map<String, Map<String, List<String>>> rotas = Map.of();
        try (InputStream in = RegrasCoordenacaoSetor.class.getClassLoader().getResourceAsStream("coordenacao/menus-por-acesso.json")) {
            if (in != null) {
                Map<String, Object> json = new ObjectMapper().readValue(in, Map.class);
                acessos = (Map<String, List<String>>) json.getOrDefault("acessos", Map.of());
                menus = (Map<String, Map<String, List<String>>>) json.getOrDefault("menus", Map.of());
                modulos = (Map<String, List<String>>) json.getOrDefault("modulos", Map.of());
                rotas = (Map<String, Map<String, List<String>>>) json.getOrDefault("rotas", Map.of());
            }
        } catch (IOException | RuntimeException e) {
            // Mapa ilegivel: segue sem filtro por grupo; as demais travas continuam valendo.
        }
        mapa = new MapaMenus(acessos, menus, modulos, rotas);
        return mapa;
    }

    /** O setor e um grupo do menu? Setores antigos (cadastro livre) seguem sem filtro por grupo. */
    public static boolean setorDoMenu(String setor) {
        return setor != null && mapa().menus().containsKey(setor);
    }

    /**
     * Acessos do grupo do menu: os mesmos que a tela de servidores mostra no(s) módulo(s) desse grupo
     * (mapa "modulos"). Grupo sem módulo: os acessos cujo link aparece nele.
     */
    public static List<String> acessosDoSetor(String setor, List<String> chaves) {
        if (!setorDoMenu(setor)) return chaves;
        List<String> doModulo = mapa().modulos().get(setor);
        if (doModulo != null) return chaves.stream().filter(doModulo::contains).toList();
        Map<String, List<String>> acessos = mapa().acessos();
        return chaves.stream().filter(chave -> acessos.getOrDefault(chave, List.of()).contains(setor)).toList();
    }

    /** Perfis que abrem o grupo do menu e que a coordenacao pode conceder. */
    public static List<String> perfisDoSetor(String setor) {
        if (!setorDoMenu(setor)) return List.of();
        List<String> perfis = mapa().menus().get(setor).getOrDefault("perfis", List.of());
        return perfis.stream().filter(PERFIS_CONCEDIVEIS::contains).toList();
    }

    private static String normalizarCargo(String cargo) {
        String semAcento = Normalizer.normalize(cargo == null ? "" : cargo, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String c = semAcento.trim().toUpperCase(Locale.ROOT);
        return c.isEmpty() ? "SERVIDOR" : c;
    }

    /** Regra do cargo para um perfil (mesma da tela de servidores): motivo da recusa ou null. */
    public static String recusaPerfil(String perfil, String cargoAlvo) {
        if (!PERFIS_CONCEDIVEIS.contains(perfil)) return "Este perfil é definido pelo cargo ou pela administração.";
        String cargo = normalizarCargo(cargoAlvo);
        if (CARGOS_MULTIDISCIPLINARES.contains(cargo)) return "Cargo multidisciplinar recebe só a própria categoria.";
        if (PERFIS_ASSISTENCIAIS.contains(perfil) && "SERVIDOR".equals(cargo)) {
            return "Triagem e sala de medicação exigem cargo profissional.";
        }
        return null;
    }

    /** Valor de um perfil (campo booleano da entidade Servidor). */
    public static Boolean valorPerfil(Servidor s, String perfil) {
        if (s == null || !PERFIS_CONCEDIVEIS.contains(perfil)) return null;
        try {
            Field campo = Servidor.class.getDeclaredField(perfil);
            campo.setAccessible(true);
            Object v = campo.get(s);
            return v instanceof Boolean b ? b : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
