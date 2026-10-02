package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;

import org.junit.jupiter.api.Test;

import br.com.iv.entity.FhirEnvioDestino;

// Testa a logica de decisao do motor de envio FHIR sem banco/Quarkus: o
// requisito central do plano e que, sem destino configurado ou com o destino
// desativado, NENHUMA chamada de rede seja tentada - o que aqui vira uma
// funcao pura (estadoBloqueioSemDestino), testavel com objetos em memoria.
class FhirDispatchServiceTest {

    private final FhirDispatchService dispatchService = new FhirDispatchService();

    @Test
    void finalidadeDoDestinoSegueOTipoDoDocumento() {
        assertEquals("SUMARIO_ALTA", FhirDispatchService.finalidadeDestino("RESUMO_ALTA"));
        assertEquals("RESULTADO_EXAME", FhirDispatchService.finalidadeDestino(FhirDispatchService.TIPO_RESULTADO_EXAME));
    }

    @Test
    void semDestinoConfiguradoDeveBloquearComoNaoConfigurado() {
        assertEquals("NAO_CONFIGURADO", FhirDispatchService.estadoBloqueioSemDestino(null));
    }

    @Test
    void destinoExistenteMasInativoDeveBloquearComoDesativado() {
        FhirEnvioDestino destino = new FhirEnvioDestino();
        destino.setAtivo(false);

        assertEquals("DESATIVADO", FhirDispatchService.estadoBloqueioSemDestino(destino));
    }

    @Test
    void destinoAtivoNaoDeveBloquear() {
        FhirEnvioDestino destino = new FhirEnvioDestino();
        destino.setAtivo(true);

        assertNull(FhirDispatchService.estadoBloqueioSemDestino(destino));
    }

    @Test
    void backoffDeveCrescerExponencialmenteComTetoDeSessentaMinutos() {
        assertEquals(2, FhirDispatchService.calcularBackoffMinutos(1));
        assertEquals(8, FhirDispatchService.calcularBackoffMinutos(3));
        assertEquals(60, FhirDispatchService.calcularBackoffMinutos(10));
    }

    @Test
    void deveEsgotarTentativasSomenteNoLimiteConfigurado() {
        assertFalse(FhirDispatchService.esgotouTentativas(9, 10));
        assertTrue(FhirDispatchService.esgotouTentativas(10, 10));
    }

    @Test
    void autenticacaoBearerDeveAdicionarHeaderAuthorization() {
        FhirEnvioDestino destino = destinoComAutenticacao("BEARER_TOKEN", "meu-token", null);
        HttpRequest request = requestComAutenticacao(destino);

        assertEquals("Bearer meu-token", request.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void autenticacaoApiKeyHeaderDeveUsarNomeDeHeaderConfigurado() {
        FhirEnvioDestino destino = destinoComAutenticacao("API_KEY_HEADER", "chave-123", "X-Minha-Chave");
        HttpRequest request = requestComAutenticacao(destino);

        assertEquals("chave-123", request.headers().firstValue("X-Minha-Chave").orElse(null));
    }

    @Test
    void autenticacaoNenhumaNaoDeveAdicionarHeaderAuthorization() {
        FhirEnvioDestino destino = destinoComAutenticacao("NENHUMA", null, null);
        HttpRequest request = requestComAutenticacao(destino);

        assertTrue(request.headers().firstValue("Authorization").isEmpty());
    }

    @Test
    void autenticacaoRndsDeveEnviarTokenNoXAuthorizationServerECnsNoAuthorization() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost/teste"));
        FhirDispatchService.aplicarAutenticacaoRnds(builder, "tok-123", " 700000000000005 ");
        HttpRequest request = builder.build();

        assertEquals("Bearer tok-123", request.headers().firstValue("X-Authorization-Server").orElse(null));
        assertEquals("700000000000005", request.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void autenticacaoRndsSemCnsNaoDeveInventarHeaderAuthorization() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost/teste"));
        FhirDispatchService.aplicarAutenticacaoRnds(builder, "tok-123", null);

        assertTrue(builder.build().headers().firstValue("Authorization").isEmpty());
    }

    private FhirEnvioDestino destinoComAutenticacao(String tipo, String credencial, String headerNome) {
        FhirEnvioDestino destino = new FhirEnvioDestino();
        destino.setTipoAutenticacao(tipo);
        destino.setApiKeyHeaderNome(headerNome);
        // aplicarAutenticacao le a credencial decifrada via FhirEnvioDestinoService,
        // que exige o cofre configurado; para testar so o roteamento de header,
        // fazemos um subtipo minimo que devolve a credencial diretamente.
        FhirEnvioDestinoService destinoServiceFake = new FhirEnvioDestinoService() {
            @Override
            public String credencialDecifrada(FhirEnvioDestino d) {
                return credencial;
            }
        };
        injetarDestinoService(destinoServiceFake);
        return destino;
    }

    private void injetarDestinoService(FhirEnvioDestinoService fake) {
        try {
            var campo = FhirDispatchService.class.getDeclaredField("destinoService");
            campo.setAccessible(true);
            campo.set(dispatchService, fake);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpRequest requestComAutenticacao(FhirEnvioDestino destino) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost/teste"));
        dispatchService.aplicarAutenticacao(builder, destino);
        return builder.build();
    }
}
