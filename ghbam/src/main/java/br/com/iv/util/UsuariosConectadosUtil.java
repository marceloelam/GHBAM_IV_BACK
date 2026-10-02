package br.com.iv.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsuariosConectadosUtil {
    private static final long ONLINE_STALE_MS = 45 * 1000;
// Mapeia unidade para lista de usuÃ¡rios conectados (nome + ip)
    private static final Map<String, Set<UsuarioConectado>> usuariosPorUnidade = new ConcurrentHashMap<>();
    private static final Logger LOGGER = Logger.getLogger(UsuariosConectadosUtil.class.getName());

    private static String normalizeUnidade(String unidade) {
        if (unidade == null) return null;
        return unidade.trim().toLowerCase();
    }

    // Remove usuÃ¡rio da unidade por matrÃ­cula. Retorna true se removeu pelo menos um usuÃ¡rio.
    public static boolean removerUsuarioPorMatricula(String unidade, String matricula) {
        if (unidade == null || matricula == null) return false;
        String key = normalizeUnidade(unidade);
        String matriculaTrim = matricula.trim();
    LOGGER.log(Level.FINE, "Tentando remover matricula={0} da unidade={1}", new Object[]{matriculaTrim, key});
        Set<UsuarioConectado> usuarios = usuariosPorUnidade.get(key);
        if (usuarios != null) {
            boolean removed = false;
            Iterator<UsuarioConectado> it = usuarios.iterator();
            // Log estado antes
            LOGGER.log(Level.FINE, "Usuarios antes (count={0}) na unidade={1}: {2}", new Object[]{usuarios.size(), key, usuarios});
            List<String> removedList = new ArrayList<>();
            while (it.hasNext()) {
                UsuarioConectado u = it.next();
                String m = u.getMatricula();
                if (m != null && m.trim().equals(matriculaTrim)) {
                    it.remove();
                    removed = true;
                    removedList.add(m);
                    LOGGER.log(Level.INFO, "Removido usuario matricula={0} nome={1} na unidade={2}", new Object[]{m, u.getNome(), key});
                }
            }
            // Log estado depois
            LOGGER.log(Level.FINE, "Usuarios removidos: {0}", removedList);
            LOGGER.log(Level.FINE, "Usuarios depois (count={0}) na unidade={1}: {2}", new Object[]{usuarios.size(), key, usuarios});
            if (usuarios.isEmpty()) {
                usuariosPorUnidade.remove(key);
                LOGGER.log(Level.FINE, "Unidade {0} ficou vazia e foi removida do mapa", key);
            }
            LOGGER.log(Level.FINE, "Resultado da remoÃ§Ã£o: {0}", removed);
            return removed;
        }
        LOGGER.log(Level.FINE, "Nenhum usuÃ¡rio encontrado para unidade {0}", key);
        return false;
    }

    // Adiciona usuÃ¡rio Ã  unidade
    public static void adicionarUsuario(String unidade, String nome, String matricula, String cargo, String especialidade, String foto) {
        adicionarUsuario(unidade, nome, matricula, cargo, especialidade, foto, false, null);
    }

    public static void adicionarUsuario(String unidade, String nome, String matricula, String cargo, String especialidade, String foto, Boolean chatprofissionais) {
        adicionarUsuario(unidade, nome, matricula, cargo, especialidade, foto, chatprofissionais, null);
    }

    public static void adicionarUsuario(String unidade, String nome, String matricula, String cargo, String especialidade, String foto, Boolean chatprofissionais, String sala) {
        String key = normalizeUnidade(unidade);
        Set<UsuarioConectado> usuarios = usuariosPorUnidade.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        String nomeTrim = nome != null ? nome.trim() : null;
        String matriculaTrim = matricula != null ? matricula.trim() : null;
        UsuarioConectado novo = new UsuarioConectado(nomeTrim, matriculaTrim, cargo, especialidade, foto, chatprofissionais, sala);
        LOGGER.log(Level.FINE, "Adicionando/atualizando usuario matricula={0} nome={1} na unidade={2}", new Object[]{matriculaTrim, nomeTrim, key});
        if (usuarios.contains(novo)) {
            // Atualiza timestamp se jÃ¡ existe
            for (UsuarioConectado u : usuarios) {
                if (u.equals(novo)) {
                    u.atualizarAtividade(chatprofissionais, sala);
                    return;
                }
            }
        }
        usuarios.add(novo);    // Adiciona apenas uma vez
    }

    // RemoÃ§Ã£o automÃ¡tica de usuÃ¡rios inativos desabilitada. UsuÃ¡rios sÃ³ serÃ£o removidos apÃ³s logoff.

    // Remove usuÃ¡rio da unidade
    public static void removerUsuario(String unidade, String nome) {
        if (unidade == null || nome == null) return;
        String key = normalizeUnidade(unidade);
        Set<UsuarioConectado> usuarios = usuariosPorUnidade.get(key);
        if (usuarios != null) {
            String nomeTrim = nome.trim();
            usuarios.removeIf(u -> u.getNome() != null && u.getNome().trim().equals(nomeTrim));
            if (usuarios.isEmpty()) {
                usuariosPorUnidade.remove(key);
            }
        }
    }

    // Lista todos os usuÃ¡rios conectados na unidade
    public static Set<UsuarioConectado> listarUsuarios(String unidade) {
        return listarUsuarios(unidade, false);
    }

    public static Set<UsuarioConectado> listarUsuarios(String unidade, boolean apenasChat) {
        removeInactiveUsers(System.currentTimeMillis() - ONLINE_STALE_MS);
        String key = normalizeUnidade(unidade);
        Set<UsuarioConectado> usuarios = usuariosPorUnidade.get(key);
        if (usuarios == null) return Collections.emptySet();
        if (apenasChat) {
            Set<UsuarioConectado> filtrados = new HashSet<>();
            for (UsuarioConectado usuario : usuarios) {
                if (Boolean.TRUE.equals(usuario.getChatprofissionais())) {
                    filtrados.add(usuario);
                }
            }
            return Collections.unmodifiableSet(filtrados);
        }
        // Retornar uma copia para evitar alteracoes externas na colecao interna
        return Collections.unmodifiableSet(new HashSet<>(usuarios));
    }

    /**
     * Remove usuÃ¡rios inativos que nÃ£o enviaram heartbeat hÃ¡ mais tempo que o cutoff especificado.
     * @param cutoffTimeMillis Timestamp em milissegundos. UsuÃ¡rios com ultimaAtividade anterior a este valor serÃ£o removidos.
     * @return NÃºmero de usuÃ¡rios removidos.
     */
    public static int removeInactiveUsers(long cutoffTimeMillis) {
        int totalRemoved = 0;
        List<String> unidadesVazias = new ArrayList<>();

        LOGGER.log(Level.FINE, "Iniciando remoÃ§Ã£o de usuÃ¡rios inativos. Cutoff: {0}", cutoffTimeMillis);

        // Itera sobre todas as unidades
        for (Map.Entry<String, Set<UsuarioConectado>> entry : usuariosPorUnidade.entrySet()) {
            String unidade = entry.getKey();
            Set<UsuarioConectado> usuarios = entry.getValue();
            
            if (usuarios != null && !usuarios.isEmpty()) {
                // Remove usuÃ¡rios inativos desta unidade
                Iterator<UsuarioConectado> it = usuarios.iterator();
                while (it.hasNext()) {
                    UsuarioConectado usuario = it.next();
                    if (usuario.getUltimaAtividade() < cutoffTimeMillis) {
                        LOGGER.log(Level.INFO, 
                            "Removendo usuÃ¡rio inativo: matricula={0}, nome={1}, unidade={2}, ultimaAtividade={3}", 
                            new Object[]{usuario.getMatricula(), usuario.getNome(), unidade, usuario.getUltimaAtividade()});
                        it.remove();
                        totalRemoved++;
                    }
                }
                
                // Marca unidades que ficaram vazias para remoÃ§Ã£o
                if (usuarios.isEmpty()) {
                    unidadesVazias.add(unidade);
                }
            }
        }

        // Remove unidades vazias do mapa
        for (String unidade : unidadesVazias) {
            usuariosPorUnidade.remove(unidade);
            LOGGER.log(Level.FINE, "Unidade {0} removida (ficou vazia apÃ³s limpeza)", unidade);
        }

        LOGGER.log(Level.FINE, "Total de usuÃ¡rios inativos removidos: {0}", totalRemoved);
        return totalRemoved;
    }

    // Lista todas as unidades e seus usuÃ¡rios conectados
    public static Map<String, Set<UsuarioConectado>> listarTodasUnidades() {
        return Collections.unmodifiableMap(usuariosPorUnidade);
    }
}

