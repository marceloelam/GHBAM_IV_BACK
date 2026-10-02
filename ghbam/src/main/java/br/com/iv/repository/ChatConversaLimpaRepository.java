package br.com.iv.repository;

import br.com.iv.entity.ChatConversaLimpa;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ChatConversaLimpaRepository implements PanacheRepository<ChatConversaLimpa> {

    public ChatConversaLimpa buscarSala(String unidade, String usuarioMatricula, String sala) {
        return find(
                "lower(unidade) = lower(?1) and tipo = 'room' and usuarioMatricula = ?2 and sala = ?3 order by atualizadoEm desc",
                unidade,
                usuarioMatricula,
                sala
        ).firstResult();
    }

    public ChatConversaLimpa buscarPrivado(String unidade, String usuarioMatricula, String outroMatricula) {
        return find(
                "lower(unidade) = lower(?1) and tipo = 'private' and usuarioMatricula = ?2 and outroMatricula = ?3 order by atualizadoEm desc",
                unidade,
                usuarioMatricula,
                outroMatricula
        ).firstResult();
    }

    public ChatConversaLimpa salvarOuAtualizarSala(String unidade, String usuarioMatricula, String sala, int ultimoIdLimpo) {
        ChatConversaLimpa registro = buscarSala(unidade, usuarioMatricula, sala);
        if (registro == null) {
            registro = new ChatConversaLimpa();
            registro.setTipo("room");
            registro.setUnidade(unidade);
            registro.setUsuarioMatricula(usuarioMatricula);
            registro.setSala(sala);
        }
        registro.setOutroMatricula("");
        registro.setUltimoIdLimpo(Math.max(valorAtual(registro), ultimoIdLimpo));
        registro.persist();
        return registro;
    }

    public ChatConversaLimpa salvarOuAtualizarPrivado(String unidade, String usuarioMatricula, String outroMatricula, int ultimoIdLimpo) {
        ChatConversaLimpa registro = buscarPrivado(unidade, usuarioMatricula, outroMatricula);
        if (registro == null) {
            registro = new ChatConversaLimpa();
            registro.setTipo("private");
            registro.setUnidade(unidade);
            registro.setUsuarioMatricula(usuarioMatricula);
            registro.setOutroMatricula(outroMatricula);
            registro.setSala("");
        }
        registro.setUltimoIdLimpo(Math.max(valorAtual(registro), ultimoIdLimpo));
        registro.persist();
        return registro;
    }

    private int valorAtual(ChatConversaLimpa registro) {
        return registro != null && registro.getUltimoIdLimpo() != null ? registro.getUltimoIdLimpo() : 0;
    }
}
