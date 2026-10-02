package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.TratamentoDto;
import br.com.iv.entity.Tratamento;
import br.com.iv.repository.TratamentoRepository;

@ApplicationScoped
public class TratamentoService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    TratamentoRepository tratamentoRepository;

    public List<Tratamento> listarTodosOsTratamentos() {
        return tratamentoRepository.listAll();
    }

    public Tratamento listarPorID(int id) {
        return tratamentoRepository.filtrarPorId(id);
    }

    public Tratamento listarPorControle(String controle) {
        return tratamentoRepository.filtrarPorControle(controle);
    }

    @Transactional // Define um tempo limite de 60 segundos
    public void finalizarBam(int idtratamento) {
        tratamentoRepository.finalizarBam(idtratamento);
    }

    @Transactional
    public void finalizarAtendimentoMedico(int idtratamento, String alta, String op, String usuario) {
        tratamentoRepository.finalizarAtendimentoMedico(idtratamento, alta, op, usuario);
    }

    @Transactional
    public void atualizarTipoAlta(int idtratamento) {
        tratamentoRepository.atualizarTipoAlta(idtratamento);
    }

    @Transactional
    public Tratamento salvarTratamento(TratamentoDto dto) {
        Tratamento tratamento = new Tratamento();
        tratamento.setData(dto.getData());
        tratamento.setExames_solicitados(dto.getExames_solicitados());
        tratamento.setDiagnostico_provisorio(dto.getDiagnostico_provisorio());
        tratamento.setPrescricao_inicial(dto.getPrescricao_inicial());
        tratamento.setEvolucao_prescricao(dto.getEvolucao_prescricao());
        tratamento.setDestino(dto.getDestino());
        tratamento.setEncaminhamento(dto.getEncaminhamento());
        tratamento.setHora(dto.getHora());
        tratamento.setObs_saida(dto.getObs_saida());
        tratamento.setParecer_especialista(dto.getParecer_especialista());
        tratamento.setObs_enfermagem(dto.getObs_enfermagem());
        tratamento.setTipo_alta(dto.getTipo_alta());
        tratamento.setTratado(dto.getTratado());
        tratamento.setControle(dto.getControle());
        tratamento.setMedicoecrm(dto.getMedicoecrm());

        // Utilize apenas persist() para maior eficiência
        tratamento.persist();
        return tratamento;
    }

    @Transactional
    public void atualizarTratamento(int id, TratamentoDto dto) {
        Optional<Tratamento> tratamentoOp = Tratamento.findByIdOptional(id);
        if (tratamentoOp.isEmpty()) {
            throw new RuntimeException("Tratamento não encontrado para ID: " + id);
        }

        Tratamento tratamento = tratamentoOp.get();
        tratamento.setData(dto.getData());
        tratamento.setExames_solicitados(dto.getExames_solicitados());
        tratamento.setDiagnostico_provisorio(dto.getDiagnostico_provisorio());
        tratamento.setPrescricao_inicial(dto.getPrescricao_inicial());
        tratamento.setEvolucao_prescricao(dto.getEvolucao_prescricao());
        tratamento.setDestino(dto.getDestino());
        tratamento.setEncaminhamento(dto.getEncaminhamento());
        tratamento.setHora(dto.getHora());
        tratamento.setObs_saida(dto.getObs_saida());
        tratamento.setParecer_especialista(dto.getParecer_especialista());
        tratamento.setObs_enfermagem(dto.getObs_enfermagem());
        tratamento.setTipo_alta(dto.getTipo_alta());
        tratamento.setTratado(dto.getTratado());
        tratamento.setMedicoecrm(dto.getMedicoecrm());

        tratamento.persist();
    }

	public void finalizarBamIdPaciente(int idtratamentoidpaciente) {
		 tratamentoRepository.finalizarBamIdPaciente(idtratamentoidpaciente);
		
	}
}
