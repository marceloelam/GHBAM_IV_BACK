package br.com.iv.odontologia;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.StringReader;

import br.com.iv.dto.AssinaturaClinicaDto;
import br.com.iv.dto.OdontologiaFilaDto;
import br.com.iv.entity.ProntuarioClinicoItem;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.AssinaturaClinicaService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class OdontologiaService {
    @Inject
    AssinaturaClinicaService assinaturaClinicaService;

    @Inject
    EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<OdontologiaFilaDto> filaOdontologica(String unidade, String idMedico) {
        LocalDate data = LocalDate.now();
        DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String datas = data.format(formatador);

        // 1. Patients in the queue (triagem)
        String sqlFila = """
            SELECT DISTINCT p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.mae, p.pai, p.nascimento,
                   a.idavaliacao, a.data AS dataav, a.avaliado,
                   b.idbam, b.prioridade, a.hora, b.cor,
                   r.idregistro, r.eletivo, r.setor,
                   t.idtratamento, t.tratado,
                   EXISTS (
                     SELECT 1 FROM escalapaciente ep
                     WHERE ep.agbam = b.idbam AND ep.agenda = true
                   ) AS agendado,
                   COALESCE((
                     SELECT ep.modalidade FROM escalapaciente ep
                     WHERE ep.agbam = b.idbam AND ep.agenda = true
                     ORDER BY ep.idescalapaciente DESC LIMIT 1
                   ), 'PRESENCIAL') AS modalidade,
                   COALESCE((
                     SELECT ep.medico FROM escalapaciente ep
                     WHERE ep.agbam = b.idbam AND ep.agenda = true
                     ORDER BY ep.idescalapaciente DESC LIMIT 1
                   ), '') AS medico_agendado,
                   COALESCE((
                     SELECT ep.iddomedico FROM escalapaciente ep
                     WHERE ep.agbam = b.idbam AND ep.agenda = true
                     ORDER BY ep.idescalapaciente DESC LIMIT 1
                   ), 0) AS id_medico_agendado
            FROM pacientes p
            JOIN bam b ON b.paciente_idpaciente = p.idpaciente
            JOIN avaliacao a ON b.avaliacao_idavaliacao = a.idavaliacao
            JOIN registro r ON b.registro_idregistro = r.idregistro
            JOIN tratamento t ON b.tratamento_idtratamento = t.idtratamento
            WHERE UPPER(r.setor) IN ('ODONTOLOGIA', 'DENTISTA', 'BUCOMAXILO')
              AND UPPER(b.unidade) = UPPER(:unidade)
              AND t.tratado = 0
              AND b.ematendimento = false
              AND b.transferido = false
              AND CAST(b.data AS date) = CAST(:data AS date)
              AND (
                  NOT EXISTS (
                    SELECT 1 FROM setoresunidade s
                    WHERE UPPER(s.setor) = UPPER(r.setor)
                      AND UPPER(s.siglaunidade) = UPPER(:unidade)
                      AND s.triar = true
                  )
                  OR a.avaliado = 1
              )
            ORDER BY b.prioridade DESC, a.hora
            """;

        // 2. Scheduled patients (agendados, not yet checked in)
        String sqlAgendados = """
            SELECT p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.mae, p.pai, p.nascimento,
                   ep.horamarcada, ep.setor, COALESCE(ep.modalidade, 'PRESENCIAL'), ep.medico, ep.iddomedico
            FROM escalapaciente ep
            JOIN pacientes p ON p.idpaciente = ep.paciente_idpaciente
            WHERE UPPER(ep.setor) IN ('ODONTOLOGIA', 'DENTISTA', 'BUCOMAXILO')
              AND UPPER(ep.unidade) = UPPER(:unidade)
              AND ep.agenda = true
              AND ep.atendido = false
              AND ep.inicioagenda = CAST(:data AS date)
            ORDER BY ep.horamarcada
            """;

        List<OdontologiaFilaDto> pacientes = new ArrayList<>();

        // Process queue
        var queryFila = entityManager.createNativeQuery(sqlFila);
        queryFila.setParameter("unidade", unidade);
        queryFila.setParameter("data", datas);
        for (Object[] row : (List<Object[]>) queryFila.getResultList()) {
            OdontologiaFilaDto dto = new OdontologiaFilaDto();
            dto.setIdPaciente(row[0] != null ? ((Number) row[0]).intValue() : 0);
            dto.setNome((String) row[1]);
            dto.setIdade(row[2] != null ? ((Number) row[2]).intValue() : null);
            dto.setPacienteespecial((String) row[3]);
            dto.setMae((String) row[4]);
            dto.setPai((String) row[5]);
            dto.setNascimento((String) row[6]);
            dto.setIdAvaliacao(row[7] != null ? ((Number) row[7]).intValue() : 0);
            dto.setDataav((String) row[8]);
            dto.setAvaliado(row[9] != null ? ((Number) row[9]).intValue() : 0);
            dto.setIdBam(row[10] != null ? ((Number) row[10]).intValue() : 0);
            dto.setPrioridade(row[11] != null ? ((Number) row[11]).intValue() : 0);
            dto.setHora((String) row[12]);
            dto.setCor(row[13] != null ? ((Number) row[13]).intValue() : 1);
            dto.setIdRegistro(row[14] != null ? ((Number) row[14]).intValue() : 0);
            dto.setEletivo(row[15] != null ? (Boolean) row[15] : false);
            dto.setSetor((String) row[16]);
            dto.setIdTratamento(row[17] != null ? ((Number) row[17]).intValue() : 0);
            dto.setTratado(row[18] != null ? ((Number) row[18]).intValue() : 0);
            dto.setTipo("fila");
            dto.setAgendado(row[19] != null && (Boolean) row[19]);
            dto.setModalidade(dto.isAgendado() ? normalizarModalidade(row[20]) : "");
            dto.setMedicoAgendado(dto.isAgendado() && row[21] != null ? row[21].toString().trim() : "");
            dto.setIdMedicoAgendado(dto.isAgendado() && row[22] != null ? ((Number) row[22]).intValue() : null);
            dto.setPodeAtender(!dto.isAgendado() || mesmoMedico(idMedico, dto.getIdMedicoAgendado()));
            pacientes.add(dto);
        }

        // Mescla o agendamento ao BAM já presente na fila, sem perder triagem/classificação.
        var pacientesPorId = pacientes.stream().collect(Collectors.toMap(
                OdontologiaFilaDto::getIdPaciente,
                paciente -> paciente,
                (primeiro, ignorado) -> primeiro));
        if (idMedico != null && !idMedico.isBlank()) {
            int medicoId;
            try { medicoId = Integer.parseInt(idMedico); }
            catch (NumberFormatException e) { medicoId = 0; }
            if (medicoId > 0) {
                var queryAgendados = entityManager.createNativeQuery(sqlAgendados);
                queryAgendados.setParameter("unidade", unidade);
                queryAgendados.setParameter("data", datas);
                    for (Object[] row : (List<Object[]>) queryAgendados.getResultList()) {
                        int idPaciente = row[0] != null ? ((Number) row[0]).intValue() : 0;
                        OdontologiaFilaDto pacienteNaFila = pacientesPorId.get(idPaciente);
                        if (pacienteNaFila != null) {
                            pacienteNaFila.setAgendado(true);
                            pacienteNaFila.setModalidade(normalizarModalidade(row[9]));
                            pacienteNaFila.setMedicoAgendado(row[10] != null ? row[10].toString().trim() : "");
                            pacienteNaFila.setIdMedicoAgendado(row[11] != null ? ((Number) row[11]).intValue() : null);
                            pacienteNaFila.setPodeAtender(mesmoMedico(idMedico, pacienteNaFila.getIdMedicoAgendado()));
                            continue;
                        }

                        OdontologiaFilaDto dto = new OdontologiaFilaDto();
                        dto.setIdPaciente(idPaciente);
                        dto.setNome((String) row[1]);
                        dto.setIdade(row[2] != null ? ((Number) row[2]).intValue() : null);
                        dto.setPacienteespecial((String) row[3]);
                        dto.setMae((String) row[4]);
                        dto.setPai((String) row[5]);
                        dto.setNascimento((String) row[6]);
                        dto.setHora((String) row[7]);
                        dto.setSetor((String) row[8]);
                        dto.setModalidade(normalizarModalidade(row[9]));
                        dto.setTipo("agendado");
                        dto.setAgendado(true);
                        dto.setMedicoAgendado(row[10] != null ? row[10].toString().trim() : "");
                        dto.setIdMedicoAgendado(row[11] != null ? ((Number) row[11]).intValue() : null);
                        dto.setPodeAtender(mesmoMedico(idMedico, dto.getIdMedicoAgendado()));
                        pacientes.add(dto);
                        pacientesPorId.put(idPaciente, dto);
                    }
                }
            }

        return pacientes;
    }

    private String normalizarModalidade(Object modalidade) {
        String valor = modalidade == null ? "PRESENCIAL" : modalidade.toString().trim().toUpperCase(Locale.ROOT);
        return valor.contains("VIDEO") || valor.contains("VÍDEO") ? "VIDEO" : "PRESENCIAL";
    }

    private boolean mesmoMedico(String idMedicoLogado, Integer idMedicoAgendado) {
        if (idMedicoAgendado == null || idMedicoAgendado <= 0 || idMedicoLogado == null) return false;
        return idMedicoLogado.trim().equals(String.valueOf(idMedicoAgendado));
    }

    @Transactional
    public List<OdontologiaAtendimento> listarPorPaciente(Integer idpaciente) {
        if (idpaciente == null || idpaciente <= 0) return List.of();
        List<OdontologiaAtendimento> atendimentos = OdontologiaAtendimento
                .find("idpaciente = ?1 order by criadoEm desc", idpaciente)
                .list();
        OdontologiaProntuario prontuario = OdontologiaProntuario.find("idpaciente", idpaciente).firstResult();
        if (prontuario != null) {
            garantirNumero(prontuario);
            atendimentos.forEach(item -> enriquecer(item, prontuario));
        }
        return atendimentos;
    }

    @Transactional
    public OdontologiaAtendimentoView localizar(Integer id) {
        OdontologiaAtendimento atendimento = OdontologiaAtendimento.findById(id);
        if (atendimento == null) return null;
        OdontologiaProntuario prontuario = OdontologiaProntuario.findById(atendimento.getIdprontuario());
        if (prontuario != null) {
            garantirNumero(prontuario);
            enriquecer(atendimento, prontuario);
        }
        return new OdontologiaAtendimentoView(atendimento, registros(id));
    }

    @Transactional
    public OdontologiaAtendimentoView criar(OdontologiaAtendimentoDto dto, AuthenticatedUser user) {
        validarUsuario(user);
        validarDto(dto);
        OdontologiaAtendimento atendimento = new OdontologiaAtendimento();
        preencher(atendimento, dto, user);
        atendimento.setIdprontuario(obterOuCriarProntuario(dto.getIdpaciente()).getIdprontuario());
        atendimento.setStatus("RASCUNHO");
        atendimento.persist();
        substituirRegistros(atendimento.getIdatendimento(), dto.getRegistros());
        sincronizarHistoricoGeral(atendimento, user);
        return localizar(atendimento.getIdatendimento());
    }

    private OdontologiaProntuario obterOuCriarProntuario(Integer idpaciente) {
        OdontologiaProntuario existente = OdontologiaProntuario.find("idpaciente", idpaciente).firstResult();
        if (existente != null) {
            garantirNumero(existente);
            return existente;
        }
        OdontologiaProntuario prontuario = new OdontologiaProntuario();
        prontuario.setIdpaciente(idpaciente);
        prontuario.persist();
        prontuario.flush();
        garantirNumero(prontuario);
        return prontuario;
    }

    private void garantirNumero(OdontologiaProntuario prontuario) {
        if (prontuario.getNumeroProntuario() != null && !prontuario.getNumeroProntuario().isBlank()) return;
        int ano = (prontuario.getCriadoEm() == null ? LocalDateTime.now() : prontuario.getCriadoEm()).getYear();
        prontuario.setNumeroProntuario(OdontologiaRules.numeroProntuario(ano, prontuario.getIdprontuario()));
    }

    private void enriquecer(OdontologiaAtendimento atendimento, OdontologiaProntuario prontuario) {
        atendimento.setIdprontuario(prontuario.getIdprontuario());
        atendimento.setNumeroProntuarioOdontologico(prontuario.getNumeroProntuario());
    }

    private void sincronizarHistoricoGeral(OdontologiaAtendimento atendimento, AuthenticatedUser user) {
        upsertHistorico("ODONTO-FICHA-" + atendimento.getIdatendimento(), atendimento,
                atendimento.getIdbam(), "Ficha de tratamento odontologico",
                limpar(atendimento.getQueixaPrincipal()),
                "ASSINADO".equalsIgnoreCase(atendimento.getStatus()) ? "FINALIZADO" : "EM_TRATAMENTO",
                "", user);
        sincronizarRegistrosJson(atendimento.getEvolucoesJson(), "ODONTO-EV-", atendimento, user,
                "Procedimento odontologico");
        sincronizarRegistrosJson(atendimento.getPeriodontaisJson(), "ODONTO-PER-", atendimento, user,
                "Evolucao periodontal");
    }

    private void sincronizarRegistrosJson(String json, String prefixo, OdontologiaAtendimento atendimento,
            AuthenticatedUser user, String titulo) {
        if (vazio(json)) return;
        try (var reader = Json.createReader(new StringReader(json))) {
            JsonArray itens = reader.readArray();
            for (var valor : itens) {
                if (!(valor instanceof JsonObject item)) continue;
                String id = item.getString("id", "");
                if (id.isBlank()) continue;
                String codigo = prefixo + id;
                if (codigo.length() > 40) codigo = codigo.substring(0, 40);
                Integer idbam = item.containsKey("idbam") && !item.isNull("idbam")
                        ? item.getInt("idbam") : atendimento.getIdbam();
                upsertHistorico(codigo, atendimento, idbam, titulo,
                        item.getString("descricao", ""), "ATIVO",
                        item.getString("observacao", ""), user);
            }
        } catch (RuntimeException ignored) {
            // JSON legado invalido nao pode impedir o salvamento da ficha primaria.
        }
    }

    private void upsertHistorico(String codigo, OdontologiaAtendimento atendimento, Integer idbam,
            String titulo, String descricao, String status, String observacao, AuthenticatedUser user) {
        ProntuarioClinicoItem item = ProntuarioClinicoItem.find("codigo", codigo).firstResult();
        boolean novo = item == null;
        if (novo) item = new ProntuarioClinicoItem();
        item.setIdpaciente(atendimento.getIdpaciente());
        item.setIdbam(idbam);
        item.setUnidade(atendimento.getUnidade());
        item.setTipo("ATENDIMENTO_ODONTOLOGICO");
        item.setStatus(status);
        item.setCodigo(codigo);
        item.setTitulo(titulo);
        item.setDescricao(descricao);
        item.setObservacao(observacao);
        if (novo) {
            item.setCriadoPor(limpar(user.nome()));
            item.setCriadoPorMatricula(limpar(user.matricula()));
            item.persist();
        } else {
            item.setAtualizadoPor(limpar(user.nome()));
            item.setAtualizadoPorMatricula(limpar(user.matricula()));
        }
    }

    @Transactional
    public OdontologiaAtendimentoView atualizar(Integer id, OdontologiaAtendimentoDto dto, AuthenticatedUser user) {
        validarUsuario(user);
        validarDto(dto);
        OdontologiaAtendimento atendimento = OdontologiaAtendimento.findById(id);
        if (atendimento == null) return null;
        OdontologiaRules.exigirEditavel(atendimento.getStatus());
        preencher(atendimento, dto, user);
        substituirRegistros(id, dto.getRegistros());
        sincronizarHistoricoGeral(atendimento, user);
        return localizar(id);
    }

    @Transactional
    public OdontologiaAtendimentoView assinar(Integer id, OdontologiaAtendimentoDto dto, AuthenticatedUser user) {
        validarUsuario(user);
        OdontologiaAtendimento atendimento = OdontologiaAtendimento.findById(id);
        if (atendimento == null) return null;
        OdontologiaRules.exigirEditavel(atendimento.getStatus());
        if (dto == null || vazio(dto.getConteudoHash())) {
            throw new IllegalArgumentException("Hash do conteudo odontologico e obrigatorio.");
        }

        AssinaturaClinicaDto assinatura = new AssinaturaClinicaDto();
        assinatura.setTipoDocumento(AssinaturaClinicaService.AVALIACAO_ODONTOLOGICA);
        assinatura.setIddocumento(id);
        assinatura.setIdbam(atendimento.getIdbam());
        assinatura.setIdpaciente(atendimento.getIdpaciente());
        assinatura.setUnidade(atendimento.getUnidade());
        assinatura.setConteudoHash(dto.getConteudoHash());
        assinatura.setConteudoResumo(dto.getConteudoResumo());
        assinaturaClinicaService.assinar(assinatura, user);
        atendimento.setStatus("ASSINADO");
        sincronizarHistoricoGeral(atendimento, user);
        return localizar(id);
    }

    private void substituirRegistros(Integer idatendimento, List<OdontologiaAtendimentoDto.RegistroDto> registros) {
        OdontogramaRegistro.delete("idatendimento", idatendimento);
        if (registros == null) return;
        for (OdontologiaAtendimentoDto.RegistroDto dto : registros) {
            if (dto == null || dto.getNumeroDente() == null
                    || !OdontologiaRules.numeroFdiValido(dto.getNumeroDente())) {
                throw new IllegalArgumentException("Numero de dente FDI invalido.");
            }
            String face = normalizar(dto.getFace());
            String condicao = normalizar(dto.getCondicao());
            if (!OdontologiaRules.faceValida(face)) throw new IllegalArgumentException("Face dentaria invalida.");
            if (!OdontologiaRules.condicaoValida(condicao)) throw new IllegalArgumentException("Condicao dentaria invalida.");
            OdontogramaRegistro registro = new OdontogramaRegistro();
            registro.setIdatendimento(idatendimento);
            registro.setNumeroDente(dto.getNumeroDente());
            registro.setFace(face);
            registro.setCondicao(condicao);
            registro.setDescricao(limpar(dto.getDescricao()));
            registro.persist();
        }
    }

    private List<OdontogramaRegistro> registros(Integer idatendimento) {
        return OdontogramaRegistro
                .find("idatendimento = ?1 order by numeroDente, face", idatendimento)
                .list();
    }

    private void preencher(OdontologiaAtendimento destino, OdontologiaAtendimentoDto dto, AuthenticatedUser user) {
        if (destino.getIdatendimento() == null) {
            destino.setIdpaciente(dto.getIdpaciente());
            destino.setIdbam(dto.getIdbam());
            destino.setUnidade(limpar(user.unidade()));
        }
        destino.setTipoOdontograma(valorPermitido(dto.getTipoOdontograma(), "INICIAL", "PLANEJAMENTO", "ACOMPANHAMENTO"));
        destino.setDenticao(valorPermitido(dto.getDenticao(), "PERMANENTE", "DECIDUA"));
        destino.setQueixaPrincipal(limpar(dto.getQueixaPrincipal()));
        destino.setHistoriaDoencaAtual(limpar(dto.getHistoriaDoencaAtual()));
        destino.setAnamneseJson(limpar(dto.getAnamneseJson()));
        destino.setExameGeral(limpar(dto.getExameGeral()));
        destino.setExameExtraoral(limpar(dto.getExameExtraoral()));
        destino.setExameIntraoral(limpar(dto.getExameIntraoral()));
        destino.setObservacoes(limpar(dto.getObservacoes()));
        destino.setEvolucoesJson(limpar(dto.getEvolucoesJson()));
        destino.setPeriodontaisJson(limpar(dto.getPeriodontaisJson()));
        if (destino.getIdatendimento() == null) {
            destino.setCriadoPor(limpar(user.nome()));
            destino.setCriadoPorMatricula(limpar(user.matricula()));
        }
    }

    private void validarDto(OdontologiaAtendimentoDto dto) {
        if (dto == null || dto.getIdpaciente() == null || dto.getIdpaciente() <= 0) {
            throw new IllegalArgumentException("Paciente obrigatorio.");
        }
    }

    private void validarUsuario(AuthenticatedUser user) {
        if (user == null || vazio(user.matricula())) throw new IllegalStateException("Usuario nao autenticado.");
    }

    private String valorPermitido(String valor, String... permitidos) {
        String normalizado = normalizar(valor);
        for (String permitido : permitidos) if (permitido.equals(normalizado)) return normalizado;
        return permitidos[0];
    }

    private String normalizar(String valor) {
        return limpar(valor).toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String limpar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
