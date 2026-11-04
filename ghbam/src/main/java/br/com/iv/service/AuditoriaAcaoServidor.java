package br.com.iv.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import br.com.iv.dto.LogDto;
import br.com.iv.security.AuditPacienteResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Efeitos que o servidor faz sozinho dentro de uma acao do usuario (ex.: gerar o pedido do setor ao salvar a
 * prescricao estruturada). O AuditRequestFilter ja registra a requisicao; isto registra a consequencia, no
 * mesmo formato AUDITORIA_LGPD da auditoria clinica do frontend. O LogService anexa o usuario da requisicao.
 */
@ApplicationScoped
public class AuditoriaAcaoServidor {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    @Inject
    LogService logService;

    public void registrar(String acao, String modulo, String recurso, int idbam, Object iddocumento, String detalhes, String unidade) {
        try {
            LogDto dto = new LogDto();
            dto.setData(LocalDate.now());
            dto.setHora(LocalTime.now().format(HORA));
            dto.setEvento(String.join(" ",
                    "AUDITORIA_LGPD", "tipo=CLINICO", "acao=" + acao, "modulo=" + modulo, "recurso=" + recurso,
                    "resultado=SUCESSO", "origem=SERVIDOR",
                    idbam > 0 ? "idbam=" + idbam : "",
                    iddocumento != null ? "iddocumento=" + iddocumento : "",
                    detalhes == null || detalhes.isBlank() ? "" : "detalhes=" + limitar(detalhes, 320)).trim().replaceAll("\\s+", " "));
            dto.setUnidade(unidade == null ? null : limitar(unidade, 20));
            if (idbam > 0) {
                dto.setPacienteTipo(AuditPacienteResolver.TIPO_BAM);
                dto.setPacienteValor(String.valueOf(idbam));
            }
            logService.salvarLogAsync(dto);
        } catch (RuntimeException e) {
            // Falha de log nao pode desfazer a acao clinica ja feita.
        }
    }

    private static String limitar(String valor, int max) {
        String v = valor == null ? "" : valor.replaceAll("[\\r\\n]+", " ").trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
