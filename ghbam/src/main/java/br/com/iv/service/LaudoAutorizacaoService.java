package br.com.iv.service;

import java.io.Serializable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.LaudoAutorizacaoDto;
import br.com.iv.entity.LaudoAutorizacao;
import br.com.iv.repository.LaudoAutorizacaoRepository;

@ApplicationScoped
public class LaudoAutorizacaoService implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@Inject
	LaudoAutorizacaoRepository laudoAutorizacaoRepository;

	@Inject
	AssinaturaClinicaService assinaturaClinicaService;


	@Transactional
	public LaudoAutorizacao filtarPorBam(int bam) {
		return laudoAutorizacaoRepository.filtarPorBam(bam);
	}
	
	// Salvar
		@Transactional
		public LaudoAutorizacao salvarLaudoAutorizacao(LaudoAutorizacaoDto dto) {
			LaudoAutorizacao laudoAutorizacao = new LaudoAutorizacao();
			laudoAutorizacao.setCnes1(dto.getCnes1());
			laudoAutorizacao.setSolicitante(dto.getSolicitante()  );
			laudoAutorizacao.setExecutante(dto.getExecutante()  );
			laudoAutorizacao.setCnes2(dto.getCnes2()  );
			laudoAutorizacao.setBam(dto.getBam()  );
			laudoAutorizacao.setSintomasclinicos(dto.getSintomasclinicos()  );
			laudoAutorizacao.setJusinternacao(dto.getJusinternacao()  );
			laudoAutorizacao.setProvasdiag(dto.getProvasdiag()  );
			laudoAutorizacao.setDiagnosticoinicial(dto.getDiagnosticoinicial()  );
			laudoAutorizacao.setCidprincipal(dto.getCidprincipal()  );
			laudoAutorizacao.setCidsecundario(dto.getCidsecundario()  );
			laudoAutorizacao.setCidcausas(dto.getCidcausas()  );
			laudoAutorizacao.setProcedimentosolicitado(dto.getProcedimentosolicitado()  );
			laudoAutorizacao.setCodprocedimento(dto.getCodprocedimento()  );
			laudoAutorizacao.setClinica(dto.getClinica()  );
			laudoAutorizacao.setCaraterinternacao(dto.getCaraterinternacao()  );
			laudoAutorizacao.setDocumento1(dto.getDocumento1()  );
			laudoAutorizacao.setCpfprofissional(dto.getCpfprofissional()  );
			laudoAutorizacao.setSolicitanteassistente(dto.getSolicitanteassistente()  );
			laudoAutorizacao.setDocumento2(dto.getDocumento2()  );
			laudoAutorizacao.setCnpjsegu(dto.getCnpjsegu()  );
			laudoAutorizacao.setNbilhete(dto.getNbilhete()  );
			laudoAutorizacao.setSerie(dto.getSerie()  );
			laudoAutorizacao.setCnpjempresa(dto.getCnpjempresa()  );
			laudoAutorizacao.setCnaeempresa(dto.getCnaeempresa()  );
			laudoAutorizacao.setCbor(dto.getCbor()  );
			laudoAutorizacao.setTipoacidente(dto.getTipoacidente()  );
			laudoAutorizacao.setProfissionalautorizador(dto.getProfissionalautorizador()  );
			laudoAutorizacao.setDocumento3(dto.getDocumento3()  );
			laudoAutorizacao.setCpfprofautorizador(dto.getCpfprofautorizador()  );
			laudoAutorizacao.setNainternacao(dto.getNainternacao()  );
			laudoAutorizacao.setUnidade(dto.getUnidade()  );
			laudoAutorizacao.persist();
			encaminharBamAoNir(laudoAutorizacao.getBam());
			return laudoAutorizacao;
		}

		@Transactional
		public LaudoAutorizacao encaminharAoNir(int id) {
			LaudoAutorizacao laudoAutorizacao = laudoAutorizacaoRepository.findById((long) id);
			if (laudoAutorizacao == null) {
				throw new NotFoundException("Laudo de autorizacao nao localizado.");
			}
			encaminharBamAoNir(laudoAutorizacao.getBam());
			return laudoAutorizacao;
		}

		private void encaminharBamAoNir(int bam) {
			int tratamentosAtualizados = laudoAutorizacaoRepository.encaminharTratamentoAoNir(bam);
			if (tratamentosAtualizados != 1) {
				throw new IllegalStateException(
						"Nao foi possivel localizar um unico tratamento para o BAM " + bam + ".");
			}
			laudoAutorizacaoRepository.sinalizarRegistroEncaminhadoAoNir(bam);
		}



		// Alterar
		@Transactional
		public void atualizarPaciente(int id, LaudoAutorizacaoDto dto) {
			LaudoAutorizacao laudoAutorizacao = laudoAutorizacaoRepository.findById((long) id);
			if (laudoAutorizacao == null) {
				throw new NotFoundException("Laudo de autorizacao nao localizado.");
			}
			bloquearSeAssinado("LAUDO_AUTORIZACAO", id);
			laudoAutorizacao.setCnes1(dto.getCnes1());
			laudoAutorizacao.setSolicitante(dto.getSolicitante()  );
			laudoAutorizacao.setExecutante(dto.getExecutante()  );
			laudoAutorizacao.setCnes2(dto.getCnes2()  );
			laudoAutorizacao.setBam(dto.getBam()  );
			laudoAutorizacao.setSintomasclinicos(dto.getSintomasclinicos()  );
			laudoAutorizacao.setJusinternacao(dto.getJusinternacao()  );
			laudoAutorizacao.setProvasdiag(dto.getProvasdiag()  );
			laudoAutorizacao.setDiagnosticoinicial(dto.getDiagnosticoinicial()  );
			laudoAutorizacao.setCidprincipal(dto.getCidprincipal()  );
			laudoAutorizacao.setCidsecundario(dto.getCidsecundario()  );
			laudoAutorizacao.setCidcausas(dto.getCidcausas()  );
			laudoAutorizacao.setProcedimentosolicitado(dto.getProcedimentosolicitado()  );
			laudoAutorizacao.setCodprocedimento(dto.getCodprocedimento()  );
			laudoAutorizacao.setClinica(dto.getClinica()  );
			laudoAutorizacao.setCaraterinternacao(dto.getCaraterinternacao()  );
			laudoAutorizacao.setDocumento1(dto.getDocumento1()  );
			laudoAutorizacao.setCpfprofissional(dto.getCpfprofissional()  );
			laudoAutorizacao.setSolicitanteassistente(dto.getSolicitanteassistente()  );
			laudoAutorizacao.setDocumento2(dto.getDocumento2()  );
			laudoAutorizacao.setCnpjsegu(dto.getCnpjsegu()  );
			laudoAutorizacao.setNbilhete(dto.getNbilhete()  );
			laudoAutorizacao.setSerie(dto.getSerie()  );
			laudoAutorizacao.setCnpjempresa(dto.getCnpjempresa()  );
			laudoAutorizacao.setCnaeempresa(dto.getCnaeempresa()  );
			laudoAutorizacao.setCbor(dto.getCbor()  );
			laudoAutorizacao.setTipoacidente(dto.getTipoacidente()  );
			laudoAutorizacao.setProfissionalautorizador(dto.getProfissionalautorizador()  );
			laudoAutorizacao.setDocumento3(dto.getDocumento3()  );
			laudoAutorizacao.setCpfprofautorizador(dto.getCpfprofautorizador()  );
			laudoAutorizacao.setNainternacao(dto.getNainternacao()  );
			laudoAutorizacao.setUnidade(dto.getUnidade()  );
			laudoAutorizacao.persist();
			encaminharBamAoNir(laudoAutorizacao.getBam());
		}
	private void bloquearSeAssinado(String tipoDocumento, Integer iddocumento) {
		if (assinaturaClinicaService.bloqueadoPorDocumento(tipoDocumento, iddocumento)) {
			throw new WebApplicationException(
					"Documento ja assinado. Registre um adendo em vez de alterar o conteudo original.",
					Response.Status.CONFLICT);
		}
	}

}
