package br.com.iv.entity;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity
@Table(name = "pharmacia_operacao_documento")
public class PharmaciaOperacaoDocumento {
    @Id @Column(name = "operacao_id") public Long operacaoId;
    @JsonIgnore @Column(name = "conteudo_base64", nullable = false, columnDefinition = "text") public String conteudoBase64;
}
