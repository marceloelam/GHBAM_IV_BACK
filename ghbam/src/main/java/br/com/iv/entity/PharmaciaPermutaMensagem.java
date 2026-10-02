package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Canal de comunicacao entre a unidade solicitante e a unidade que recebeu o
// pedido de permuta — thread simples (sem edicao/exclusao) presa a uma
// PharmaciaPermuta especifica. Fica bloqueada para novas mensagens quando a
// permuta e finalizada ou recusada (ver PharmaciaPermutaService.enviarMensagem).
@Entity
@Table(name = "pharmacia_permuta_mensagem")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PharmaciaPermutaMensagem extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permuta_id", nullable = false)
    @JsonIgnoreProperties({"itens", "hibernateLazyInitializer", "handler"})
    private PharmaciaPermuta permuta;

    // Sigla da unidade de quem enviou (origem ou destino) — usada pra alinhar
    // a mensagem a esquerda/direita no chat sem precisar recalcular no front.
    @Column(name = "unidade_sigla", nullable = false, length = 20)
    private String unidadeSigla;

    @Column(nullable = false, length = 20)
    private String autorMatricula;

    @Column(length = 120)
    private String autorNome;

    @Column(nullable = false, length = 1000)
    private String texto;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(nullable = false)
    private LocalDateTime criadoEm;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PharmaciaPermuta getPermuta() { return permuta; }
    public void setPermuta(PharmaciaPermuta permuta) { this.permuta = permuta; }

    public String getUnidadeSigla() { return unidadeSigla; }
    public void setUnidadeSigla(String unidadeSigla) { this.unidadeSigla = unidadeSigla; }

    public String getAutorMatricula() { return autorMatricula; }
    public void setAutorMatricula(String autorMatricula) { this.autorMatricula = autorMatricula; }

    public String getAutorNome() { return autorNome; }
    public void setAutorNome(String autorNome) { this.autorNome = autorNome; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
