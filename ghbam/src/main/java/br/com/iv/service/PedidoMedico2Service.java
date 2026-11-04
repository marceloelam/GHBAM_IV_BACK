package br.com.iv.service;

import java.io.Serializable;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import br.com.iv.dto.PedidoMedico2Dto;
import br.com.iv.dto.PedidoMedico2xDto;
import br.com.iv.dto.PedidoMedico2yDto;
import br.com.iv.entity.PedidoMedico2;
import br.com.iv.repository.PedidoMedico2Repository;
import br.com.iv.util.GerarIdUnico;

@ApplicationScoped
public class PedidoMedico2Service implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	EntityManager entityManager;

	@Inject
	PedidoMedico2Repository pedidoMedico2Repository;

	@Inject
	AssinaturaClinicaService assinaturaClinicaService;

	@Inject
	AuditoriaAcaoServidor auditoria;

	public List<PedidoMedico2> filtarBam(int idbam) {
		List<PedidoMedico2> pedidoMedico2Op = pedidoMedico2Repository.filtarBam(idbam);
		if (pedidoMedico2Op.isEmpty()) {
			System.out.println("Pedido Medico não encontrado...");
		}
		return pedidoMedico2Op;
	}
//----------------------- COLETA------------------------------

	public List<PedidoMedico2> filtarBamColetarPs(int idbam) {
		List<PedidoMedico2> pedidoMedico2Op = pedidoMedico2Repository.filtarBamColetarPs(idbam);
		if (pedidoMedico2Op.isEmpty()) {
			System.out.println("Pedido Medico não encontrado...");
		}
		return pedidoMedico2Op;
	}

	public List<PedidoMedico2> filtarBamColetarInternados(int idbam) {
		List<PedidoMedico2> pedidoMedico2Op = pedidoMedico2Repository.filtarBamColetarInternados(idbam);
		if (pedidoMedico2Op.isEmpty()) {
			System.out.println("Pedido Medico não encontrado...");
		}
		return pedidoMedico2Op;
	}

	public List<PedidoMedico2> filtarBamColetarEmergencia(int idbam) {
		List<PedidoMedico2> pedidoMedico2Op = pedidoMedico2Repository.filtarBamColetarEmergencia(idbam);
		if (pedidoMedico2Op.isEmpty()) {
			System.out.println("Pedido Medico não encontrado...");
		}
		return pedidoMedico2Op;
	}

	public List<PedidoMedico2xDto> coletaPs(String unidade, String local) {
		List<PedidoMedico2xDto> pedidoMedico2OP = pedidoMedico2Repository.coletaPs(unidade, local);
		if (pedidoMedico2OP.isEmpty()) {
			System.out.println("Não há pacientes para coleta...");
		}
		return pedidoMedico2OP;
	}

	public List<PedidoMedico2xDto> coletaInternado(String unidade, String local) {
		List<PedidoMedico2xDto> pedidoMedico2OP = pedidoMedico2Repository.coletaInternado(unidade, local);
		if (pedidoMedico2OP.isEmpty()) {
			System.out.println("Não há pacientes internado para coleta...");
		}
		return pedidoMedico2OP;
	}

	public List<PedidoMedico2xDto> coletaEmergencia(String unidade, String local) {
		List<PedidoMedico2xDto> pedidoMedico2OP = pedidoMedico2Repository.coletaInternadoEmergencia(unidade, local);
		if (pedidoMedico2OP.isEmpty()) {
			System.out.println("Não há pacientes para coleta...");
		}
		return pedidoMedico2OP;
	}

	@Transactional
	public void alterarPs(int bam) {
		pedidoMedico2Repository.finalizarColetaPs(bam);
	}

	@Transactional
	public void alterarInternado(int bam) {
		pedidoMedico2Repository.finalizarColetaInternado(bam);
	}

	@Transactional
	public void alterarEmergencia(int bam) {
		pedidoMedico2Repository.finalizarColetaEmergencia(bam);
	}

	// ----------------------- LABORATÓRIO------------------------------

	public List<PedidoMedico2> filtarBamLabPs(int idbam) {
		List<PedidoMedico2> pedidoMedico2Op = pedidoMedico2Repository.filtarBamLabPs(idbam);
		if (pedidoMedico2Op.isEmpty()) {
			System.out.println("Pedido Medico não encontrado...");
		}
		return pedidoMedico2Op;
	}

	public List<PedidoMedico2> filtarBamLabInternados(int idbam) {
		List<PedidoMedico2> pedidoMedico2Op = pedidoMedico2Repository.filtarBamLabInternados(idbam);
		if (pedidoMedico2Op.isEmpty()) {
			System.out.println("Pedido Medico não encontrado...");
		}
		return pedidoMedico2Op;
	}

	public List<PedidoMedico2> filtarBamLabEmergencia(int idbam) {
		List<PedidoMedico2> pedidoMedico2Op = pedidoMedico2Repository.filtarBamLabEmergencia(idbam);
		if (pedidoMedico2Op.isEmpty()) {
			System.out.println("Pedido Medico não encontrado...");
		}
		return pedidoMedico2Op;
	}

	@Transactional
	public List<PedidoMedico2xDto> labPs(String unidade, String local) {
		List<PedidoMedico2xDto> pedidoMedico2OP = pedidoMedico2Repository.labPs(unidade, local);
		if (pedidoMedico2OP.isEmpty()) {
			System.out.println("Não há pacientes para coleta...");
		}
		return pedidoMedico2OP;
	}
	
	public List<PedidoMedico2xDto> pedido2Ps(String unidade, String local) {
		List<PedidoMedico2xDto> pedidoMedico2OP = pedidoMedico2Repository.pedido2Ps(unidade, local);
		if (pedidoMedico2OP.isEmpty()) {
			System.out.println("Não há pacientes para " + local + "...");
		}
		return pedidoMedico2OP;
	}

	public List<PedidoMedico2xDto> pacientesDiretoSetor(String unidade, String setor) {
		List<PedidoMedico2xDto> pacientesOP = pedidoMedico2Repository.pacientesDiretoSetor(unidade, setor);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes cadastrados direto para " + setor + "...");
		}
		return pacientesOP;
	}

	public void finalizarExameDiretoSetor(int idbam) {
		pedidoMedico2Repository.finalizarExameDiretoSetor(idbam);
	}

	/**
	 * Finaliza o paciente que veio direto da recepcao e, sendo RAIO X/TOMOGRAFIA, cria o
	 * exame em imagens com o procedimento escolhido na finalizacao: e a confirmacao do
	 * setor que libera o exame para a worklist do aparelho (worklist_orthanc_v).
	 */
	@Transactional
	public void finalizarExameDiretoSetor(int idbam, String local, String procedimento, String descricao) {
		String l = local == null ? "" : local.trim().toUpperCase(Locale.ROOT);
		// RAIO X/TOMOGRAFIA: o procedimento e obrigatorio (vira o exame da worklist); sem ele nada e finalizado.
		if (LOCAIS_IMAGEM.contains(l) && (procedimento == null || procedimento.isBlank())) {
			throw new IllegalArgumentException("Informe o procedimento realizado para finalizar o exame de " + l + ".");
		}
		pedidoMedico2Repository.finalizarExameDiretoSetor(idbam);
		if (LOCAIS_IMAGEM.contains(l)) {
			String d = descricao == null || descricao.isBlank() ? l : descricao;
			inserirImagem(idbam, l, procedimento, d, 0);
		}
	}


	
/*
	@Transactional(TxType.SUPPORTS)
	public List<PedidoMedico2> pedido2lista(PedidoMedico2yDto dto) {
	        List<PedidoMedico2> pedidoMedico2OP = pedidoMedico2Repository.filtarBamListaLocal(dto.getBam(), dto.getObs(), dto.getLocal());
	        if (pedidoMedico2OP.isEmpty()) {
	            System.out.println("Não há pacientes para coleta...");
	        }
	        return pedidoMedico2OP;
	    
	}
*/
	@Transactional(TxType.SUPPORTS)
	public List<PedidoMedico2> pedido2lista(PedidoMedico2yDto dto) {
	        List<PedidoMedico2> pedidoMedico2OP = pedidoMedico2Repository.filtarBamListaLocal(dto);
	        if (pedidoMedico2OP.isEmpty()) {
	            System.out.println("Não há pacientes para coleta...");
	        }
	        return pedidoMedico2OP;
	    
	}
	
	
		
	@Transactional
	public List<PedidoMedico2xDto> labInternado(String unidade, String local) {
		List<PedidoMedico2xDto> pedidoMedico2OP = pedidoMedico2Repository.labInternado(unidade, local);
		if (pedidoMedico2OP.isEmpty()) {
			System.out.println("Não há pacientes internado para coleta...");
		}
		return pedidoMedico2OP;
	}

	public List<PedidoMedico2xDto> labEmergencia(String unidade, String local) {
		List<PedidoMedico2xDto> pedidoMedico2OP = pedidoMedico2Repository.labInternadoEmergencia(unidade, local);
		if (pedidoMedico2OP.isEmpty()) {
			System.out.println("Não há pacientes para coleta...");
		}
		return pedidoMedico2OP;
	}

	@Transactional
	public void alterarLabPs(int bam, String user) {
		pedidoMedico2Repository.finalizarLabPs(bam, user);
	}

	@Transactional
	public void alterarLabInternado(int bam) {
		pedidoMedico2Repository.finalizarLabInternado(bam);
	}

	@Transactional
	public void alterarLabEmergencia(int bam) {
		pedidoMedico2Repository.finalizarLabEmergencia(bam);
	}
//----------------------------------------------------------------------

	// Salvar os setores
	@Transactional
	public PedidoMedico2 salvarPedidoMedico2(PedidoMedico2Dto dto) {
		PedidoMedico2 pedidoMedico2 = new PedidoMedico2();
		GerarIdUnico gu = new GerarIdUnico();

		pedidoMedico2.setPedido(dto.getPedido());
		pedidoMedico2.setLocal(dto.getLocal());
		pedidoMedico2.setReal(dto.getReal());
		pedidoMedico2.setMedico(dto.getMedico());
		pedidoMedico2.setCrm(dto.getCrm());
		pedidoMedico2.setSetor(dto.getSetor());
		pedidoMedico2.setCod(dto.getCod());
		pedidoMedico2.setBam(dto.getBam());
		pedidoMedico2.setPosicao(dto.getPosicao());
		pedidoMedico2.setEntrada(dto.getEntrada());
		pedidoMedico2.setObs(dto.getObs());
		pedidoMedico2.setObs2(dto.getObs2());
		pedidoMedico2.setObs3(dto.getObs3());
		pedidoMedico2.setArquivo(dto.getArquivo());
		pedidoMedico2.setEfetuado(dto.isEfetuado());
		pedidoMedico2.setColetar(dto.isColetar());
		pedidoMedico2.setEmergencia(dto.isEmergencia());
		pedidoMedico2.setInternado(dto.isInternado());
		pedidoMedico2.setConfirmado(dto.isConfirmado());
		pedidoMedico2.setMedicoviu(dto.isMedicoviu());
		pedidoMedico2.setEas(dto.isEas());
		pedidoMedico2.setDt(dto.getDt());
		pedidoMedico2.setHr(dto.getHr());
		pedidoMedico2.setUnidade(dto.getUnidade());
		pedidoMedico2.setLiberoucoleta(dto.getLiberoucoleta());
		pedidoMedico2.setLiberouexame(dto.getLiberouexame());
		pedidoMedico2.setRestaurou(dto.getRestaurou());
		pedidoMedico2.setIdunico(gu.gerarId());

		entityManager.persist(pedidoMedico2);
		entityManager.flush();
		registrarExameDeImagem(pedidoMedico2);

		return pedidoMedico2;
	}

	private static final java.util.Set<String> LOCAIS_IMAGEM = java.util.Set.of("RAIO X", "TOMOGRAFIA");

	/**
	 * Porta o salvarNoImagem do GHBAM antigo (JSF, AtendimentoMedicoBean): pedido de
	 * RAIO X/TOMOGRAFIA cria a linha em imagens, que e o exame do setor de imagem
	 * (idimagens = AccessionNumber das views worklist_v/worklist_orthanc_v). Sem ela o
	 * exame nao aparece na worklist dos equipamentos. Formatos iguais aos do legado:
	 * data dd/MM/yyyy e controleid yyyyMdHms sem zeros. Mesma transacao do pedido.
	 */
	void registrarExameDeImagem(PedidoMedico2 pedido) {
		String local = pedido.getLocal() == null ? "" : pedido.getLocal().trim().toUpperCase(Locale.ROOT);
		if (!LOCAIS_IMAGEM.contains(local)) {
			return;
		}
		inserirImagem(pedido.getBam(), local, pedido.getCod(), pedido.getPedido(), pedido.getPosicao());
	}

	private void inserirImagem(int bam, String local, String procedimento, String descricaoExame, int posicao) {
		java.time.LocalDateTime agora = java.time.LocalDateTime.now();
		String controle = "" + agora.getYear() + agora.getMonthValue() + agora.getDayOfMonth()
				+ agora.getHour() + agora.getMinute() + agora.getSecond();
		String descricao = descricaoExame == null ? "" : descricaoExame.trim().toUpperCase(Locale.ROOT);

		entityManager.createNativeQuery(
				"INSERT INTO imagens (idbam, idpacientes, idregistro, idtratamento, cbo, atendidopor, posicao, idunico, "
				+ "controleid, procedimento, descricao, data, entrada, local, tecnico, liberacao, obs, laudo, link) "
				+ "SELECT b.idbam, b.paciente_idpaciente, b.registro_idregistro, b.tratamento_idtratamento, "
				+ "COALESCE(LEFT((SELECT s.cbo FROM servidor s WHERE s.matricula = r.matriculaatendidopor "
				+ "AND r.matriculaatendidopor <> '' LIMIT 1), 12), ''), "
				+ "COALESCE(LEFT(r.atendidopor, 55), ''), :posicao, :idunico, :controle, :procedimento, :descricao, "
				+ ":data, :entrada, :local, '', '', '', '', '' "
				+ "FROM bam b LEFT JOIN registro r ON r.idregistro = b.registro_idregistro WHERE b.idbam = :bam")
				.setParameter("posicao", posicao)
				.setParameter("idunico", new GerarIdUnico().gerarId())
				.setParameter("controle", controle)
				.setParameter("procedimento", limitar(procedimento, 13))
				.setParameter("descricao", limitar(descricao, 95))
				.setParameter("data", agora.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
				.setParameter("entrada", agora.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
				.setParameter("local", local)
				.setParameter("bam", bam)
				.executeUpdate();
	}

	private static String limitar(String valor, int max) {
		String v = valor == null ? "" : valor.trim();
		return v.length() > max ? v.substring(0, max) : v;
	}

	@Transactional
	public void remove(int id, PedidoMedico2Dto dto) {
		Optional<PedidoMedico2> pedidomedico2OP = PedidoMedico2.findByIdOptional(id);
		if (pedidomedico2OP.isEmpty()) {
			throw new IllegalArgumentException("Pedido medico nao localizado.");
		}

		PedidoMedico2 pedidoMedico2 = pedidomedico2OP.get();
		if (pedidoMedico2.isEfetuado()) {
			throw new IllegalStateException("Pedido medico ja realizado nao pode ser excluido.");
		}

		boolean assinado = assinaturaClinicaService.bloqueadoPorDocumento(AssinaturaClinicaService.PEDIDO_MEDICO, id);
		if (assinado && !mesmoSolicitante(pedidoMedico2, dto)) {
			throw new IllegalStateException("Pedido medico assinado so pode ser excluido pelo medico solicitante enquanto nao realizado.");
		}

		suspenderItemEstruturadoDeOrigem(pedidoMedico2, dto);
		pedidoMedico2.delete();

	}

	/**
	 * Pedido gerado pela prescricao estruturada (obs3 = PRESC_ESTR:{iditem}): retirar o pedido suspende o item
	 * de origem, senao ele continuava ativo na prescricao. Item ja coberto por prescricao assinada segue a
	 * regra do botao Suspender: exige adendo, entao a retirada e recusada.
	 */
	private void suspenderItemEstruturadoDeOrigem(PedidoMedico2 pedido, PedidoMedico2Dto dto) {
		String marca = pedido.getObs3() == null ? "" : pedido.getObs3().trim();
		if (!marca.startsWith("PRESC_ESTR:")) return;
		int iditem;
		try {
			iditem = Integer.parseInt(marca.substring("PRESC_ESTR:".length()).trim());
		} catch (NumberFormatException e) {
			return;
		}
		br.com.iv.entity.PrescricaoItemEstruturado item = br.com.iv.entity.PrescricaoItemEstruturado.findById(iditem);
		if (item == null || "SUSPENSO".equalsIgnoreCase(item.getStatus())) return;

		var assinatura = assinaturaClinicaService.assinaturaAtivaPorBam(AssinaturaClinicaService.PRESCRICAO_ESTRUTURADA_BAM, item.getIdbam());
		if (assinatura != null && assinatura.getAssinadoEm() != null && item.getCriadoEm() != null
				&& !item.getCriadoEm().isAfter(assinatura.getAssinadoEm())) {
			throw new IllegalStateException("Este pedido veio de uma prescricao estruturada ja assinada. Suspenda o item pela prescricao estruturada (adendo).");
		}

		String quem = dto == null || dto.getMedico() == null ? "" : dto.getMedico().trim();
		item.setStatus("SUSPENSO");
		item.setSuspensoPor(quem);
		item.setAtualizadoPor(quem);
		item.setMotivoSuspensao("Pedido retirado em Ver pedidos medicos.");
		item.setSuspensoEm(java.time.LocalDateTime.now());
		auditoria.registrar("ALTERAR", "PRESCRICAO_ESTRUTURADA", "ITEM_PRESCRICAO_ESTRUTURADA", item.getIdbam(), iditem,
				"suspenso ao retirar o pedido " + pedido.getIdpedidomedico() + " em Ver pedidos medicos; medicamento=" + item.getMedicamento(),
				item.getUnidade());
	}

	private boolean mesmoSolicitante(PedidoMedico2 pedidoMedico2, PedidoMedico2Dto dto) {
		if (pedidoMedico2 == null || dto == null) {
			return false;
		}

		String crmPedido = normalizarComparacao(pedidoMedico2.getCrm());
		String crmAtual = normalizarComparacao(dto.getCrm());
		if (!crmPedido.isBlank() && !crmAtual.isBlank() && crmPedido.equals(crmAtual)) {
			return true;
		}

		String medicoPedido = normalizarComparacao(pedidoMedico2.getMedico());
		String medicoAtual = normalizarComparacao(dto.getMedico());
		return !medicoPedido.isBlank() && !medicoAtual.isBlank() && medicoPedido.equals(medicoAtual);
	}

	private String normalizarComparacao(String valor) {
		if (valor == null) {
			return "";
		}
		String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return semAcento.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
	}
	public List<PedidoMedico2> filtarBamExamesPs(int idbam, String local) {
		List<PedidoMedico2> pedidoMedico2Op = pedidoMedico2Repository.filtarBamExamePs(idbam, local);
		if (pedidoMedico2Op.isEmpty()) {
			System.out.println("Pedido Medico não encontrado...");
		}
		return pedidoMedico2Op;
	}
	
	@Transactional
	public void atualizarAoMedicoRespostaExames(int bam, String resposta, String local) {
		pedidoMedico2Repository.atualizarAoMedicoRespostaExames(bam, resposta, local);
	}

	public List<PedidoMedico2> filtrarBamPedidoMedico2(int idbam, String qual) {
		List<PedidoMedico2> pedidoMedico2Op = pedidoMedico2Repository.filtrarBamPedidoMedico2(idbam, qual);
		if (pedidoMedico2Op.isEmpty()) {
			System.out.println("Resultados de exames não encontrado...");
		}
		return pedidoMedico2Op;
	}
	
	
}
