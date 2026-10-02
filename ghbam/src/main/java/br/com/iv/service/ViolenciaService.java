package br.com.iv.service;


import br.com.iv.entity.Violencia;
import br.com.iv.repository.ViolenciaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import jakarta.transaction.Transactional;


@ApplicationScoped
public class ViolenciaService {

    @Inject
    ViolenciaRepository repository;



    @Transactional
    public Violencia save(Violencia violencia) {
        // Não salva se já existir um registro com o mesmo bamNumero
        if (violencia.getBamNumero() != null && !violencia.getBamNumero().isBlank()) {
            var existente = repository.find("bamNumero", violencia.getBamNumero()).firstResult();
            if (existente != null && (violencia.getId() == null || !((Violencia)existente).getId().equals(violencia.getId()))) {
                // Lança 409 Conflict informando que o registro já existe
                var resp = Response.status(Response.Status.CONFLICT)
                        .entity(java.util.Map.of("message", "Dr(a). Já houve um relato para este BAM de violência contra a mulher.", "bam", violencia.getBamNumero()))
                        .type(MediaType.APPLICATION_JSON)
                        .build();
                throw new WebApplicationException(resp);
            }
        }
        // O campo tipo agora é String, não precisa buscar entidade relacionada
        if (violencia.getId() == null) {
            repository.persist(violencia);
            return violencia;
        } else {
            return repository.getEntityManager().merge(violencia);
        }
    }



    public List<Violencia> findAll() {
        return repository.listAll();
    }



    public java.util.Optional<Violencia> findById(Long id) {
        return repository.findByIdOptional(id);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public List<Violencia> findByBam(int bam) {
        return repository.find("bam", bam).list();
    }
}
