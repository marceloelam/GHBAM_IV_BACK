package br.com.iv.ws;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

/**
 * Tempo real do chat dos profissionais.
 * - O navegador não envia Authorization no handshake: o primeiro evento é {"event":"auth","token":...}
 *   (token fora da URL, para não ir para logs). Sem auth em 10 s, a conexão é fechada.
 * - O servidor empurra mensagens novas, leitura e ciência (publicar*); typing é repassado entre usuários.
 * - Envio assíncrono: uma conexão lenta nunca segura a requisição REST que publicou.
 */
@ServerEndpoint("/ws/chat-presence")
public class ChatPresenceEndpoint {
    private static final Map<String, Set<Session>> BY_USER = new ConcurrentHashMap<>();
    private static final Map<String, Set<Session>> BY_UNIT = new ConcurrentHashMap<>();
    private static final ExecutorService AUTH = Executors.newVirtualThreadPerTaskExecutor();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chat-ws-timer");
        t.setDaemon(true);
        return t;
    });
    private static final String IDENTITY = "identity";
    private static final String UNIT = "unit";

    @Inject ObjectMapper objectMapper;
    @Inject ChatSocketAuth chatSocketAuth;

    @OnOpen public void open(Session session) {
        session.setMaxIdleTimeout(TimeUnit.MINUTES.toMillis(2));
        TIMER.schedule(() -> {
            if (session.isOpen() && session.getUserProperties().get(IDENTITY) == null) fechar(session, "auth-timeout");
        }, 10, TimeUnit.SECONDS);
    }

    @OnClose public void close(Session session) {
        Object identity = session.getUserProperties().get(IDENTITY);
        Object unit = session.getUserProperties().get(UNIT);
        remover(BY_USER, identity, session);
        remover(BY_UNIT, unit, session);
        // Saiu de vez (nenhuma outra aba aberta): avisa a unidade para a lista de online atualizar.
        if (identity != null && !BY_USER.containsKey(String.valueOf(identity))) avisarPresenca(String.valueOf(unit), String.valueOf(identity), false);
    }

    @OnError public void error(Session session, Throwable ignored) { close(session); }

    @OnMessage public void message(String payload, Session sender) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String type = event.path("event").asText("");
            if ("auth".equals(type)) { autenticar(sender, event.path("token").asText("")); return; }
            Object identity = sender.getUserProperties().get(IDENTITY);
            if (identity == null) return;
            if ("ping".equals(type)) { sender.getAsyncRemote().sendText("{\"event\":\"pong\"}"); return; }
            // typing (pontinhos "digitando") e delivered (✓✓ cinza: chegou na tela do destinatário) são só repassados.
            if (!"typing".equals(type) && !"delivered".equals(type)) return;
            String recipient = event.path("destinatarioMatricula").asText("");
            String conversationKey = event.path("conversationKey").asText("");
            if (conversationKey.isBlank()) return;
            if ("delivered".equals(type) && recipient.isBlank()) return;
            String json = objectMapper.writeValueAsString(Map.of("event", type, "conversationKey", conversationKey,
                    "matricula", String.valueOf(identity), "nome", String.valueOf(sender.getUserProperties().getOrDefault("nome", "")),
                    "type", event.path("type").asText(""), "room", event.path("room").asText(""),
                    "digitando", event.path("digitando").asBoolean(true), "messageId", event.path("messageId").asText("")));
            if (!recipient.isBlank()) enviar(BY_USER.get(recipient), json, sender);
            else enviar(BY_UNIT.get(String.valueOf(sender.getUserProperties().get(UNIT))), json, sender);
        } catch (Exception ignored) { }
    }

    private void autenticar(Session session, String token) {
        if (session.getUserProperties().get(IDENTITY) != null) return;
        AUTH.submit(() -> {
            try {
                ChatSocketAuth.Identidade id = chatSocketAuth.autenticar(token);
                if (id == null) { fechar(session, "auth-invalida"); return; }
                session.getUserProperties().put(IDENTITY, id.matricula());
                session.getUserProperties().put("nome", id.nome() == null ? "" : id.nome());
                session.getUserProperties().put("cargo", id.cargo() == null ? "" : id.cargo());
                String unit = id.unidade() == null ? "" : id.unidade().trim().toLowerCase();
                session.getUserProperties().put(UNIT, unit);
                boolean primeiraAba = !BY_USER.containsKey(id.matricula());
                BY_USER.computeIfAbsent(id.matricula(), k -> ConcurrentHashMap.newKeySet()).add(session);
                if (!unit.isBlank()) BY_UNIT.computeIfAbsent(unit, k -> ConcurrentHashMap.newKeySet()).add(session);
                session.getAsyncRemote().sendText("{\"event\":\"ready\"}");
                if (primeiraAba) avisarPresenca(unit, id.matricula(), true);
            } catch (Exception e) {
                fechar(session, "auth-erro");
            }
        });
    }

    /**
     * Quem está conectado na unidade agora (presença real: a conexão fica aberta em qualquer tela,
     * inclusive com a aba em segundo plano — ao contrário do heartbeat, que pausa nesse caso).
     */
    public static java.util.List<Map<String, String>> conectadosNaUnidade(String unidade) {
        if (unidade == null) return java.util.List.of();
        Map<String, Map<String, String>> porMatricula = new java.util.LinkedHashMap<>();
        for (Session s : BY_UNIT.getOrDefault(unidade.trim().toLowerCase(), Set.of())) {
            if (!s.isOpen()) continue;
            Object m = s.getUserProperties().get(IDENTITY);
            if (m == null) continue;
            porMatricula.putIfAbsent(String.valueOf(m), Map.of(
                    "matricula", String.valueOf(m),
                    "nome", String.valueOf(s.getUserProperties().getOrDefault("nome", "")),
                    "cargo", String.valueOf(s.getUserProperties().getOrDefault("cargo", ""))));
        }
        return new java.util.ArrayList<>(porMatricula.values());
    }

    /**
     * Tem alguma aba aberta no chat, em qualquer unidade (mensagem privada vai por matrícula,
     * então é isso que diz se a pessoa recebe agora).
     */
    public static boolean estaConectado(String matricula) {
        if (matricula == null || matricula.isBlank()) return false;
        Set<Session> sessoes = BY_USER.get(matricula.trim());
        return sessoes != null && sessoes.stream().anyMatch(Session::isOpen);
    }

    private static void avisarPresenca(String unit, String matricula, boolean online) {
        if (unit == null || unit.isBlank() || "null".equals(unit)) return;
        enviar(BY_UNIT.get(unit), "{\"event\":\"presence\",\"matricula\":\"" + matricula.replace("\"", "") + "\",\"online\":" + online + "}", null);
    }

    /** Mensagem privada: destinatário e as outras abas do remetente. */
    public static void publicarParaUsuarios(String json, String... matriculas) {
        for (String m : matriculas) if (m != null && !m.isBlank()) enviar(BY_USER.get(m), json, null);
    }

    /** Mensagem de sala: todos os conectados na unidade (a tela filtra a sala). */
    public static void publicarParaUnidade(String unidade, String json) {
        if (unidade == null) return;
        enviar(BY_UNIT.get(unidade.trim().toLowerCase()), json, null);
    }

    private static void enviar(Set<Session> sessions, String json, Session excluir) {
        if (sessions == null) return;
        for (Session s : sessions) {
            if (s == excluir || !s.isOpen()) continue;
            try { s.getAsyncRemote().sendText(json); } catch (Exception ignored) { }
        }
    }

    private static void remover(Map<String, Set<Session>> mapa, Object chave, Session session) {
        if (chave == null) return;
        Set<Session> sessions = mapa.get(String.valueOf(chave));
        if (sessions != null) { sessions.remove(session); if (sessions.isEmpty()) mapa.remove(String.valueOf(chave), sessions); }
    }

    private static void fechar(Session session, String motivo) {
        try { session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, motivo)); } catch (Exception ignored) { }
    }
}
