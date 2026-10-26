package br.com.iv.entity;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity
@Table(name = "pharmacia_operacao_documento")
public class PharmaciaOperacaoDocumento {
    @Id @Column(name = "operacao_id") public Long operacaoId;
    // Legado/fallback: com o MinIO ligado fica nulo e o PDF esta em armazenamentoChave.
    @JsonIgnore @Column(name = "conteudo_base64", columnDefinition = "text") public String conteudoBase64;
    @JsonIgnore @Column(name = "armazenamento_chave", length = 255) public String armazenamentoChave;
}
