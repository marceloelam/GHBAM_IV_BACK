package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.iv.cdss.dto.ResultadoCriticoDto;
import br.com.iv.cdss.service.ReconciliacaoMedicamentosService;
import br.com.iv.cdss.service.ResultadoCriticoService;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Pacientes;

class JornadaPaServicePendenciasSegurancaTest {

    private Bam bam() {
        Pacientes paciente = new Pacientes();
        paciente.setIdpaciente(10);
        Bam bam = new Bam();
        bam.setIdbam(500);
        bam.setPaciente(paciente);
        return bam;
    }

    @Test
    void listaCriticoSemCienciaEReconciliacaoPendente() {
        JornadaPaService service = new JornadaPaService();
        service.resultadoCriticoService = new ResultadoCriticoService() {
            @Override
            public List<ResultadoCriticoDto> listarPorBam(int idbam) {
                return List.of(
                        new ResultadoCriticoDto(1, 2, idbam, "Potassio", "6,8", "mEq/L", "Potassio", "ALTO", 6.2, null, false, null, null, null),
                        new ResultadoCriticoDto(3, 2, idbam, "Sodio", "118", "mEq/L", "Sodio", "BAIXO", 120, null, true, null, "Dra. X", "ok"));
            }
        };
        service.reconciliacaoService = new ReconciliacaoMedicamentosService() {
            @Override
            public List<Map<String, Object>> listar(int idbam, int idpaciente) {
                Map<String, Object> pendente = new LinkedHashMap<>();
                pendente.put("medicamento", "Losartana 50mg");
                pendente.put("decisao", null);
                Map<String, Object> decidido = new LinkedHashMap<>();
                decidido.put("medicamento", "Metformina");
                decidido.put("decisao", "MANTER");
                return List.of(pendente, decidido);
            }
        };

        List<Map<String, Object>> pendencias = service.pendenciasSegurancaPaciente(bam());

        assertEquals(2, pendencias.size());
        assertTrue(String.valueOf(pendencias.get(0).get("titulo")).contains("Potassio 6,8"));
        assertTrue(String.valueOf(pendencias.get(1).get("titulo")).contains("Losartana"));
    }

    @Test
    void falhaNaConsultaNaoDerrubaConformidade() {
        JornadaPaService service = new JornadaPaService();
        service.resultadoCriticoService = new ResultadoCriticoService() {
            @Override
            public List<ResultadoCriticoDto> listarPorBam(int idbam) {
                throw new IllegalStateException("banco indisponivel");
            }
        };
        assertTrue(service.pendenciasSegurancaPaciente(bam()).isEmpty());
    }
}
