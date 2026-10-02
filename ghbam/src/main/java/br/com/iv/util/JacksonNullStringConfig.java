package br.com.iv.util;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

/**
 * Configura Jackson globalmente para converter null JSON → "" (string vazia)
 * em todos os campos String durante a desserialização.
 *
 * Isso evita HTTP 400 quando o frontend envia campos como biometria/fir/foto
 * com valor null (ex.: servidor sem digital cadastrada).
 */
@Singleton
public class JacksonNullStringConfig implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        // Qualquer campo String que receba null no JSON vira "" em vez de null
        mapper.configOverride(String.class)
              .setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY));
    }
}
