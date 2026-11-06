package br.com.iv.service;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.dto.TemaVisualDto;
import br.com.iv.entity.TemaVisual;
import br.com.iv.repository.TemaVisualRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TemaVisualService implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final TypeReference<Map<String, String>> MAPA_PAPEIS = new TypeReference<>() {};

    @Inject
    TemaVisualRepository temaVisualRepository;

    @Inject
    ObjectMapper objectMapper;

    public Optional<TemaVisualDto> buscarPorUnidade(String unidade) {
        String unidadeNormalizada = temaVisualRepository.normalizarUnidade(unidade);
        return temaVisualRepository.buscarPorUnidade(unidadeNormalizada).map(this::paraDto);
    }

    // Tela de login: ainda nao ha sessao. Unidade conhecida usa so o proprio tema
    // (sem tema = padrao, nunca a cor de outra unidade). So o computador que nunca
    // entrou, sem unidade conhecida (GLOBAL), recebe o ultimo tema salvo na instalacao.
    // Rota publica: devolve so as cores, sem identificar quem salvou.
    public Optional<TemaVisualDto> buscarParaLogin(String unidade) {
        String unidadeNormalizada = temaVisualRepository.normalizarUnidade(unidade);
        Optional<TemaVisual> encontrado = temaVisualRepository.buscarPorUnidade(unidadeNormalizada);
        if (encontrado.isEmpty() && "GLOBAL".equals(unidadeNormalizada)) {
            encontrado = temaVisualRepository.buscarMaisRecente();
        }
        return encontrado
                .map(tema -> {
                    TemaVisualDto dto = paraDto(tema);
                    dto.setIdTemaVisual(null);
                    dto.setUsuario(null);
                    return dto;
                });
    }

    @Transactional
    public TemaVisualDto salvar(TemaVisualDto dto) {
        validar(dto);
        String unidade = temaVisualRepository.normalizarUnidade(dto.getUnidade());
        TemaVisual tema = temaVisualRepository.buscarPorUnidade(unidade).orElseGet(TemaVisual::new);

        tema.setUnidade(unidade);
        tema.setPrimaria(dto.getPrimaria().toUpperCase());
        tema.setSecundaria(dto.getSecundaria().toUpperCase());
        tema.setGradiente(dto.getGradiente() == null || dto.getGradiente());
        tema.setPapeisJson(paraJson(dto.getPapeis()));
        tema.setUsuario(dto.getUsuario());

        if (tema.getIdTemaVisual() == null) {
            tema.persist();
        }

        TemaVisualDto salvo = paraDto(tema);
        salvo.setPersistidoNoBackend(true);
        return salvo;
    }

    private void validar(TemaVisualDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Tema visual nao informado.");
        }
        validarCor(dto.getPrimaria(), "primaria");
        validarCor(dto.getSecundaria(), "secundaria");
        if (dto.getPapeis() != null) {
            dto.getPapeis().forEach((chave, cor) -> {
                if (cor != null && !cor.isBlank()) {
                    validarCor(cor, "papel " + chave);
                }
            });
        }
    }

    private void validarCor(String cor, String campo) {
        if (cor == null || !HEX_COLOR.matcher(cor).matches()) {
            throw new IllegalArgumentException("Cor " + campo + " invalida. Use o formato #RRGGBB.");
        }
    }

    private TemaVisualDto paraDto(TemaVisual tema) {
        TemaVisualDto dto = new TemaVisualDto();
        dto.setIdTemaVisual(tema.getIdTemaVisual());
        dto.setUnidade(tema.getUnidade());
        dto.setPrimaria(tema.getPrimaria());
        dto.setSecundaria(tema.getSecundaria());
        dto.setGradiente(tema.getGradiente());
        dto.setPapeis(deJson(tema.getPapeisJson()));
        dto.setUsuario(tema.getUsuario());
        dto.setPersistidoNoBackend(true);
        return dto;
    }

    private String paraJson(Map<String, String> papeis) {
        try {
            return objectMapper.writeValueAsString(papeis == null ? Collections.emptyMap() : papeis);
        } catch (Exception e) {
            throw new IllegalArgumentException("Nao foi possivel serializar os papeis do tema visual.", e);
        }
    }

    private Map<String, String> deJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAPA_PAPEIS);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
