package br.com.iv.service;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import br.com.iv.dto.AltaadmnirDto;
import br.com.iv.entity.Altaadmnir;
import br.com.iv.repository.AltaadmnirRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AltaadmnirService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	AltaadmnirRepository altaadmnirRepository;

	// Listando todos os cbo
	public List<Altaadmnir> listarTodosAltaadmnir() {
		return altaadmnirRepository.listAll(Sort.by("bam"));
	}

	@Transactional
	public List<Altaadmnir> filtarPorCBO(String bam, AltaadmnirDto dto) {

		List<Altaadmnir> altaadmnirOp = altaadmnirRepository.filtarPorAltaadmnir(bam);
		if (altaadmnirOp.isEmpty()) {
			System.out.println("Essa alta administrativa não foi encontrada...");
		}
		return altaadmnirOp;
	}
	
	
	@Transactional
	public Altaadmnir salvarAltaadmnir(int bam, String servidor) {
		Altaadmnir altaadmnir = new Altaadmnir();
		altaadmnir.setData(converteData());
		altaadmnir.setHora(converteTime());
		altaadmnir.setBam(bam);
		altaadmnir.setServidormatricula(servidor);
		altaadmnir.persist();
		return altaadmnir;
	}

	protected String converteData() {
		Date dataAtual = new Date();
		SimpleDateFormat formatador = new SimpleDateFormat("yyyy/MM/dd");
		String dataFormatada = formatador.format(dataAtual);

		return dataFormatada;
	}

	protected String converteTime() {
		Date horaAtual = new Date();
		SimpleDateFormat formatador = new SimpleDateFormat("HH:mm");
		String timeFormatada = formatador.format(horaAtual);

		return timeFormatada;
	}

}
