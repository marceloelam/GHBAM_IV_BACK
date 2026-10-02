package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.CriarApacDto;
import br.com.iv.entity.FaturamentoApac;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.enums.ApacStatus;
import br.com.iv.repository.FaturamentoApacRepository;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class FaturamentoApacService {

    @Inject
    FaturamentoApacRepository apacRepository;

    @Inject
    FaturamentoAuditService auditService;

    public List<FaturamentoApac> listar(String status, Integer idcompetencia) {
        return apacRepository.listar(status, idcompetencia);
    }

    public List<FaturamentoApac> listar(String status, Integer idcompetencia, String unidade) {
        return apacRepository.listarPaginado(status, idcompetencia, unidade, null, 0, 200);
    }

    public List<FaturamentoApac> listarPaginado(String status, Integer idcompetencia, String unidade, String busca,
            int pagina, int tamanho) {
        return apacRepository.listarPaginado(status, idcompetencia, unidade, busca, pagina, tamanho);
    }

    public long contarFiltrado(String status, Integer idcompetencia, String unidade, String busca) {
        return apacRepository.contarFiltrado(status, idcompetencia, unidade, busca);
    }

    @Transactional
    public FaturamentoApac criar(CriarApacDto dto, AuthenticatedUser user) {
        if (dto == null || dto.getIdpaciente() <= 0) {
            throw new IllegalArgumentException("Paciente e obrigatorio.");
        }
        if (dto.getProcedimentoprincipal() == null || dto.getProcedimentoprincipal().isBlank()) {
            throw new IllegalArgumentException("Procedimento principal e obrigatorio.");
        }
        String unidade = texto(dto.getUnidade());
        if (unidade.isBlank() && user != null) {
            unidade = texto(user.unidade());
        }
        if (unidade.isBlank() || "TODAS".equalsIgnoreCase(unidade)) {
            throw new IllegalArgumentException("Unidade e obrigatoria para solicitar APAC.");
        }
        FaturamentoCompetencia competencia = competenciaAberta(dto.getIdcompetencia(), unidade);
        long sequencial = apacRepository.contarPorCompetenciaUnidade(competencia.getIdcompetencia(), unidade) + 1;

        FaturamentoApac apac = new FaturamentoApac();
        apac.setNumero(String.format("APAC-%s-%05d", competenciaAaaamm(competencia), sequencial));
        apac.setIdpaciente(dto.getIdpaciente());
        apac.setNomepaciente(dto.getNomepaciente());
        apac.setCnspaciente(dto.getCnspaciente());
        apac.setCpfpaciente(dto.getCpfpaciente());
        apac.setUnidade(unidade.toUpperCase());
        apac.setCnes(dto.getCnes());
        apac.setNomeestabelecimento(dto.getNomeestabelecimento());
        apac.setProntuario(dto.getProntuario());
        apac.setBam(dto.getBam());
        apac.setDatanascimento(dto.getDatanascimento());
        apac.setSexo(dto.getSexo());
        apac.setRacacor(dto.getRacacor());
        apac.setEtnia(dto.getEtnia());
        apac.setSituacaorua(dto.getSituacaorua());
        apac.setNomemae(dto.getNomemae());
        apac.setNomeresponsavel(dto.getNomeresponsavel());
        apac.setTelefonecelular(dto.getTelefonecelular());
        apac.setTelefonecontato(dto.getTelefonecontato());
        apac.setMunicipioresidencia(dto.getMunicipioresidencia());
        apac.setUf(dto.getUf());
        apac.setCodigoibge(dto.getCodigoibge());
        apac.setCep(dto.getCep());
        apac.setEndereco(dto.getEndereco());
        apac.setComplemento(dto.getComplemento());
        apac.setIdprofissional(dto.getIdprofissional());
        apac.setMatriculaprofissional(dto.getMatriculaprofissional());
        apac.setNomeprofissional(dto.getNomeprofissional());
        apac.setCnsprofissional(dto.getCnsprofissional());
        apac.setCpfprofissional(dto.getCpfprofissional());
        apac.setConselhoprofissional(dto.getConselhoprofissional());
        apac.setRegistroprofissional(dto.getRegistroprofissional());
        apac.setProcedimentoprincipal(dto.getProcedimentoprincipal());
        apac.setDescricaoprocedimentoprincipal(dto.getDescricaoprocedimentoprincipal());
        apac.setProcedimentossecundarios(dto.getProcedimentossecundarios());
        apac.setDescricaoprocedimentossecundarios(dto.getDescricaoprocedimentossecundarios());
        apac.setCid(dto.getCid());
        apac.setCidsecundario(dto.getCidsecundario());
        apac.setDiagnosticoobservacaoclinica(dto.getDiagnosticoobservacaoclinica());
        apac.setJustificativaclinica(dto.getJustificativaclinica());
        apac.setExamescomplementares(dto.getExamescomplementares());
        apac.setNomeestabelecimentoexecutante(dto.getNomeestabelecimentoexecutante());
        apac.setCnesexecutante(dto.getCnesexecutante());
        apac.setOrgaoautorizador(dto.getOrgaoautorizador());
        apac.setCodorgaoemissor(dto.getCodorgaoemissor());
        apac.setProfissionalautorizador(dto.getProfissionalautorizador());
        apac.setCnsprofissionalautorizador(dto.getCnsprofissionalautorizador());
        apac.setNumeroautorizacao(dto.getNumeroautorizacao());
        apac.setValidadeinicio(dto.getValidadeinicio());
        apac.setValidadefim(dto.getValidadefim());
        apac.setQuantidade(dto.getQuantidade() <= 0 ? 1 : dto.getQuantidade());
        apac.setIdcompetencia(competencia.getIdcompetencia());
        apac.setStatus(ApacStatus.SOLICITADA.name());
        apac.setDataSolicitacao(LocalDateTime.now());
        apac.setCriadoEm(LocalDateTime.now());
        apac.setUsuarioCriador(user == null ? "" : user.matricula());
        apac.persist();

        auditService.registrar(user == null ? "" : user.unidade(), "CRIAR_APAC", "SUCESSO", "FaturamentoApac",
                apac.getNumero(), null, ApacStatus.SOLICITADA.name(), null, null, user);
        return apac;
    }

    @Transactional
    public FaturamentoApac autorizar(int idapac, AuthenticatedUser user) {
        FaturamentoApac apac = obrigatoria(idapac, user == null ? null : user.unidade());
        String anterior = apac.getStatus();
        if (!ApacStatus.SOLICITADA.name().equals(anterior) && !ApacStatus.AGUARDANDO_AUTORIZACAO.name().equals(anterior)) {
            throw new IllegalStateException("APAC " + apac.getNumero() + " nao pode ser autorizada no status " + anterior + ".");
        }
        apac.setStatus(ApacStatus.AUTORIZADA.name());
        apac.setDataAutorizacao(LocalDateTime.now());
        apac.setUsuarioAutorizacao(user == null ? "" : user.matricula());
        apac.setAtualizadoEm(LocalDateTime.now());

        auditService.registrar(user == null ? "" : user.unidade(), "AUTORIZAR_APAC", "SUCESSO", "FaturamentoApac",
                apac.getNumero(), anterior, ApacStatus.AUTORIZADA.name(), null, null, user);
        return apac;
    }

    @Transactional
    public FaturamentoApac alterarStatus(int idapac, ApacStatus novoStatus, AuthenticatedUser user) {
        FaturamentoApac apac = obrigatoria(idapac, user == null ? null : user.unidade());
        String anterior = apac.getStatus();
        apac.setStatus(novoStatus.name());
        apac.setAtualizadoEm(LocalDateTime.now());

        auditService.registrar(user == null ? "" : user.unidade(), "ALTERAR_STATUS_APAC", "SUCESSO", "FaturamentoApac",
                apac.getNumero(), anterior, novoStatus.name(), null, null, user);
        return apac;
    }

    // A APAC so entra na remessa do SIA se tiver idcompetencia (ver
    // FaturamentoApacRepository.listarElegiveisExportacao). Sem esta validacao a APAC nasce
    // orfa - numero APAC-000000-xxxxx - e nunca e exportada, por mais que seja encerrada.
    private FaturamentoCompetencia competenciaAberta(Integer idcompetencia, String unidade) {
        if (idcompetencia == null) {
            throw new IllegalArgumentException("Competencia e obrigatoria para solicitar APAC. "
                    + "Abra a competencia da unidade em Faturamento SUS > Competencias.");
        }
        FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
        if (competencia == null) {
            throw new IllegalArgumentException("Competencia nao encontrada para o id: " + idcompetencia);
        }
        if (!"ABERTA".equalsIgnoreCase(texto(competencia.getStatus()))) {
            throw new IllegalArgumentException("Competencia " + competencia.getCompetencia() + " esta "
                    + competencia.getStatus() + ". Só e possivel solicitar APAC em competencia ABERTA.");
        }
        String unidadeCompetencia = texto(competencia.getUnidade());
        if (!unidadeCompetencia.isBlank() && !"TODAS".equalsIgnoreCase(unidadeCompetencia)
                && !unidadeCompetencia.equalsIgnoreCase(unidade)) {
            throw new IllegalArgumentException("Competencia " + competencia.getCompetencia() + " pertence a unidade "
                    + unidadeCompetencia + " e nao a " + unidade + ".");
        }
        return competencia;
    }

    // O numero interno usa a competencia AAAAMM (202608), nao o id da tabela - o placeholder
    // de 6 digitos do formato antigo (APAC-000000-) foi desenhado para AAAAMM.
    private String competenciaAaaamm(FaturamentoCompetencia competencia) {
        String digitos = texto(competencia.getCompetencia()).replaceAll("\\D", "");
        if (digitos.length() >= 6) {
            return digitos.substring(0, 6);
        }
        return String.format("%6s", digitos).replace(' ', '0');
    }

    private FaturamentoApac obrigatoria(int idapac) {
        return obrigatoria(idapac, null);
    }

    private FaturamentoApac obrigatoria(int idapac, String unidade) {
        FaturamentoApac apac = FaturamentoApac.findById(idapac);
        if (apac == null) {
            throw new IllegalArgumentException("APAC nao encontrada para o id: " + idapac);
        }
        if (unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade)
                && (apac.getUnidade() == null || !apac.getUnidade().equalsIgnoreCase(unidade.trim()))) {
            throw new IllegalArgumentException("APAC nao encontrada para a unidade informada.");
        }
        return apac;
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
