package br.com.iv.cdss.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.iv.cdss.dto.CdssAvaliacaoPrescricaoRequest;
import br.com.iv.entity.Avaliacao;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Bulario;
import br.com.iv.repository.BamRepository;
import br.com.iv.repository.BularioRepository;

class CdssServiceTest {

    @Test
    void usaPesoDaTriagemQuandoRequestNaoInformaPeso() {
        CdssService service = new CdssService();
        service.rulesEngine = new CdssRulesEngine();

        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setPeso2(70d);
        Bam bam = new Bam();
        bam.setAvaliacao(avaliacao);
        service.bamRepository = new BamRepositoryStub(bam);
        service.bularioRepository = new BularioRepositoryStub();

        CdssAvaliacaoPrescricaoRequest request = new CdssAvaliacaoPrescricaoRequest();
        request.setIdbam(503971);
        request.setMedicamento("Amicacina");
        request.setDose("1,4 g");
        request.setMedicamentosAtuais(List.of("Soro fisiologico"));

        var response = service.avaliarPrescricao(request);

        assertFalse(response.getAlertas().stream().anyMatch(a -> "CDSS_PESO_OBRIGATORIO".equals(a.getCodigo())));
    }

    @Test
    void tfgCkdEpi2021() {
        org.junit.jupiter.api.Assertions.assertEquals(64.5, CdssService.ckdEpi2021(1.0, 60, true), 0.5);
        org.junit.jupiter.api.Assertions.assertEquals(91.7, CdssService.ckdEpi2021(1.0, 50, false), 0.5);
        org.junit.jupiter.api.Assertions.assertNull(CdssService.ckdEpi2021(1.0, 12, false));
        org.junit.jupiter.api.Assertions.assertNull(CdssService.feminino("ignorado"));
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, CdssService.feminino("Feminino"));
    }

    static class BamRepositoryStub extends BamRepository {
        private final Bam bam;

        BamRepositoryStub(Bam bam) {
            this.bam = bam;
        }

        @Override
        public Bam filtrarPorUnicoBam(int query) {
            return bam;
        }
    }

    static class BularioRepositoryStub extends BularioRepository {
        @Override
        public List<Bulario> filtarBulario(String ynome) {
            return List.of();
        }
    }
}
