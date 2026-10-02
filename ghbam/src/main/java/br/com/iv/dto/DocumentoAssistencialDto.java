package br.com.iv.dto;
import com.fasterxml.jackson.databind.JsonNode; import lombok.Data;
@Data public class DocumentoAssistencialDto { public String uuid,tipo,status,prontuario,nomepaciente,titulo; public Integer idpaciente,idbam; public JsonNode conteudo; }
