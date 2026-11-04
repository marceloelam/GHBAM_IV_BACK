package br.com.iv.almoxarifado;

import br.com.iv.dto.LogDto;
import br.com.iv.service.LogService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Log legivel das acoes do almoxarifado ("quem fez o que"), gravado na tabela de logs do sistema.
 * Complementa o AuditRequestFilter, que registra so o metodo e o caminho da chamada.
 * Grava na mesma transacao da acao: se a acao falhar, o log nao fica.
 */
@ApplicationScoped
public class AlmoxAuditoria {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Inject
    LogService logService;

    public void registrar(String unidade, String usuario, String texto) {
        LogDto dto = new LogDto();
        dto.setData(LocalDate.now());
        dto.setHora(LocalTime.now().format(HORA));
        dto.setEvento("ALMOXARIFADO: " + texto + (usuario == null || usuario.isBlank() ? "" : " (matricula " + usuario + ")"));
        dto.setUnidade(unidade == null ? null : unidade.length() > 20 ? unidade.substring(0, 20) : unidade);
        logService.salvarLog(dto);
    }

    /** Acumula "campo: antes -> depois" so do que mudou. */
    public static final class Diferencas {
        private final List<String> partes = new ArrayList<>();

        public Diferencas campo(String nome, Object antes, Object depois) {
            String a = fmt(antes);
            String d = fmt(depois);
            if (!Objects.equals(a, d)) partes.add(nome + ": " + (a.isEmpty() ? "-" : a) + " -> " + (d.isEmpty() ? "-" : d));
            return this;
        }

        public boolean vazio() {
            return partes.isEmpty();
        }

        @Override
        public String toString() {
            return String.join("; ", partes);
        }

        private static String fmt(Object v) {
            if (v == null) return "";
            if (v instanceof BigDecimal b) return b.stripTrailingZeros().toPlainString();
            return String.valueOf(v).trim();
        }
    }
}
