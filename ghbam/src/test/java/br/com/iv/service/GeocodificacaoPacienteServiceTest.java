package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class GeocodificacaoPacienteServiceTest {

	private static GeocodificacaoPacienteService.Pedido pedido(String endereco, String numero, String bairro, String municipio) {
		return new GeocodificacaoPacienteService.Pedido(1, endereco, numero, bairro, municipio, "RJ", false);
	}

	@Test
	void pontoPadraoDeItaguaiNaoContaComoCoordenadaReal() {
		assertFalse(GeocodificacaoPacienteService.coordenadaReal(-22.869417, -43.777349));
		assertFalse(GeocodificacaoPacienteService.coordenadaReal(0, 0));
		assertTrue(GeocodificacaoPacienteService.coordenadaReal(-22.85, -43.70));
	}

	@Test
	void tentativasVaoDaRuaAteOMunicipio() {
		List<GeocodificacaoPacienteService.Tentativa> t = GeocodificacaoPacienteService
				.montarTentativas(pedido("RUA GENERAL BOCAIUVA", "S/N 120", "CENTRO", "ITAGUAI"));
		assertEquals(List.of("endereco", "bairro", "municipio"), t.stream().map(GeocodificacaoPacienteService.Tentativa::precisao).toList());
		assertEquals("120 RUA GENERAL BOCAIUVA", t.get(0).params().get("street"));
		assertEquals("CENTRO, ITAGUAI, RJ", t.get(1).params().get("q"));
	}

	@Test
	void semMunicipioNaoConsulta() {
		assertTrue(GeocodificacaoPacienteService.montarTentativas(pedido("RUA X", "1", "CENTRO", "")).isEmpty());
	}

	@Test
	void tabelaLocalAchaBairroIgnorandoAcentoEspacoEPrefixo() {
		GeocodificacaoPacienteService.Resultado r = GeocodificacaoPacienteService.coordenadaLocal("brisamar", "Itaguaí", "RJ");
		assertEquals("bairro", r.precisao());
		assertEquals(r, GeocodificacaoPacienteService.coordenadaLocal("BAIRRO BRISA MAR", "ITAGUAI", "RJ"));
		assertEquals("bairro", GeocodificacaoPacienteService.coordenadaLocal("CHAPERÓ", "ITAGUAI", "RJ").precisao());
	}

	@Test
	void bairroDesconhecidoCaiNoMunicipioEForaDoRjNaoAcha() {
		assertEquals("municipio", GeocodificacaoPacienteService.coordenadaLocal("RUA INVENTADA", "SEROPEDICA", "RJ").precisao());
		assertNull(GeocodificacaoPacienteService.coordenadaLocal("CENTRO", "ITAGUAI", "PI"));
	}

	@Test
	void outroEstadoUsaASedeDoMunicipioPelaSiglaOuNome() {
		GeocodificacaoPacienteService.Resultado sp = GeocodificacaoPacienteService.coordenadaLocal("SE", "São Paulo", "SP");
		assertEquals("municipio", sp.precisao());
		assertEquals(-23.53, sp.latitude(), 0.05);
		assertEquals(sp, GeocodificacaoPacienteService.coordenadaLocal("SE", "SAO PAULO", "São Paulo"));
		// Mesmo nome em estados diferentes: cada um no seu lugar.
		GeocodificacaoPacienteService.Resultado pi = GeocodificacaoPacienteService.coordenadaLocal("", "BOM JESUS", "PI");
		assertEquals(-9.07, pi.latitude(), 0.05);
		assertNull(GeocodificacaoPacienteService.coordenadaLocal("", "MUNICIPIO INEXISTENTE", "MG"));
		assertNull(GeocodificacaoPacienteService.coordenadaLocal("CENTRO", "", "RJ"));
	}

	@Test
	void centroDoMunicipioNaoSobrescreveCoordenadaReal() {
		br.com.iv.entity.Pacientes p = new br.com.iv.entity.Pacientes();
		p.setBairro("XYZ");
		p.setMunicipio("ITAGUAI");
		p.setLatitude(-22.85);
		p.setLongitude(-43.70);
		GeocodificacaoPacienteService.aplicarCoordenadaLocal(p, GeocodificacaoPacienteService.PadraoUnidade.VAZIO);
		assertEquals(-22.85, p.getLatitude(), 1e-9);

		p.setLatitude(GeocodificacaoPacienteService.LATITUDE_PADRAO);
		p.setLongitude(GeocodificacaoPacienteService.LONGITUDE_PADRAO);
		GeocodificacaoPacienteService.aplicarCoordenadaLocal(p, GeocodificacaoPacienteService.PadraoUnidade.VAZIO);
		assertTrue(GeocodificacaoPacienteService.coordenadaReal(p.getLatitude(), p.getLongitude()));
	}

	@Test
	void listasDaRecepcaoEstadoMunicipioBairro() {
		List<String> rj = GeocodificacaoPacienteService.listarMunicipios("RJ");
		assertEquals(92, rj.size());
		assertTrue(rj.contains("ITAGUAÍ"));
		assertEquals(rj, GeocodificacaoPacienteService.listarMunicipios("Rio de Janeiro"));
		assertTrue(GeocodificacaoPacienteService.listarMunicipios("MG").contains("JUIZ DE FORA"));

		List<String> bairros = GeocodificacaoPacienteService.listarBairros("RJ", "Itaguaí");
		assertTrue(bairros.contains("CHAPERÓ"));
		assertTrue(bairros.contains("MONTE SERRAT")); // da lista antiga do hospital, mesmo sem coordenada
		assertTrue(GeocodificacaoPacienteService.listarBairros("SP", "SAO PAULO").isEmpty());
	}

	@Test
	void municipioVazioAssumeOMunicipioDaUnidadeEDigitadoPrevalece() {
		GeocodificacaoPacienteService.PadraoUnidade unidade = new GeocodificacaoPacienteService.PadraoUnidade("Seropédica", -22.74, -43.70);
		br.com.iv.entity.Pacientes p = new br.com.iv.entity.Pacientes();
		p.setMunicipio("");
		GeocodificacaoPacienteService.completarMunicipio(p, unidade);
		assertEquals("SEROPÉDICA", p.getMunicipio());

		p.setMunicipio("NOVA IGUACU");
		GeocodificacaoPacienteService.completarMunicipio(p, unidade);
		assertEquals("NOVA IGUACU", p.getMunicipio());
	}

	@Test
	void semBairroNemMunicipioConhecidoFicaNoPontoDaUnidade() {
		GeocodificacaoPacienteService.PadraoUnidade unidade = new GeocodificacaoPacienteService.PadraoUnidade("ITAGUAI", -22.8601, -43.7702);
		br.com.iv.entity.Pacientes p = new br.com.iv.entity.Pacientes();
		p.setBairro("XYZ");
		p.setMunicipio("CIDADE INEXISTENTE");
		p.setEstado("RJ");
		p.setLatitude(GeocodificacaoPacienteService.LATITUDE_PADRAO);
		p.setLongitude(GeocodificacaoPacienteService.LONGITUDE_PADRAO);
		GeocodificacaoPacienteService.aplicarCoordenadaLocal(p, unidade);
		assertEquals(-22.8601, p.getLatitude(), 1e-9);
		// No ponto da unidade ainda conta como "sem localizacao propria": sera recalculado depois.
		assertTrue(GeocodificacaoPacienteService.semLocalizacaoPropria(p, unidade));

		// Municipio conhecido substitui o ponto da unidade.
		p.setMunicipio("SEROPEDICA");
		GeocodificacaoPacienteService.aplicarCoordenadaLocal(p, unidade);
		assertFalse(GeocodificacaoPacienteService.semLocalizacaoPropria(p, unidade));
	}

	@Test
	void interpretaRespostaDoNominatimEDescartaForaDoBrasil() {
		GeocodificacaoPacienteService s = new GeocodificacaoPacienteService();
		s.mapper = new ObjectMapper();
		GeocodificacaoPacienteService.Resultado r = s.interpretar("[{\"lat\":\"-22.8521\",\"lon\":\"-43.7752\"}]", "endereco");
		assertEquals(-22.8521, r.latitude(), 1e-9);
		assertEquals("endereco", r.precisao());
		assertNull(s.interpretar("[{\"lat\":\"48.85\",\"lon\":\"2.35\"}]", "endereco"));
		assertNull(s.interpretar("[]", "endereco"));
		assertNull(s.interpretar("lixo", "endereco"));
	}
}
