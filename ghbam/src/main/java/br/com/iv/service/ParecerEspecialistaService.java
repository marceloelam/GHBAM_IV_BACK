package br.com.iv.service;

import br.com.iv.dto.AnamneseInternacaoDto;
import br.com.iv.dto.ParecerEspecialistaDto;
import br.com.iv.entity.ParecerEspecialista;
import br.com.iv.repository.ParecerEspecialistaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ParecerEspecialistaService {

    @Inject
    ParecerEspecialistaRepository repository;

    @Inject
    AssinaturaClinicaService assinaturaClinicaService;

    public List<ParecerEspecialistaDto> findAll() {
        return repository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ParecerEspecialistaDto findByIdDto(int id) {
        ParecerEspecialista p = repository.findById(id);
        return p == null ? null : toDto(p);
    }
    
    public ParecerEspecialistaDto findByBamDto(int bam, String unidade) {
        ParecerEspecialista p = repository.findUltimoParecerEspecialistaByNbamAndUnidade(bam, unidade);
        return p == null ? null : toDto(p);
    }
    
    public List<ParecerEspecialista> listaDePareceres(String especialidade, String unidade) {
        return repository.listaDePareceres(especialidade, unidade);
	}
    
    public ParecerEspecialista findById(int id) {
        return repository.findById(id);
    }

    @Transactional
    public ParecerEspecialistaDto create(ParecerEspecialistaDto dto) {
        ParecerEspecialista entity = toEntity(dto);
        repository.persist(entity);
        return toDto(entity);
    }

    @Transactional
    public ParecerEspecialistaDto update(int id, ParecerEspecialistaDto dto) {
        ParecerEspecialista entity = repository.findById(id);
        if (entity == null) {
            return null;
        }
        bloquearSeAssinado(id);
        // copy mutable fields
        entity.setDesc1(dto.getDesc1());
        entity.setDesc2(dto.getDesc2());
        entity.setData(dto.getData());
        entity.setDataresp(dto.getDataresp());
        entity.setProntuario(truncate(dto.getProntuario(), 15));
        entity.setMedicorequisitante(truncate(dto.getMedicorequisitante(), 55));
        entity.setCrm(truncate(dto.getCrm(), 15));
        entity.setMedicorecebedor(truncate(dto.getMedicorecebedor(), 55));
        entity.setCrmrecebedor(truncate(dto.getCrmrecebedor(), 15));
        entity.setEspecialidadeorigem(truncate(dto.getEspecialidadeorigem(), 15));
        entity.setEspecialidadedestino(truncate(dto.getEspecialidadedestino(), 15));
        entity.setUnidade(truncate(dto.getUnidade(), 25));
        entity.setNbam(dto.getNbam());

        repository.persist(entity);
        return toDto(entity);
    }

    @Transactional
    public ParecerEspecialistaDto responder(int id, ParecerEspecialistaDto dto) {
        ParecerEspecialista entity = repository.findById(id);
        if (entity == null) {
            return null;
        }
        if (!vazio(entity.getDesc2())) {
            throw new WebApplicationException(
                    "Parecer ja respondido. Registre um novo parecer ou adendo em vez de alterar a resposta.",
                    Response.Status.CONFLICT);
        }
        if (vazio(dto.getDesc2())) {
            throw new WebApplicationException(
                    "Informe a resposta do especialista antes de salvar.",
                    Response.Status.BAD_REQUEST);
        }

        entity.setDesc2(dto.getDesc2());
        entity.setDataresp(dto.getDataresp());
        entity.setMedicorecebedor(truncate(dto.getMedicorecebedor(), 55));
        entity.setCrmrecebedor(truncate(dto.getCrmrecebedor(), 15));

        repository.persist(entity);
        return toDto(entity);
    }

    @Transactional
    public boolean delete(int id) {
        if (repository.findById(id) == null) {
            return false;
        }
        bloquearSeAssinado(id);
        return repository.deleteById(id);
    }

    private void bloquearSeAssinado(Integer iddocumento) {
        if (assinaturaClinicaService.bloqueadoPorDocumento(AssinaturaClinicaService.PARECER_ESPECIALISTA, iddocumento)) {
            throw new WebApplicationException(
                    "Documento ja assinado. Registre um adendo em vez de alterar o conteudo original.",
                    Response.Status.CONFLICT);
        }
    }

    private ParecerEspecialistaDto toDto(ParecerEspecialista e) {
        if (e == null) return null;
        return new ParecerEspecialistaDto(
                e.getIdparecerespecialista(),
                e.getDesc1(),
                e.getData(),
                e.getDataresp(),
                e.getDesc2(),
                e.getProntuario(),
                e.getMedicorequisitante(),
                e.getCrm(),
                e.getMedicorecebedor(),
                e.getCrmrecebedor(),
                e.getEspecialidadeorigem(),
                e.getEspecialidadedestino(),
                e.getUnidade(),
                e.getNbam()
        );
    }

    private ParecerEspecialista toEntity(ParecerEspecialistaDto d) {
        ParecerEspecialista e = new ParecerEspecialista();
        // If DTO carries id and it's >0, set it (entity id is generated; setting may be ignored)
        e.setIdparecerespecialista(d.getIdparecerespecialista());
        e.setDesc1(d.getDesc1());
        e.setDesc2(d.getDesc2());
        e.setData(d.getData());
        e.setDataresp(d.getDataresp());
        e.setProntuario(truncate(d.getProntuario(), 15));
        e.setMedicorequisitante(truncate(d.getMedicorequisitante(), 55));
        e.setCrm(truncate(d.getCrm(), 15));
        e.setMedicorecebedor(truncate(d.getMedicorecebedor(), 55));
        e.setCrmrecebedor(truncate(d.getCrmrecebedor(), 15));
        e.setEspecialidadeorigem(truncate(d.getEspecialidadeorigem(), 15));
        e.setEspecialidadedestino(truncate(d.getEspecialidadedestino(), 15));
        e.setUnidade(truncate(d.getUnidade(), 25));
        e.setNbam(d.getNbam());
        return e;
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        if (value.length() <= maxLen) return value;
        return value.substring(0, maxLen);
    }

    private boolean vazio(String value) {
        return value == null || value.trim().isEmpty();
    }

	public List<AnamneseInternacaoDto> findAnamneseInternacao(int nbam, String unidade) {
        return repository.findAnamneseInternacao(nbam, unidade);
	}
}
