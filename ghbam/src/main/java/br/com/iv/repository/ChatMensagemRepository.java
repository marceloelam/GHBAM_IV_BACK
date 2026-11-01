package br.com.iv.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import br.com.iv.entity.ChatMensagem;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ChatMensagemRepository implements PanacheRepository<ChatMensagem> {

    // desdeId > 0: só as mensagens novas (busca incremental do frontend); 0 = mensagens do dia.

    public List<ChatMensagem> listarSala(String unidade, String sala, int limite) {
        return listarSala(unidade, sala, 0, limite);
    }

    public List<ChatMensagem> listarSala(String unidade, String sala, int desdeId, int limite) {
        return find(
                "lower(unidade) = lower(?1) and tipo = 'room' and sala = ?2 and criadoEm >= ?3 and idchatmensagem > ?4 order by criadoEm desc",
                unidade,
                sala,
                LocalDate.now().atStartOfDay(),
                desdeId
        ).page(0, limite).list();
    }

    public List<ChatMensagem> listarPrivado(String unidade, String atual, String destinatario, int limite) {
        return listarPrivado(unidade, atual, destinatario, 0, limite);
    }

    public List<ChatMensagem> listarPrivado(String unidade, String atual, String destinatario, int desdeId, int limite) {
        return find(
                "lower(unidade) = lower(?1) and tipo = 'private' and ((remetenteMatricula = ?2 and destinatarioMatricula = ?3) or (remetenteMatricula = ?3 and destinatarioMatricula = ?2)) and criadoEm >= ?4 and idchatmensagem > ?5 order by criadoEm desc",
                unidade,
                atual,
                destinatario,
                LocalDate.now().atStartOfDay(),
                desdeId
        ).page(0, limite).list();
    }

    public List<ChatMensagem> listarPrivadasRecebidas(String unidade, String matricula, int limite) {
        return find(
                "lower(unidade) = lower(?1) and tipo = 'private' and destinatarioMatricula = ?2 and criadoEm >= ?3 order by criadoEm desc",
                unidade,
                matricula,
                LocalDate.now().atStartOfDay()
        ).page(0, limite).list();
    }

    public List<ChatMensagem> listarPrivadasRecebidas(String matricula, int limite) {
        return listarPrivadasRecebidas(matricula, 0, limite);
    }

    public List<ChatMensagem> listarPrivadasRecebidas(String matricula, int desdeId, int limite) {
        return find(
                "tipo = 'private' and destinatarioMatricula = ?1 and criadoEm >= ?2 and idchatmensagem > ?3 order by criadoEm desc",
                matricula,
                LocalDate.now().atStartOfDay(),
                desdeId
        ).page(0, limite).list();
    }

    /** Urgentes da sala ainda sem ciência (para o alerta em qualquer tela). */
    public List<ChatMensagem> listarUrgentesSala(String unidade, int desdeId, int limite) {
        return find(
                "lower(unidade) = lower(?1) and tipo = 'room' and prioridade = 'URGENTE' and cienciaEm is null and criadoEm >= ?2 and idchatmensagem > ?3 order by criadoEm desc",
                unidade,
                LocalDate.now().atStartOfDay(),
                desdeId
        ).page(0, limite).list();
    }

    public int marcarVisualizada(int id, String matricula) {
        return update("visualizadaEm = ?1 where idchatmensagem = ?2 and destinatarioMatricula = ?3 and visualizadaEm is null",
                LocalDateTime.now(), id, matricula);
    }

    /** Ciência de urgente: no privado só o destinatário; na sala, qualquer um da unidade (primeiro a assumir). */
    public int registrarCiencia(int id, String matricula, String nome, String unidade) {
        return update("cienciaEm = ?1, cienciaPor = ?2 where idchatmensagem = ?3 and prioridade = 'URGENTE' and cienciaEm is null"
                        + " and ((tipo = 'private' and destinatarioMatricula = ?4) or (tipo = 'room' and lower(unidade) = lower(?5) and remetenteMatricula <> ?4))",
                LocalDateTime.now(), nome, id, matricula, unidade);
    }
}
