package br.com.iv.repository;

import java.io.Serializable;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class AntigoCorrigirRepository implements Serializable{

	private static final long serialVersionUID = 1L;
	@Inject
	EntityManager entityManager;

	public void corrigirAntigo() {
		String sql =  "UPDATE PACIENTES set municipio = 'ITAGUAI', naturalidade = 'RJ', "
				+ "cns = '000000000000000', estado = 'RJ', data=cast('2025-01-20' as date), "
				+ "ecivil = 'SOLTEIRO', hora = '00:00', numero='0', prontuario = '0', rg='0', "
				+ "sangue = 'NF',sexo='M', telefone = '0', pacienteespecial='TIPICO',profissao='NF', "
				+ "ENDERECO = 'NÃO INFORMADO', bairro='CENTRO', etnia = 'PARDO' "
				+ "WHERE  municipio is null";
		entityManager.createNativeQuery(sql).executeUpdate();

	}
	

}
