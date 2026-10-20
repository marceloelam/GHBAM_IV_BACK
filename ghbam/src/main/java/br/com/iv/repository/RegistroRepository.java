package br.com.iv.repository;

import java.util.List;

import jakarta.inject.Inject;

import br.com.iv.entity.Registro;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RegistroRepository implements PanacheRepository<Registro> {
	@Inject
	EntityManager entityManager;

	public Registro filtrarPorId(int id) {
		return find("idregistro = ?1", id).firstResult();
	}

	public Registro filtrarPorControle(String controle) {
		return find("controle = ?1", controle).firstResult();
	}

	public List<Registro> filtarPorControle(String query, String query2) {
		return find("controle = ?1", query).list();
	}

	public void atualizarRegistroAtendidoPorNaInternacao(int idRegistro) {
		String sql = "UPDATE registro set atendidopor = concat(atendidopor, ' ') where idregistro = :id";
		entityManager.createNativeQuery(sql).setParameter("id", idRegistro).executeUpdate();
	}

	public int atualizarRegistroResponsavelObservacao(int idRegistro, String responsavel, String obs) {
		String sql = "UPDATE registro set responsavel = :responsavel, obsentrada = :obs, atendidopor='', eletivo=false  where idregistro = :id";
		return entityManager.createNativeQuery(sql).setParameter("id", idRegistro).setParameter("responsavel", responsavel)
				.setParameter("obs", obs).executeUpdate();
	}

	public void liberarPaciente(int idRegistro) {
		liberarPacienteBam(idRegistro);

		String sql = "UPDATE registro set atendidopor = '.'  where idregistro = :id";
		entityManager.createNativeQuery(sql).setParameter("id", idRegistro).executeUpdate();
	}

	public void liberarPacienteBam(int idRegistro) {
		String sql = "UPDATE bam set ematendimento = false  where registro_idregistro = :id";
		entityManager.createNativeQuery(sql).setParameter("id", idRegistro).executeUpdate();
	}

	// TRATAMENTO - ZERANDO CAMPO TRATADO
	public void atualizarTratamento(int idRegistro) {
		String sql = "UPDATE TRATAMENTO set tratado = 0  where idtratamento in  (select idtratamento from tratamento inner join bam on tratamento.idtratamento = bam.tratamento_idtratamento inner join registro on registro.idregistro = bam.registro_idregistro where registro.idregistro =:id)";
		entityManager.createNativeQuery(sql).setParameter("id", idRegistro).executeUpdate();
	}

	// BAM - retirar do atendimento sem apagar o marcador de transferencia
	public void atualizarBam(int idRegistro) {
		String sql = "UPDATE BAM set ematendimento = false  where idbam in  (select idbam from bam  inner join registro on registro.idregistro = bam.registro_idregistro where registro.idregistro =:id)";
		entityManager.createNativeQuery(sql).setParameter("id", idRegistro).executeUpdate();
	}


	@Transactional
    public void atualizarAvaliacao(int idRegistro, String novosetor) {
        try {
            // 1 - Buscar avaliação e unidade do BAM
            String sqlAvaliacao =
                "SELECT avaliacao_idavaliacao, unidade " +
                "FROM bam " +
                "WHERE idbam IN (SELECT idbam FROM bam WHERE registro_idregistro = :id LIMIT 1) " +
                "LIMIT 1";

			@SuppressWarnings("unchecked")
			java.util.List<Object[]> resultados = entityManager
					.createNativeQuery(sqlAvaliacao)
					.setParameter("id", idRegistro)
					.getResultList();

			if (resultados == null || resultados.isEmpty()) {
				// nenhum bam/avaliação encontrado para esse registro; nada a atualizar
				return;
			}

			Object[] resultado = resultados.get(0);
			Integer avaliacaoId = ((Number) resultado[0]).intValue();
			String unidadeEncontrada = (resultado[1] != null) ? resultado[1].toString() : null;

			if (unidadeEncontrada == null || unidadeEncontrada.trim().isEmpty()) {
				return;
			}

            // 2 - Verificar se o setor precisa de triagem
            String sqlTriagem =
                "SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END " +
                "FROM setoresunidade " +
                "WHERE siglaunidade = :unidade " +
                "AND setor = :setor " +
                "AND triar = true";

			    Number resultadoTriagem = (Number) entityManager
				    .createNativeQuery(sqlTriagem)
				    .setParameter("unidade", unidadeEncontrada)
				    .setParameter("setor", novosetor.trim())
				    .getSingleResult();

            int valorAvaliado = (resultadoTriagem != null && resultadoTriagem.intValue() == 1) ? 1 : 0;

            // 3 - Atualizar tabela avaliacao
            String sqlUpdate =
                "UPDATE avaliacao SET avaliado = :valor WHERE idavaliacao = :idavaliacao";

            int linhasAfetadas = entityManager.createNativeQuery(sqlUpdate)
                    .setParameter("valor", valorAvaliado)
                    .setParameter("idavaliacao", avaliacaoId)
                    .executeUpdate();

            //System.out.println("Atualização concluída: avaliacaoId=%d, avaliado=%d, linhas=%d" +
              //        avaliacaoId, valorAvaliado, linhasAfetadas);

		} catch (NoResultException e) {
			// nenhum resultado encontrado em consultas internas; não é erro crítico
			return;
		} catch (Exception e) {
			// Lança novamente para forçar rollback
			throw e;
		}
    }
	
	
	
	
	
	
	public void atualizarNovoSetorDeAtendimento(int idRegistro, String novosetor) {
		String sql = "UPDATE registro set setor = :novosetor where idregistro = :id";
		entityManager.createNativeQuery(sql).setParameter("id", idRegistro).setParameter("novosetor", novosetor)
				.executeUpdate();
		atualizarTratamento(idRegistro);
		atualizarBam(idRegistro);
		atualizarAvaliacao(idRegistro, novosetor);
	}

	public void atualizarRegistroEspera(int idRegistro, String atendidopor) {
		String sql;
		if (atendidopor.equals("-")) {
			sql = "UPDATE registro set atendidopor = '' where idregistro = :id";
			entityManager.createNativeQuery(sql).setParameter("id", idRegistro).executeUpdate();
		} else {
			sql = "UPDATE registro set atendidopor = :atendidopor where idregistro = :id";
			entityManager.createNativeQuery(sql).setParameter("id", idRegistro).setParameter("atendidopor", atendidopor)
					.executeUpdate();
		}
	}

	@SuppressWarnings("unchecked")
	public List<Registro> duploAtendimentoMedico(String unidade, String medico) {
		/*
		 * String sql = "SELECT * FROM registro r" +
		 * " left join bam b on b.registro_idregistro = r.idregistro " +
		 * " left join tratamento as t on b.tratamento_idtratamento = t.idtratamento " +
		 * " left join pacientes p on b.paciente_idpaciente = p.idpaciente " +
		 * " WHERE t.tratado = 0 " + " AND r.atendidopor = :medico " +
		 * " AND b.unidade = :unidade " + " ORDER BY b.idbam LIMIT 1";
		 */
		String sql = "SELECT r.*, b.idbam, t.idtratamento, p.idpaciente FROM registro r "
				+ "LEFT JOIN bam b ON b.registro_idregistro = r.idregistro "
				+ "LEFT JOIN tratamento t ON b.tratamento_idtratamento = t.idtratamento "
				+ "LEFT JOIN pacientes p ON b.paciente_idpaciente = p.idpaciente WHERE t.tratado = 0 "
				+ "AND r.atendidopor = :medico AND b.unidade = :unidade ORDER BY b.idbam LIMIT 1";

		return getEntityManager().createNativeQuery(sql, Registro.class).setParameter("medico", medico)
				.setParameter("unidade", unidade).getResultList();

	}

	public long contarAtendimentosNaSemana(String servidor, String unidade, String quando) {
	    String sql;

	    if ("dia".equals(quando)) {
	        sql = "SELECT COALESCE(COUNT(*), 0) " +
	              "FROM registro r " +
	              "INNER JOIN bam b ON b.registro_idregistro = r.idregistro " +
	              "WHERE r.atendidopor = :servidor " +
	              "AND b.unidade = :unidade " +
	              "AND b.data >= timezone('America/Sao_Paulo', now())::date " +
	              "AND b.data < timezone('America/Sao_Paulo', now())::date + INTERVAL '1 day'";
	    }

	    else if ("semana".equals(quando)) {
	        sql = "SELECT COALESCE(COUNT(*), 0) " +
	              "FROM registro r " +
	              "INNER JOIN bam b ON b.registro_idregistro = r.idregistro " +
	              "WHERE r.atendidopor = :servidor " +
	              "AND b.unidade = :unidade " +
	              "AND b.data >= date_trunc('week', timezone('America/Sao_Paulo', now()))::date " +
	              "AND b.data < (date_trunc('week', timezone('America/Sao_Paulo', now())) + INTERVAL '7 days')::date";
	    }

	    else if ("mes".equals(quando)) {
	        sql = "SELECT COALESCE(COUNT(*), 0) " +
	              "FROM registro r " +
	              "INNER JOIN bam b ON b.registro_idregistro = r.idregistro " +
	              "WHERE r.atendidopor = :servidor " +
	              "AND b.unidade = :unidade " +
	              "AND b.data >= date_trunc('month', timezone('America/Sao_Paulo', now()))::date " +
	              "AND b.data < (date_trunc('month', timezone('America/Sao_Paulo', now())) + INTERVAL '1 month')::date";
	    }

	    else if ("ano".equals(quando)) {
	        sql = "SELECT COALESCE(COUNT(*), 0) " +
	              "FROM registro r " +
	              "INNER JOIN bam b ON b.registro_idregistro = r.idregistro " +
	              "WHERE r.atendidopor = :servidor " +
	              "AND b.unidade = :unidade " +
	              "AND b.data >= date_trunc('year', timezone('America/Sao_Paulo', now()))::date " +
	              "AND b.data < (date_trunc('year', timezone('America/Sao_Paulo', now())) + INTERVAL '1 year')::date";
	    }
	    else {
	        throw new IllegalArgumentException("Periodo de contagem invalido.");
	    }

	    try {
	        return ((Number) entityManager.createNativeQuery(sql)
	                .setParameter("servidor", servidor)
	                .setParameter("unidade", unidade)
	                .getSingleResult()).longValue();
	    } catch (NoResultException e) {
	        return 0;
	    }
	}

	public void atualizarRegistroinformarexame(int bam, String local) {
		String sql = "";
		if(local.equals("LABORATORIO")) {
			 sql = "UPDATE registro SET temlabo = true where idregistro in (select registro_idregistro from bam where idbam = :bam)";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		} else if(local.equals("RAIOX")) {
			 sql = "UPDATE registro set temrx = true where idregistro in (select registro_idregistro from bam where idbam = :bam)";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		} else if(local.equals("TOMOGRAFIA")) {
			 sql = "UPDATE registro set temtomo = true where idregistro in (select registro_idregistro from bam where idbam = :bam)";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		} else if(local.equals("MEDICACAO")) {
			 sql = "UPDATE registro set temmed = true where idregistro in (select registro_idregistro from bam where idbam = :bam)";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		} else if(local.equals("ENFERMAGEM")) {
			 sql = "UPDATE registro set temenfe = true where idregistro in (select registro_idregistro from bam where idbam = :bam)";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		}
		
		
	}

}
