package br.com.iv.ws;

import java.util.NoSuchElementException;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

import io.quarkus.logging.LoggingFilter;

/**
 * Descarta do console o "NoSuchElementException: httpDecoder" do Undertow: quando o navegador
 * fecha a conexao no meio do handshake do WebSocket (reload, troca de aba, rede), o Undertow tenta
 * remover um handler que ja saiu do pipeline. A conexao ja estava morta e o chat reconecta sozinho;
 * so essa excecao e filtrada, qualquer outro erro do Vert.x continua aparecendo.
 */
@LoggingFilter(name = "handshake-websocket-abortado")
public final class HandshakeAbortadoLogFilter implements Filter {

    @Override
    public boolean isLoggable(LogRecord record) {
        Throwable erro = record.getThrown();
        if (!(erro instanceof NoSuchElementException) || !"httpDecoder".equals(erro.getMessage())) return true;
        for (StackTraceElement linha : erro.getStackTrace()) {
            if (linha.getClassName().startsWith("io.undertow.websockets.")) return false;
        }
        return true;
    }
}
