package br.com.iv.iaauditoria;

import io.quarkus.arc.Arc;
import jakarta.enterprise.context.RequestScoped;

/**
 * Dados clinicos da chamada que so o servico chamador conhece (origem, paciente,
 * pergunta do profissional). Opcional: sem ele a auditoria grava origem pela classe
 * chamadora e deixa paciente/pergunta nulos.
 */
@RequestScoped
public class IaAuditoriaContexto {
    String origem;
    Integer pacienteId;
    String bam;
    String pergunta;

    /**
     * Ponto de uso pelos servicos. Seguro fora de requisicao HTTP (stream/SSE, jobs):
     * sem request context ativo nao faz nada, em vez de lancar ContextNotActiveException
     * no meio do fluxo clinico.
     */
    public static void registrar(String origem, Integer pacienteId, Object bam, String pergunta) {
        try {
            if (Arc.container() != null && Arc.container().requestContext().isActive()) {
                Arc.container().instance(IaAuditoriaContexto.class).get().definir(origem, pacienteId, bam, pergunta);
            }
        } catch (Exception ignored) {
            // auditoria nunca interrompe a chamada clinica
        }
    }

    void definir(String origem, Integer pacienteId, Object bam, String pergunta) {
        this.origem = origem;
        this.pacienteId = pacienteId != null && pacienteId > 0 ? pacienteId : null;
        String b = bam == null ? "" : String.valueOf(bam).trim();
        this.bam = b.isEmpty() || "0".equals(b) ? null : b;
        this.pergunta = pergunta;
    }
}
