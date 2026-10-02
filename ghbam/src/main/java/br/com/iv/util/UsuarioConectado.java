package br.com.iv.util;
import java.util.Objects;

public class UsuarioConectado {
    private String nome;
    private String matricula;
    private String cargo;
    private String especialidade;
    private String foto;
    private Boolean chatprofissionais;
    private String sala;
    private long ultimaAtividade;
    private final long conectadoDesde;

    public UsuarioConectado(String nome, String matricula, String cargo, String especialidade, String foto) {
        this(nome, matricula, cargo, especialidade, foto, false, null);
    }

    public UsuarioConectado(String nome, String matricula, String cargo, String especialidade, String foto, Boolean chatprofissionais) {
        this(nome, matricula, cargo, especialidade, foto, chatprofissionais, null);
    }

    public UsuarioConectado(String nome, String matricula, String cargo, String especialidade, String foto, Boolean chatprofissionais, String sala) {
        this.nome = nome;
        this.matricula = matricula;
        this.cargo = cargo;
        this.especialidade = especialidade;
        this.foto = foto;
        this.chatprofissionais = Boolean.TRUE.equals(chatprofissionais);
        this.sala = sala;
        this.ultimaAtividade = System.currentTimeMillis();
        this.conectadoDesde = this.ultimaAtividade;
    }

    public void atualizarAtividade() {
        this.ultimaAtividade = System.currentTimeMillis();
    }

    public void atualizarAtividade(Boolean chatprofissionais, String sala) {
        this.ultimaAtividade = System.currentTimeMillis();
        if (chatprofissionais != null) {
            this.chatprofissionais = Boolean.TRUE.equals(chatprofissionais);
        }
        if (sala != null && !sala.isEmpty()) {
            this.sala = sala;
        }
    }

    public long getUltimaAtividade() {
        return ultimaAtividade;
    }

    public long getTimestamp() {
        return ultimaAtividade;
    }

    public long getUltimoHeartbeat() {
        return ultimaAtividade;
    }

    public long getLastSeen() {
        return ultimaAtividade;
    }

    // Momento em que este usuario foi visto pela primeira vez conectado nesta
    // sessao (primeiro heartbeat apos o login), diferente de ultimaAtividade
    // que e reescrito a cada heartbeat. E o que telas como "Usuarios da
    // Unidade" usam pra saber ha quanto tempo a pessoa esta online de verdade.
    public long getLoginTime() {
        return conectadoDesde;
    }

    public String getNome() {
        return nome;
    }

    public String getMatricula() {
        return matricula;
    }

    public String getCargo() {
        return cargo;
    }


    public String getEspecialidade() {
        return especialidade;
    }

    public String getFoto() {
        return foto;
    }

    public Boolean getChatprofissionais() {
        return chatprofissionais;
    }

    public Boolean getPchatprofissionais() {
        return chatprofissionais;
    }

    public String getSala() {
        return sala;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsuarioConectado that = (UsuarioConectado) o;
        return Objects.equals(matricula, that.matricula);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matricula);
    }
}
