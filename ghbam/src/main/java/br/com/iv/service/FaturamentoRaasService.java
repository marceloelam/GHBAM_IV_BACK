package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.CriarRaasDto;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.entity.FaturamentoRaas;
import br.com.iv.enums.RaasStatus;
import br.com.iv.repository.FaturamentoRaasRepository;
import br.com.iv.repository.FaturamentoProducaoRepository;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class FaturamentoRaasService {

    @Inject
    FaturamentoRaasRepository raasRepository;

    @Inject
    FaturamentoProducaoRepository producaoRepository;

    @Inject
    FaturamentoAuditService auditService;

    public List<FaturamentoRaas> listar(String status, Integer idcompetencia) {
        return raasRepository.listar(status, idcompetencia);
    }

    public List<FaturamentoRaas> listar(String status, Integer idcompetencia, String unidade) {
        return raasRepository.listarPaginado(status, idcompetencia, unidade, null, 0, 200);
    }

    public List<FaturamentoRaas> listarPaginado(String status, Integer idcompetencia, String unidade, String busca,
            int pagina, int tamanho) {
        return raasRepository.listarPaginado(status, idcompetencia, unidade, busca, pagina, tamanho);
    }

    public long contarFiltrado(String status, Integer idcompetencia, String unidade, String busca) {
        return raasRepository.contarFiltrado(status, idcompetencia, unidade, busca);
    }

    /**
     * Prepara RAAS em rascunho a partir da producao que o SIGTAP identificou como RAAS.
     * Nao conclui nem exporta o registro: a equipe ainda precisa registrar/conferir as
     * acoes realizadas. A chave paciente+procedimento evita duplicacao a cada reprocessamento.
     */
    @Transactional
    public int prepararRascunhosAutomaticos(FaturamentoCompetencia competencia, String unidade, AuthenticatedUser user) {
        if (competencia == null || competencia.getIdcompetencia() <= 0) {
            return 0;
        }
        String unidadeEfetiva = texto(unidade).isBlank() ? texto(competencia.getUnidade()) : texto(unidade);
        if (unidadeEfetiva.isBlank() || "TODAS".equalsIgnoreCase(unidadeEfetiva)) {
            return 0;
        }
        int criadas = 0;
        long proximoSequencial = raasRepository.contarPorCompetenciaUnidade(competencia.getIdcompetencia(), unidadeEfetiva) + 1;
        for (Afaturar producao : producaoRepository.listarRaasAptasParaRascunho(competencia.getIdcompetencia(), unidadeEfetiva)) {
            String procedimento = texto(producao.getProcedimento());
            if (procedimento.isBlank() || raasRepository.existeParaPacienteProcedimento(
                    competencia.getIdcompetencia(), unidadeEfetiva, producao.getIdpaciente(), procedimento)) {
                continue;
            }
            FaturamentoRaas raas = new FaturamentoRaas();
            raas.setNumero(String.format("RAAS-%s-%05d", competenciaAaaamm(competencia), proximoSequencial++));
            raas.setIdpaciente(producao.getIdpaciente());
            raas.setNomepaciente(producao.getNomepaciente());
            raas.setCnspaciente(producao.getCnsdopciente());
            raas.setUnidade(unidadeEfetiva.toUpperCase());
            raas.setMatriculaprofissional(producao.getMatriculaservidor());
            raas.setNomeprofissional(producao.getNomeservidor());
            raas.setCnsprofissional(producao.getCnsdoprofissional());
            raas.setProcedimento(procedimento);
            raas.setCid(producao.getCid10());
            raas.setProntuario(String.valueOf(producao.getBam()));
            raas.setSexo(producao.getSexo());
            raas.setDatanascimento(producao.getDtnascimento());
            raas.setCodigoibge(producao.getIbgecod());
            raas.setMesatendimento(competencia.getCompetencia());
            raas.setIdcompetencia(competencia.getIdcompetencia());
            raas.setStatus(RaasStatus.ABERTA.name());
            raas.setCriadoEm(LocalDateTime.now());
            raas.setUsuarioCriador(user == null ? "" : user.matricula());
            raas.persist();
            criadas++;
        }
        if (criadas > 0) {
            auditService.registrar(unidadeEfetiva, "PREPARAR_RAAS_AUTOMATICA", "SUCESSO", "FaturamentoCompetencia",
                    String.valueOf(competencia.getIdcompetencia()), null, String.valueOf(criadas), null,
                    "Rascunhos RAAS preparados a partir da producao SIGTAP.", user);
        }
        return criadas;
    }

    @Transactional
    public FaturamentoRaas criar(CriarRaasDto dto, AuthenticatedUser user) {
        if (dto == null || dto.getIdpaciente() <= 0) {
            throw new IllegalArgumentException("Paciente e obrigatorio.");
        }
        if (dto.getProcedimento() == null || dto.getProcedimento().isBlank()) {
            throw new IllegalArgumentException("Procedimento e obrigatorio.");
        }
        String unidade = texto(dto.getUnidade());
        if (unidade.isBlank() && user != null) {
            unidade = texto(user.unidade());
        }
        if (unidade.isBlank() || "TODAS".equalsIgnoreCase(unidade)) {
            throw new IllegalArgumentException("Unidade e obrigatoria para abrir RAAS.");
        }
        FaturamentoCompetencia competencia = competenciaAberta(dto.getIdcompetencia(), unidade);
        long sequencial = raasRepository.contarPorCompetenciaUnidade(competencia.getIdcompetencia(), unidade) + 1;

        FaturamentoRaas raas = new FaturamentoRaas();
        raas.setNumero(String.format("RAAS-%s-%05d", competenciaAaaamm(competencia), sequencial));
        raas.setIdpaciente(dto.getIdpaciente());
        raas.setNomepaciente(dto.getNomepaciente());
        raas.setCnspaciente(dto.getCnspaciente());
        raas.setUnidade(unidade.toUpperCase());
        raas.setCnes(dto.getCnes());
        raas.setIdprofissional(dto.getIdprofissional());
        raas.setMatriculaprofissional(dto.getMatriculaprofissional());
        raas.setNomeprofissional(dto.getNomeprofissional());
        raas.setCnsprofissional(dto.getCnsprofissional());
        raas.setCpfprofissional(dto.getCpfprofissional());
        raas.setConselhoprofissional(dto.getConselhoprofissional());
        raas.setRegistroprofissional(dto.getRegistroprofissional());
        raas.setProcedimento(dto.getProcedimento());
        raas.setCid(dto.getCid());
        raas.setNomeestabelecimento(dto.getNomeestabelecimento());
        raas.setProntuario(dto.getProntuario());
        raas.setSexo(dto.getSexo());
        raas.setDatanascimento(dto.getDatanascimento());
        raas.setNacionalidade(dto.getNacionalidade());
        raas.setRacacor(dto.getRacacor());
        raas.setEtniaindigena(dto.getEtniaindigena());
        raas.setNomemae(dto.getNomemae());
        raas.setNomeresponsavel(dto.getNomeresponsavel());
        raas.setMunicipioresidencia(dto.getMunicipioresidencia());
        raas.setUf(dto.getUf());
        raas.setCodigoibge(dto.getCodigoibge());
        raas.setCep(dto.getCep());
        raas.setEndereco(dto.getEndereco());
        raas.setComplemento(dto.getComplemento());
        raas.setTelefonecelular(dto.getTelefonecelular());
        raas.setTelefonecontato(dto.getTelefonecontato());
        raas.setDataadmissao(dto.getDataadmissao());
        raas.setMesatendimento(dto.getMesatendimento());
        raas.setNumeroautorizacao(dto.getNumeroautorizacao());
        raas.setUsuarioalcooldrogas(dto.getUsuarioalcooldrogas());
        raas.setDrogaalcool(dto.getDrogaalcool());
        raas.setDrogacrack(dto.getDrogacrack());
        raas.setDrogaoutras(dto.getDrogaoutras());
        raas.setOrigempaciente(dto.getOrigempaciente());
        raas.setCidcausasassociadas(dto.getCidcausasassociadas());
        raas.setDiagnosticoprincipal(dto.getDiagnosticoprincipal());
        raas.setDiagnosticocausasassociadas(dto.getDiagnosticocausasassociadas());
        raas.setCoberturaesf(dto.getCoberturaesf());
        raas.setCnesesf(dto.getCnesesf());
        raas.setEncaminhamento(dto.getEncaminhamento());
        raas.setConclusao(dto.getConclusao());
        raas.setAcoesrealizadas(dto.getAcoesrealizadas());
        raas.setIdcompetencia(competencia.getIdcompetencia());
        raas.setStatus(RaasStatus.ABERTA.name());
        raas.setCriadoEm(LocalDateTime.now());
        raas.setUsuarioCriador(user == null ? "" : user.matricula());
        raas.persist();

        auditService.registrar(user == null ? "" : user.unidade(), "CRIAR_RAAS", "SUCESSO", "FaturamentoRaas",
                raas.getNumero(), null, RaasStatus.ABERTA.name(), null, null, user);
        return raas;
    }

    @Transactional
    public FaturamentoRaas alterarStatus(int idraas, RaasStatus novoStatus, AuthenticatedUser user) {
        FaturamentoRaas raas = obrigatoria(idraas, user == null ? null : user.unidade());
        String anterior = raas.getStatus();
        raas.setStatus(novoStatus.name());
        raas.setAtualizadoEm(LocalDateTime.now());

        auditService.registrar(user == null ? "" : user.unidade(), "ALTERAR_STATUS_RAAS", "SUCESSO", "FaturamentoRaas",
                raas.getNumero(), anterior, novoStatus.name(), null, null, user);
        return raas;
    }

    // A RAAS so entra na remessa do SIA se tiver idcompetencia (ver
    // FaturamentoRaasRepository.listarElegiveisExportacao). Sem esta validacao a RAAS nasce
    // orfa - numero RAAS-000000-xxxxx - e nunca e exportada, por mais que seja concluida.
    private FaturamentoCompetencia competenciaAberta(Integer idcompetencia, String unidade) {
        if (idcompetencia == null) {
            throw new IllegalArgumentException("Competencia e obrigatoria para abrir RAAS. "
                    + "Abra a competencia da unidade em Faturamento SUS > Competencias.");
        }
        FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
        if (competencia == null) {
            throw new IllegalArgumentException("Competencia nao encontrada para o id: " + idcompetencia);
        }
        if (!"ABERTA".equalsIgnoreCase(texto(competencia.getStatus()))) {
            throw new IllegalArgumentException("Competencia " + competencia.getCompetencia() + " esta "
                    + competencia.getStatus() + ". Só e possivel abrir RAAS em competencia ABERTA.");
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
    // de 6 digitos do formato antigo (RAAS-000000-) foi desenhado para AAAAMM.
    private String competenciaAaaamm(FaturamentoCompetencia competencia) {
        String digitos = texto(competencia.getCompetencia()).replaceAll("\\D", "");
        if (digitos.length() >= 6) {
            return digitos.substring(0, 6);
        }
        return String.format("%6s", digitos).replace(' ', '0');
    }

    private FaturamentoRaas obrigatoria(int idraas, String unidade) {
        FaturamentoRaas raas = FaturamentoRaas.findById(idraas);
        if (raas == null) {
            throw new IllegalArgumentException("RAAS nao encontrada para o id: " + idraas);
        }
        if (unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade)
                && (raas.getUnidade() == null || !raas.getUnidade().equalsIgnoreCase(unidade.trim()))) {
            throw new IllegalArgumentException("RAAS nao encontrada para a unidade informada.");
        }
        return raas;
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
