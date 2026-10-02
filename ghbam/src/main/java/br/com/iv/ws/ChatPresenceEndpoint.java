package br.com.iv.ws;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/ws/chat-presence")
public class ChatPresenceEndpoint {
    private static final Map<String, Set<Session>> BY_USER = new ConcurrentHashMap<>();
    @Inject ObjectMapper objectMapper;

    @OnOpen public void open(Session session) {
        Principal principal = session.getUserPrincipal();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) { close(session); return; }
        session.getUserProperties().put("identity", principal.getName());
        BY_USER.computeIfAbsent(principal.getName(), ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @OnClose public void close(Session session) {
        Object identity = session.getUserProperties().get("identity");
        if (identity == null) return;
        Set<Session> sessions = BY_USER.get(String.valueOf(identity));
        if (sessions != null) { sessions.remove(session); if (sessions.isEmpty()) BY_USER.remove(String.valueOf(identity), sessions); }
    }

    @OnError public void error(Session session, Throwable ignored) { close(session); }

    @OnMessage public void message(String payload, Session sender) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String type = event.path("event").asText("");
            String recipient = event.path("destinatarioMatricula").asText("");
            String conversationKey = event.path("conversationKey").asText("");
            if ((!"typing".equals(type) && !"read".equals(type)) || recipient.isBlank() || conversationKey.isBlank()) return;
            String senderIdentity = String.valueOf(sender.getUserProperties().get("identity"));
            String json = objectMapper.writeValueAsString(Map.of("event", type, "conversationKey", conversationKey,
                    "matricula", senderIdentity, "nome", event.path("nome").asText(""), "messageId", event.path("messageId").asText("")));
            for (Session target : BY_USER.getOrDefault(recipient, Set.of())) if (target.isOpen()) target.getAsyncRemote().sendText(json);
        } catch (Exception ignored) { }
    }
}
