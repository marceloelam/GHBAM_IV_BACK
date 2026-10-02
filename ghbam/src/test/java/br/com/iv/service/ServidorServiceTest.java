package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import br.com.iv.dto.ServidorDto;
import br.com.iv.entity.Servidor;

class ServidorServiceTest {

	@Test
	void deveManterCrmECoremEmCamposDistintos() {
		ServidorDto dto = new ServidorDto();
		dto.setCrm("CRM-12345");
		dto.setCorem("COREN-98765");
		Servidor servidor = new Servidor();

		ServidorService.aplicarRegistroProfissional(servidor, dto);

		assertEquals("CRM-12345", servidor.getCrm());
		assertEquals("COREN-98765", servidor.getCorem());
	}
}
