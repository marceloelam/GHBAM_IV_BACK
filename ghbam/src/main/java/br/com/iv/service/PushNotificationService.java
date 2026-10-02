package br.com.iv.service;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import br.com.iv.entity.PushDispositivo;
import br.gov.secure.AppSecrets;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@ApplicationScoped
public class PushNotificationService {

    static {
        // A lib nl.martijndwars:web-push exige o provider "BC" (BouncyCastle)
        // registrado no java.security.Security - ter a dependencia no
        // classpath nao basta, senao o PushService lanca
        // NoSuchProviderException ao inicializar.
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(PushNotificationService.class.getName());

    private PushService pushService;

    @Inject
    public PushNotificationService() {
        try {
            this.pushService = new PushService(
                AppSecrets.VAPID_PUBLIC_KEY,
                AppSecrets.VAPID_PRIVATE_KEY,
                AppSecrets.VAPID_SUBJECT
            );
        } catch (GeneralSecurityException e) {
            LOGGER.log(Level.SEVERE, "Erro ao inicializar PushService com VAPID keys", e);
            throw new RuntimeException("Falha ao configurar Web Push", e);
        }
    }

    @Transactional
    public String enviarNotificacao(PushDispositivo dispositivo, String titulo, String corpo, Map<String, Object> dados) {
        if (dispositivo == null || !Boolean.TRUE.equals(dispositivo.getAtivo())) {
            return "DISPOSITIVO_INATIVO";
        }

        try {
            Map<String, Object> payloadData = new HashMap<>();
            payloadData.put("title", titulo);
            payloadData.put("body", corpo);
            if (dados != null) {
                payloadData.putAll(dados);
            }

            String payload = convertMapToJson(payloadData);

            Notification notification = new Notification(
                dispositivo.getEndpoint(),
                dispositivo.getP256dh(),
                dispositivo.getAuthKey(),
                payload
            );

            // aesgcm (padrao da lib) usa um formato de Crypto-Key que o FCM
            // rejeita hoje ("crypto-key header had invalid format"); aes128gcm
            // e' o esquema atual exigido pelo FCM/navegadores modernos.
            org.apache.http.HttpResponse httpResponse = pushService.send(notification, Encoding.AES128GCM);
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            String corpoResposta = httpResponse.getEntity() != null
                ? org.apache.http.util.EntityUtils.toString(httpResponse.getEntity())
                : "(sem corpo)";

            // A biblioteca devolve a resposta HTTP normalmente, inclusive para
            // subscriptions expiradas. A falha de entrega nunca deve remover
            // nem desativar o vínculo do celular: o pareamento é persistente e
            // pode ser renovado pelo próprio aparelho.
            if (statusCode == 404 || statusCode == 410) {
                LOGGER.log(Level.INFO, "Subscription expirada; mantendo dispositivo pareado: {0}",
                    dispositivo.getId());
                return "INSCRICAO_EXPIRADA_HTTP_" + statusCode;
            }
            if (statusCode >= 400) {
                LOGGER.warning("[Push] Falha ao enviar para dispositivo " + dispositivo.getId()
                    + ": HTTP " + statusCode + " - corpo: " + corpoResposta);
                return "FALHA_HTTP_" + statusCode;
            }
            LOGGER.info("[Push] Envio para dispositivo " + dispositivo.getId() + " respondeu HTTP " + statusCode
                + " - corpo: " + corpoResposta);
            dispositivo.setUltimoUso(java.time.LocalDateTime.now());
            dispositivo.persist();
            return "ENVIADO_HTTP_" + statusCode;

        } catch (Exception e) {
            // HTTP 410 = subscription expirada/invalida
            if (e.getMessage() != null && (e.getMessage().contains("404") || e.getMessage().contains("410"))) {
                LOGGER.log(Level.INFO, "Subscription expirada; mantendo dispositivo pareado: {0}",
                    dispositivo.getId());
            } else {
                LOGGER.log(Level.WARNING, "Erro ao enviar notificacao push para dispositivo {0}: {1}",
                    new Object[]{dispositivo.getId(), e.getMessage()});
            }
            return "ERRO_ENVIO";
        }
    }

    private String convertMapToJson(Map<String, Object> map) {
        // Simples conversao de Map para JSON string (sem lib externa)
        StringBuilder json = new StringBuilder("{");
        map.forEach((key, value) -> {
            if (json.length() > 1) json.append(",");
            json.append("\"").append(key).append("\":");
            if (value instanceof String) {
                json.append("\"").append(escape((String) value)).append("\"");
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Boolean) {
                json.append(value);
            } else if (value == null) {
                json.append("null");
            } else {
                json.append("\"").append(escape(value.toString())).append("\"");
            }
        });
        json.append("}");
        return json.toString();
    }

    private String escape(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
