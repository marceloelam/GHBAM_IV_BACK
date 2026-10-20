package br.com.iv.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ConfiguracaoUnidadeJsonTest {

    @Test
    void secretsAreWriteOnlyInJson() throws Exception {
        ConfiguracaoUnidade unidade = new ConfiguracaoUnidade();
        unidade.setPwdConexao("segredo-db");
        unidade.setTokenimagempacs("token-imagem");
        unidade.setTokenlaudopacs("token-laudo");

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(unidade);

        assertFalse(json.contains("pwdConexao"));
        assertFalse(json.contains("tokenimagempacs"));
        assertFalse(json.contains("tokenlaudopacs"));
        assertFalse(json.contains("segredo-db"));
        assertFalse(json.contains("token-imagem"));
        assertFalse(json.contains("token-laudo"));

        ConfiguracaoUnidade recebida = mapper.readValue(
                "{\"pwdConexao\":\"nova-senha\",\"tokenimagempacs\":\"novo-token\"}",
                ConfiguracaoUnidade.class);
        assertEquals("nova-senha", recebida.getPwdConexao());
        assertEquals("novo-token", recebida.getTokenimagempacs());
    }
}