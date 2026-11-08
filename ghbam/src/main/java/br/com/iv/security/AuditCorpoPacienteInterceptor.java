package br.com.iv.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

/**
 * Para o log dizer "para quem": muitos "salvar" mandam o paciente no corpo (idbam, idpaciente...) e nao no
 * path. Ao ler o corpo JSON para o recurso, este interceptor procura so esses campos numericos e deixa a
 * referencia na propriedade {@link #PROPRIEDADE}, que o AuditRequestFilter usa quando o path nao traz o
 * paciente. O corpo segue intacto para o recurso; nada do corpo vai para o log alem do numero.
 *
 * ReaderInterceptor (e nao ContainerRequestFilter) porque no RESTEasy Reactive o corpo so pode ser lido
 * com seguranca quando o proprio recurso o le.
 */
@Provider
@Priority(Priorities.USER)
public class AuditCorpoPacienteInterceptor implements ReaderInterceptor {

    public static final String PROPRIEDADE = "audit.pacienteCorpo";

    /** Corpo maior que isto (fotos, PDFs em base64) nao e examinado: so o recurso le. */
    static final int LIMITE_BYTES = 512 * 1024;

    // Ordem de preferencia: BAM identifica o atendimento; idpaciente/prontuario so o paciente.
    private static final String[] CAMPOS = { "idbam", "nbam", "numerobam", "bam", "idpaciente", "iddopaciente", "prontuario" };
    private static final Map<String, String> TIPOS = Map.of(
            "idbam", AuditPacienteResolver.TIPO_BAM,
            "nbam", AuditPacienteResolver.TIPO_BAM,
            "numerobam", AuditPacienteResolver.TIPO_BAM,
            "bam", AuditPacienteResolver.TIPO_BAM,
            "idpaciente", AuditPacienteResolver.TIPO_ID_PACIENTE,
            "iddopaciente", AuditPacienteResolver.TIPO_ID_PACIENTE,
            "prontuario", AuditPacienteResolver.TIPO_PRONTUARIO);
    private static final Pattern CAMPO = Pattern.compile(
            "\"(idbam|nbam|numerobam|bam|idpaciente|iddopaciente|prontuario)\"\\s*:\\s*\"?(\\d{1,12})\"?",
            Pattern.CASE_INSENSITIVE);

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
        MediaType tipo = context.getMediaType();
        if (tipo == null || !tipo.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            return context.proceed();
        }
        String tamanho = context.getHeaders() == null ? null : context.getHeaders().getFirst("Content-Length");
        if (tamanho != null) {
            try {
                if (Long.parseLong(tamanho.trim()) > LIMITE_BYTES) return context.proceed();
            } catch (NumberFormatException ignorado) {
                // sem tamanho confiavel: segue com o limite de leitura abaixo
            }
        }
        InputStream original = context.getInputStream();
        byte[] corpo = original.readNBytes(LIMITE_BYTES + 1);
        if (corpo.length > LIMITE_BYTES) {
            // Grande demais: devolve o que ja foi lido mais o restante, sem examinar.
            context.setInputStream(new SequenceInputStream(new ByteArrayInputStream(corpo), original));
            return context.proceed();
        }
        context.setInputStream(new ByteArrayInputStream(corpo));
        try {
            String[] referencia = extrair(new String(corpo, StandardCharsets.UTF_8));
            if (referencia != null) context.setProperty(PROPRIEDADE, referencia);
        } catch (RuntimeException ignorado) {
            // Auditoria nunca pode atrapalhar a requisicao.
        }
        return context.proceed();
    }

    /** {tipo, valor} do primeiro campo de paciente encontrado (pela ordem de preferencia), ou null. */
    static String[] extrair(String json) {
        if (json == null || json.isBlank()) return null;
        String[] achados = new String[CAMPOS.length];
        Matcher m = CAMPO.matcher(json);
        while (m.find()) {
            String campo = m.group(1).toLowerCase(Locale.ROOT);
            String valor = m.group(2);
            if (valor.chars().allMatch(c -> c == '0')) continue;
            for (int i = 0; i < CAMPOS.length; i++) {
                if (CAMPOS[i].equals(campo) && achados[i] == null) achados[i] = valor;
            }
        }
        for (int i = 0; i < CAMPOS.length; i++) {
            if (achados[i] != null) return new String[] { TIPOS.get(CAMPOS[i]), achados[i] };
        }
        return null;
    }
}
