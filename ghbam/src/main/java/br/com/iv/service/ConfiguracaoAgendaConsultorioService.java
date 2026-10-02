package br.com.iv.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.ConfiguracaoAgendaConsultorioDto;
import br.com.iv.entity.ConfiguracaoAgendaConsultorio;
import br.com.iv.repository.ConfiguracaoAgendaConsultorioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ConfiguracaoAgendaConsultorioService {

    @Inject
    ConfiguracaoAgendaConsultorioRepository repository;

    public List<ConfiguracaoAgendaConsultorio> listarPorUnidade(String unidade, boolean apenasAtivos) {
        return repository.listarPorUnidade(unidade, apenasAtivos);
    }

    @Transactional
    public ConfiguracaoAgendaConsultorio salvar(ConfiguracaoAgendaConsultorioDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Dados da agenda ausentes");
        }
        ConfiguracaoAgendaConsultorio config = new ConfiguracaoAgendaConsultorio();
        aplicar(config, dto);
        config.setCriadoEm(LocalDateTime.now());
        config.setCriadoPor(valorOuPadrao(dto.getUsuario(), "-"));
        repository.persist(config);
        return config;
    }

    @Transactional
    public ConfiguracaoAgendaConsultorio atualizar(int id, ConfiguracaoAgendaConsultorioDto dto) {
        ConfiguracaoAgendaConsultorio config = buscar(id);
        aplicar(config, dto);
        config.setAtualizadoEm(LocalDateTime.now());
        config.setAtualizadoPor(valorOuPadrao(dto.getUsuario(), "-"));
        return config;
    }

    @Transactional
    public ConfiguracaoAgendaConsultorio alterarAtivo(int id, ConfiguracaoAgendaConsultorioDto dto) {
        ConfiguracaoAgendaConsultorio config = buscar(id);
        config.setAtivo(normalizarSimNao(dto == null ? null : dto.getAtivo(), "S"));
        config.setAtualizadoEm(LocalDateTime.now());
        config.setAtualizadoPor(valorOuPadrao(dto == null ? null : dto.getUsuario(), "-"));
        return config;
    }

    @Transactional
    public void excluir(int id) {
        ConfiguracaoAgendaConsultorio config = buscar(id);
        config.delete();
    }

    @Transactional
    public long excluirVarios(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return repository.delete("idconfiguracaoagendaconsultorio in ?1", ids);
    }

    private ConfiguracaoAgendaConsultorio buscar(int id) {
        Optional<ConfiguracaoAgendaConsultorio> config = ConfiguracaoAgendaConsultorio.findByIdOptional(id);
        if (config.isEmpty()) {
            throw new IllegalArgumentException("Configuracao de agenda nao encontrada para o id: " + id);
        }
        return config.get();
    }

    private void aplicar(ConfiguracaoAgendaConsultorio config, ConfiguracaoAgendaConsultorioDto dto) {
        config.setUnidade(valorOuPadrao(dto.getUnidade(), "-").toUpperCase());
        config.setProfissional(valorOuPadrao(dto.getProfissional(), "-").toUpperCase());
        config.setEspecialidade(valorOuPadrao(dto.getEspecialidade(), "-").toUpperCase());
        config.setSala(valorOuPadrao(dto.getSala(), "-").toUpperCase());
        config.setDiaSemana(normalizarDiaSemana(dto.getDiaSemana()));
        config.setDataAgenda(dto.getDataAgenda());
        config.setHoraInicio(hora(dto.getHoraInicio(), LocalTime.of(8, 0)));
        config.setHoraFim(hora(dto.getHoraFim(), LocalTime.of(12, 0)));
        config.setDuracaoMinutos(inteiroMinimo(dto.getDuracaoMinutos(), 30, 5));
        config.setLimiteEncaixes(inteiroMinimo(dto.getLimiteEncaixes(), 0, 0));
        config.setIntervaloRetornoDias(inteiroMinimo(dto.getIntervaloRetornoDias(), 30, 0));
        config.setPermiteEncaixe(normalizarSimNao(dto.getPermiteEncaixe(), "S"));
        config.setAtivo(normalizarSimNao(dto.getAtivo(), "S"));
        config.setObservacao(valorOuPadrao(dto.getObservacao(), "-"));
        config.setAtualizadoEm(LocalDateTime.now());
        config.setAtualizadoPor(valorOuPadrao(dto.getUsuario(), "-"));
    }

    private LocalTime hora(LocalTime valor, LocalTime padrao) {
        return valor == null ? padrao : valor;
    }

    private int inteiroMinimo(Integer valor, int padrao, int minimo) {
        int numero = valor == null ? padrao : valor;
        return Math.max(numero, minimo);
    }

    private String valorOuPadrao(String valor, String padrao) {
        return valor == null || valor.trim().isEmpty() ? padrao : valor.trim();
    }

    private String normalizarSimNao(String valor, String padrao) {
        return "N".equalsIgnoreCase(valorOuPadrao(valor, padrao)) ? "N" : "S";
    }

    private String normalizarDiaSemana(String valor) {
        String dia = valorOuPadrao(valor, "SEGUNDA").toUpperCase();
        if (dia.equals("SEGUNDA") || dia.equals("TERCA") || dia.equals("QUARTA") || dia.equals("QUINTA")
                || dia.equals("SEXTA") || dia.equals("SABADO") || dia.equals("DOMINGO")) {
            return dia;
        }
        return "SEGUNDA";
    }
}
