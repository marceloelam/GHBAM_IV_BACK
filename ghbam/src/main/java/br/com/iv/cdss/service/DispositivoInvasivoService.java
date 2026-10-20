package br.com.iv.cdss.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import br.com.iv.cdss.dto.DispositivoInvasivoRequest;
import br.com.iv.cdss.entity.DispositivoInvasivo;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Dispositivos invasivos por internacao: registro de insercao e retirada pela
 * enfermagem, dias de uso no painel do plantao e dispositivos-dia para a CCIH
 * (denominador das taxas de IRAS associadas a dispositivo).
 */
@ApplicationScoped
public class DispositivoInvasivoService {
    public static final Map<String, String> TIPOS = new LinkedHashMap<>();
    static {
        TIPOS.put("SVD", "Sonda vesical de demora");
        TIPOS.put("CVC", "Cateter venoso central");
        TIPOS.put("PICC", "PICC");
        TIPOS.put("TOT", "Tubo orotraqueal / ventilacao mecanica");
        TIPOS.put("TQT", "Traqueostomia");
        TIPOS.put("CAT_ARTERIAL", "Cateter arterial");
        TIPOS.put("DRENO", "Dreno");
    }

    @Inject
    PlantaoEnfermagemService plantaoService;

    @Transactional
    public Map<String, Object> inserir(DispositivoInvasivoRequest request, AuthenticatedUser usuario) {
        PlantaoEnfermagemService.exigirProfissional(usuario);
        if (request == null || request.getIdbam() == null) {
            throw new IllegalArgumentException("Informe o leito (BAM).");
        }
        String tipo = request.getTipo() == null ? "" : request.getTipo().trim().toUpperCase(Locale.ROOT);
        if (!TIPOS.containsKey(tipo)) {
            throw new IllegalArgumentException("Tipo de dispositivo invalido.");
        }
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime inserido = dataInsercao(request.getInseridoEm(), agora);
        Integer idpaciente = plantaoService.internadoNaUnidade(request.getIdbam(), usuario.unidade());
        if (idpaciente == null) {
            throw new IllegalArgumentException("Este BAM nao esta internado na sua unidade.");
        }
        DispositivoInvasivo d = new DispositivoInvasivo();
        d.setUnidade(usuario.unidade().trim());
        d.setIdbam(request.getIdbam());
        d.setIdpaciente(idpaciente);
        d.setTipo(tipo);
        d.setSitio(request.getSitio() == null ? null : request.getSitio().trim());
        d.setInseridoEm(inserido);
        d.setInseridoPor(usuario.nome());
        d.setInseridoPorMatricula(usuario.matricula());
        d.persist();
        return mapa(d, agora);
    }

    @Transactional
    public Map<String, Object> retirar(long id, String motivo, AuthenticatedUser usuario) {
        PlantaoEnfermagemService.exigirProfissional(usuario);
        DispositivoInvasivo d = DispositivoInvasivo.findById(id);
        if (d == null || !d.getUnidade().equalsIgnoreCase(usuario.unidade().trim())) {
            throw new IllegalArgumentException("Dispositivo nao encontrado na sua unidade.");
        }
        if (d.getRetiradoEm() == null) {
            d.setRetiradoEm(LocalDateTime.now());
            d.setRetiradoPor(usuario.nome());
            d.setRetiradoPorMatricula(usuario.matricula());
            d.setMotivoRetirada(motivo == null || motivo.isBlank() ? null : motivo.trim());
        }
        return mapa(d, LocalDateTime.now());
    }

    /** Dispositivos ativos por BAM, com dias de uso, para o painel do plantao. */
    public Map<Integer, List<Map<String, Object>>> ativosPorBam(List<Integer> bams) {
        Map<Integer, List<Map<String, Object>>> porBam = new HashMap<>();
        if (bams == null || bams.isEmpty()) return porBam;
        LocalDateTime agora = LocalDateTime.now();
        for (DispositivoInvasivo d : DispositivoInvasivo.<DispositivoInvasivo>list(
                "idbam in ?1 and retiradoEm is null order by inseridoEm", bams)) {
            porBam.computeIfAbsent(d.getIdbam(), k -> new ArrayList<>()).add(mapa(d, agora));
        }
        return porBam;
    }

    /** Dispositivos-dia por tipo na unidade da sessao, no periodo [inicio, fim]. */
    public Map<String, Object> indicadores(String inicio, String fim, AuthenticatedUser usuario) {
        PlantaoEnfermagemService.exigirProfissional(usuario);
        LocalDate de;
        LocalDate ate;
        try {
            de = LocalDate.parse(inicio == null ? "" : inicio.trim());
            ate = LocalDate.parse(fim == null ? "" : fim.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Periodo invalido; use aaaa-mm-dd.");
        }
        if (ate.isBefore(de) || ChronoUnit.DAYS.between(de, ate) > 366) {
            throw new IllegalArgumentException("Periodo invalido (maximo de um ano).");
        }
        LocalDate hoje = LocalDate.now();
        Map<String, long[]> porTipo = new LinkedHashMap<>();
        TIPOS.keySet().forEach(t -> porTipo.put(t, new long[2]));
        for (DispositivoInvasivo d : DispositivoInvasivo.<DispositivoInvasivo>list(
                "unidade = ?1 and inseridoEm < ?2 and (retiradoEm is null or retiradoEm >= ?3)",
                usuario.unidade().trim(), ate.plusDays(1).atStartOfDay(), de.atStartOfDay())) {
            long[] acc = porTipo.computeIfAbsent(d.getTipo(), k -> new long[2]);
            acc[0] += diasNoPeriodo(d.getInseridoEm(), d.getRetiradoEm(), de, ate, hoje);
            LocalDate dataInsercao = d.getInseridoEm().toLocalDate();
            if (!dataInsercao.isBefore(de) && !dataInsercao.isAfter(ate)) acc[1]++;
        }
        List<Map<String, Object>> linhas = new ArrayList<>();
        porTipo.forEach((tipo, acc) -> {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("tipo", tipo);
            l.put("rotulo", TIPOS.getOrDefault(tipo, tipo));
            l.put("dispositivosDia", acc[0]);
            l.put("insercoes", acc[1]);
            linhas.add(l);
        });
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("unidade", usuario.unidade().trim());
        resposta.put("inicio", de);
        resposta.put("fim", ate);
        resposta.put("tipos", linhas);
        return resposta;
    }

    /**
     * Dias-calendario em que o dispositivo esteve presente dentro de [inicio, fim],
     * contando o dia da insercao e o da retirada (contagem diaria da CCIH). Ativo
     * conta ate hoje.
     */
    static long diasNoPeriodo(LocalDateTime inserido, LocalDateTime retirado, LocalDate inicio, LocalDate fim, LocalDate hoje) {
        if (inserido == null) return 0;
        LocalDate de = inserido.toLocalDate().isAfter(inicio) ? inserido.toLocalDate() : inicio;
        LocalDate ultimoDia = retirado == null ? hoje : retirado.toLocalDate();
        LocalDate ate = ultimoDia.isBefore(fim) ? ultimoDia : fim;
        return ate.isBefore(de) ? 0 : ChronoUnit.DAYS.between(de, ate) + 1;
    }

    static LocalDateTime dataInsercao(String valor, LocalDateTime agora) {
        if (valor == null || valor.isBlank()) return agora;
        LocalDateTime data;
        try {
            data = LocalDateTime.parse(valor.trim().length() == 16 ? valor.trim() + ":00" : valor.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data de insercao invalida.");
        }
        if (data.isAfter(agora.plusMinutes(5))) {
            throw new IllegalArgumentException("Data de insercao no futuro.");
        }
        if (data.isBefore(agora.minusDays(90))) {
            throw new IllegalArgumentException("Data de insercao com mais de 90 dias; confira.");
        }
        return data;
    }

    private static Map<String, Object> mapa(DispositivoInvasivo d, LocalDateTime agora) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("tipo", d.getTipo());
        m.put("rotulo", TIPOS.getOrDefault(d.getTipo(), d.getTipo()));
        m.put("sitio", d.getSitio());
        m.put("inseridoEm", d.getInseridoEm());
        m.put("dias", diasNoPeriodo(d.getInseridoEm(), d.getRetiradoEm(), d.getInseridoEm().toLocalDate(),
                agora.toLocalDate(), agora.toLocalDate()));
        m.put("retiradoEm", d.getRetiradoEm());
        return m;
    }
}
