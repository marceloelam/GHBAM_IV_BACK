package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jboss.logging.Logger;

import io.quarkus.hibernate.orm.panache.Panache;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.VisitantesDto;
import br.com.iv.util.DateUtils;
import br.com.iv.entity.Visitantes;
import br.com.iv.repository.VisitantesRepository;

@ApplicationScoped
public class VisitantesService implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final int LIMITE_VISITANTES_POR_DIA = 4;
	private static final DateTimeFormatter FORMATO_DATA_VISITA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final Logger LOG = Logger.getLogger(VisitantesService.class);
	private static final Map<String, String> EXTENSOES = Map.of("image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");

	@Inject
	VisitantesRepository visitantesRepository;

	@Inject
	ArmazenamentoArquivoService armazenamento;

	public List<Visitantes> filtrarPelaUnidadeEDataVisita(String unidade, LocalDate data1, LocalDate data2) {
		List<Visitantes> visitantesOp = visitantesRepository.filtrarVisitantesPelaUnidadeEData(unidade, data1, data2);
		if (visitantesOp.isEmpty()) {
			System.out.println("Visitante(s) não encontrado(s)...");
		}
		return preencherFotos(visitantesOp);
	}

	public List<Visitantes> filtrarPelobamVisita(String bam) {
		List<Visitantes> visitantesOp = visitantesRepository.filtarPorBam(bam);
		if (visitantesOp.isEmpty()) {
			System.out.println("Visitante(s) não encontrado(s)...");
		}
		return preencherFotos(visitantesOp);
	}
	
	// Salvar
	@Transactional
	public Visitantes salvarLog(VisitantesDto dto) {
		if (dto == null || dto.getNbam() == null || dto.getNbam().isBlank()) {
			throw new IllegalArgumentException("O BAM do paciente é obrigatório.");
		}

		String dataVisita = LocalDate.now().format(FORMATO_DATA_VISITA);
		if (visitantesRepository.contarPorBamEData(dto.getNbam(), dataVisita) >= LIMITE_VISITANTES_POR_DIA) {
			throw new IllegalStateException(
					"Limite diário de 4 visitantes atingido para este paciente.");
		}

		Visitantes visitantes = new Visitantes();
		
		visitantes.setNomeVisitante(dto.getNomeVisitante());
		visitantes.setCpfVisitante(dto.getCpfVisitante());
		visitantes.setIdentidadeVisitante(dto.getIdentidadeVisitante());
		visitantes.setDataVisita(dataVisita);
		visitantes.setHoraVisitante(dto.getHoraVisitante());
		visitantes.setNascimentoVisitante(DateUtils.toYMD(dto.getNascimentoVisitante()));
		visitantes.setNbam(dto.getNbam());
		visitantes.setObsvisitante(dto.getObsvisitante());
		visitantes.setEndereco(dto.getEndereco());
		visitantes.setTelefone(dto.getTelefone());
		visitantes.setFotovisitante(dto.getFotovisitante());
		visitantes.setFotovisitantearquivo(dto.getFotovisitantearquivo());
		visitantes.setNunidade(dto.getNunidade());

		
		visitantes.persist();
		guardarFoto(visitantes);
		return visitantes;
	}

	/**
	 * Foto no MinIO organizada por unidade, paciente e dia:
	 * visitantes/<SIGLA>/<NOME>_pac-<id>/<aaaa-MM-dd>/visita-<id>-<uuid>.jpg.
	 * O nome do visitante nao entra no caminho. Se o MinIO falhar, fica no banco.
	 */
	private void guardarFoto(Visitantes v) {
		String foto = v.getFotovisitante();
		if (!armazenamento.habilitado() || foto == null || foto.isBlank()) return;
		try {
			String mime = "image/jpeg";
			String base64 = foto;
			if (foto.startsWith("data:")) {
				int virgula = foto.indexOf(',');
				int pontoVirgula = foto.indexOf(';');
				if (virgula < 0) return;
				if (pontoVirgula > 5 && pontoVirgula < virgula) mime = foto.substring(5, pontoVirgula);
				base64 = foto.substring(virgula + 1);
			}
			if (!EXTENSOES.containsKey(mime)) return;
			byte[] bytes = Base64.getMimeDecoder().decode(base64);
			int bam;
			try {
				bam = Integer.parseInt(v.getNbam().trim());
			} catch (NumberFormatException e) {
				bam = 0;
			}
			String chave = "visitantes/" + ArmazenamentoArquivoService.pastaUnidade(v.getNunidade()) + "/"
					+ armazenamento.pastaPacienteDoBam(bam) + LocalDate.now() + "/visita-" + v.getIdvisitante() + "-"
					+ UUID.randomUUID() + EXTENSOES.get(mime);
			v.setArmazenamentoChave(armazenamento.salvar(chave, bytes, mime));
			// Vazio, nao null: a tela testa fotovisitante === '' e chama startsWith.
			v.setFotovisitante("");
		} catch (RuntimeException falha) {
			LOG.warnf("Visitantes: MinIO indisponivel, foto da visita %d gravada no banco: %s", v.getIdvisitante(), falha.getMessage());
		}
	}

	/** Recoloca a foto do MinIO em fotovisitante (data URL), sem regravar no banco. */
	private List<Visitantes> preencherFotos(List<Visitantes> lista) {
		for (Visitantes v : lista) {
			String chave = v.getArmazenamentoChave();
			if (chave == null || chave.isBlank()) continue;
			Panache.getEntityManager().detach(v);
			String mime = EXTENSOES.entrySet().stream().filter(e -> chave.endsWith(e.getValue()))
					.map(Map.Entry::getKey).findFirst().orElse("image/jpeg");
			try {
				v.setFotovisitante("data:" + mime + ";base64," + Base64.getEncoder().encodeToString(armazenamento.ler(chave)));
			} catch (RuntimeException falha) {
				v.setFotovisitante("");
				LOG.errorf("Visitantes: falha ao ler foto da visita %d do MinIO: %s", v.getIdvisitante(), falha.getMessage());
			}
		}
		return lista;
	}

}
