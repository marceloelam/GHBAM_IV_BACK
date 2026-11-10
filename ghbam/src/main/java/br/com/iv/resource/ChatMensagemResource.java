package br.com.iv.resource;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.entity.ChatConversaLimpa;
import br.com.iv.entity.ChatMensagem;
import br.com.iv.repository.ChatConversaLimpaRepository;
import br.com.iv.repository.ChatMensagemRepository;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.ws.ChatPresenceEndpoint;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Chat dos profissionais.
 * - Quem envia/lê é sempre o usuário do token (os campos remetenteMatricula da requisição são ignorados
 *   quando há sessão), para ninguém ler o privado de outro nem enviar em nome de outro.
 * - A lista é leve: sem foto e sem o conteúdo do anexo (baixado em /{id}/anexo). Antes cada consulta
 *   a cada 3 s trazia até 100 mensagens com fotos e anexos em base64 — causa dos travamentos.
 * - desdeId: busca incremental (só o que chegou depois do último id que a tela já tem).
 * - Mensagem nova, leitura e ciência são empurradas pelo WebSocket (ChatPresenceEndpoint).
 */
@RequestScoped
@Path("/api/chat/mensagens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatMensagemResource {
    private static final int LIMITE_MENSAGENS = 100;
    private static final long MAX_ANEXO_BYTES = 5 * 1024 * 1024;
    private static final String URGENTE = "URGENTE";

    @Inject
    ChatMensagemRepository chatMensagemRepository;

    @Inject
    ChatConversaLimpaRepository chatConversaLimpaRepository;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Inject
    ObjectMapper objectMapper;

    @Context
    ContainerRequestContext requestContext;

    @GET
    public Response listar(@QueryParam("tipo") String tipo,
                           @QueryParam("sala") String sala,
                           @QueryParam("unidade") String unidade,
                           @QueryParam("remetenteMatricula") String remetenteMatricula,
                           @QueryParam("destinatarioMatricula") String destinatarioMatricula,
                           @QueryParam("desdeId") Integer desdeId) {
        String usuarioMatricula = matriculaDaSessao(remetenteMatricula);
        if (usuarioMatricula.isBlank()) return naoAutenticado();
        String tipoNormalizado = normalizarTipo(tipo);
        String unidadeNormalizada = unidadeDaSessao(unidade);
        int desde = desdeId == null || desdeId < 0 ? 0 : desdeId;

        List<ChatMensagem> mensagens;
        if ("private".equals(tipoNormalizado)) {
            String destinatario = safe(destinatarioMatricula);
            if (destinatario.isBlank()) {
                mensagens = chatMensagemRepository.listarPrivadasRecebidas(usuarioMatricula, desde, LIMITE_MENSAGENS);
            } else {
                if (unidadeNormalizada.isBlank()) {
                    return Response.status(Response.Status.BAD_REQUEST).entity("Parametro 'unidade' obrigatorio.").build();
                }
                mensagens = chatMensagemRepository.listarPrivado(unidadeNormalizada, usuarioMatricula, destinatario, desde, LIMITE_MENSAGENS);
            }
        } else {
            if (unidadeNormalizada.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST).entity("Parametro 'unidade' obrigatorio.").build();
            }
            mensagens = chatMensagemRepository.listarSala(unidadeNormalizada, safeOrDefault(sala, "geral"), desde, LIMITE_MENSAGENS);
        }

        return Response.ok(ordenarVisiveis(mensagens, usuarioMatricula)).build();
    }

    /** Para o aviso em qualquer tela: privadas recebidas + urgentes da sala sem ciência (exceto as minhas). */
    @GET
    @Path("/alertas")
    public Response alertas(@QueryParam("unidade") String unidade, @QueryParam("desdeId") Integer desdeId) {
        String usuarioMatricula = matriculaDaSessao(null);
        if (usuarioMatricula.isBlank()) return naoAutenticado();
        int desde = desdeId == null || desdeId < 0 ? 0 : desdeId;
        List<ChatMensagem> lista = new ArrayList<>(chatMensagemRepository.listarPrivadasRecebidas(usuarioMatricula, desde, 50));
        String unidadeSessao = unidadeDaSessao(unidade);
        if (!unidadeSessao.isBlank()) {
            chatMensagemRepository.listarUrgentesSala(unidadeSessao, desde, 20).stream()
                    .filter(m -> !usuarioMatricula.equals(m.getRemetenteMatricula()))
                    .forEach(lista::add);
        }
        return Response.ok(ordenarVisiveis(lista, usuarioMatricula)).build();
    }

    /** Quem está conectado ao tempo real na unidade da sessão (presença que não some com a aba em segundo plano). */
    @GET
    @Path("/online")
    public Response online(@QueryParam("unidade") String unidade) {
        if (matriculaDaSessao(null).isBlank()) return naoAutenticado();
        return Response.ok(ChatPresenceEndpoint.conectadosNaUnidade(unidadeDaSessao(unidade))).build();
    }

    @GET
    @Path("/{id}/anexo")
    public Response anexo(@PathParam("id") int id) {
        String usuarioMatricula = matriculaDaSessao(null);
        if (usuarioMatricula.isBlank()) return naoAutenticado();
        ChatMensagem mensagem = buscar(id);
        if (mensagem == null || mensagem.getAnexoConteudo() == null || mensagem.getAnexoConteudo().isBlank()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        boolean participante = usuarioMatricula.equals(mensagem.getRemetenteMatricula())
                || usuarioMatricula.equals(mensagem.getDestinatarioMatricula());
        if (!participante) return Response.status(Response.Status.FORBIDDEN).build();
        return Response.ok(new ChatAnexoDto(mensagem.getAnexoNome(), mensagem.getAnexoTipo(), mensagem.getAnexoTamanho(), mensagem.getAnexoConteudo())).build();
    }

    @POST
    @Path("/limpar")
    @Transactional
    public Response limpar(ChatLimparRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Dados da conversa obrigatorios.").build();
        }

        String tipo = normalizarTipo(request.tipo);
        String unidade = unidadeDaSessao(request.unidade);
        String usuarioMatricula = matriculaDaSessao(request.remetenteMatricula);
        String destinatarioMatricula = safe(request.destinatarioMatricula);
        String sala = safeOrDefault(request.sala, "geral");

        if (unidade.isBlank() || usuarioMatricula.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Unidade e usuario sao obrigatorios para limpar o chat.")
                    .build();
        }

        if ("private".equals(tipo) && destinatarioMatricula.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Destinatario obrigatorio para limpar conversa privada.")
                    .build();
        }

        int ultimoIdLimpo = calcularUltimoIdLimpo(tipo, unidade, usuarioMatricula, destinatarioMatricula, sala, request.ultimoId);
        ChatConversaLimpa registro = "private".equals(tipo)
                ? chatConversaLimpaRepository.salvarOuAtualizarPrivado(unidade, usuarioMatricula, destinatarioMatricula, ultimoIdLimpo)
                : chatConversaLimpaRepository.salvarOuAtualizarSala(unidade, usuarioMatricula, sala, ultimoIdLimpo);

        ChatLimparResponse response = new ChatLimparResponse();
        response.tipo = registro.getTipo();
        response.sala = registro.getSala();
        response.unidade = registro.getUnidade();
        response.usuarioMatricula = registro.getUsuarioMatricula();
        response.outroMatricula = registro.getOutroMatricula();
        response.ultimoIdLimpo = registro.getUltimoIdLimpo();
        return Response.ok(response).build();
    }

    @POST
    @Path("/visualizadas")
    @Transactional
    public Response marcarVisualizada(ChatVisualizacaoRequest request) {
        String usuarioMatricula = matriculaDaSessao(request == null ? null : request.matricula);
        if (request == null || request.messageId == null || usuarioMatricula.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Mensagem e matricula sao obrigatorias.").build();
        }
        int atualizadas = chatMensagemRepository.marcarVisualizada(request.messageId, usuarioMatricula);
        if (atualizadas > 0) {
            ChatMensagem mensagem = buscar(request.messageId);
            if (mensagem != null) {
                publicar(Map.of("event", "read", "messageId", String.valueOf(request.messageId),
                        "conversationKey", "private:" + usuarioMatricula), mensagem.getRemetenteMatricula());
            }
        }
        return Response.ok(Map.of("visualizada", atualizadas > 0)).build();
    }

    /** Ciência de mensagem urgente ("Ciente"): fica registrado quem assumiu e quando, e o remetente é avisado. */
    @POST
    @Path("/{id}/ciencia")
    @Transactional
    public Response ciencia(@PathParam("id") int id) {
        AuthenticatedUser user = usuarioDaSessao();
        if (user == null) return naoAutenticado();
        String nome = safeOrDefault(user.nome(), user.matricula());
        int n = chatMensagemRepository.registrarCiencia(id, user.matricula(), nome, unidadeDaSessao(user.unidade()));
        if (n == 0) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("erro", "Mensagem não encontrada, não urgente ou já com ciência.")).build();
        }
        ChatMensagem mensagem = buscar(id);
        Map<String, Object> evento = new HashMap<>();
        evento.put("event", "ciencia");
        evento.put("messageId", String.valueOf(id));
        evento.put("cienciaPor", nome);
        evento.put("cienciaEm", mensagem != null && mensagem.getCienciaEm() != null ? mensagem.getCienciaEm().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        // Para o aviso a quem enviou: quem deu ciencia e de qual mensagem (trecho curto).
        evento.put("cienciaMatricula", user.matricula());
        if (mensagem != null) {
            String texto = mensagem.getMensagem() == null ? "" : mensagem.getMensagem();
            evento.put("remetenteMatricula", mensagem.getRemetenteMatricula());
            evento.put("trecho", texto.length() > 160 ? texto.substring(0, 160) + "…" : texto);
        }
        if (mensagem != null && "room".equals(mensagem.getTipo())) {
            publicarUnidade(mensagem.getUnidade(), evento);
        } else if (mensagem != null) {
            publicar(evento, mensagem.getRemetenteMatricula(), mensagem.getDestinatarioMatricula());
        }
        return Response.ok(evento).build();
    }

    @POST
    @Transactional
    public Response criar(ChatMensagemRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Mensagem obrigatoria.").build();
        }
        AuthenticatedUser user = usuarioDaSessao();
        if (user == null && !legadoPermitido()) return naoAutenticado();

        String tipo = normalizarTipo(request.tipo);
        String unidade = unidadeDaSessao(request.unidade);
        String remetenteMatricula = user != null ? user.matricula() : safe(request.remetenteMatricula);
        String destinatarioMatricula = safe(request.destinatarioMatricula);
        String mensagemTexto = safe(request.mensagem);
        boolean temAnexo = request.anexo != null && !safe(request.anexo.conteudo).isBlank();

        if (unidade.isBlank() || remetenteMatricula.isBlank() || (mensagemTexto.isBlank() && !temAnexo)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Unidade, remetente e mensagem/anexo sao obrigatorios.")
                    .build();
        }

        if (temAnexo && !"private".equals(tipo)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Envio de arquivos permitido somente em conversa privada.")
                    .build();
        }

        if ("private".equals(tipo) && destinatarioMatricula.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Destinatario obrigatorio para mensagem privada.")
                    .build();
        }

        if (temAnexo && request.anexo.tamanho != null && request.anexo.tamanho > MAX_ANEXO_BYTES) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Arquivo maior que 5 MB.").build();
        }

        ChatMensagem mensagem = new ChatMensagem();
        mensagem.setTipo(tipo);
        mensagem.setSala(safeOrDefault(request.sala, "geral"));
        mensagem.setUnidade(unidade);
        mensagem.setMensagem(mensagemTexto);
        mensagem.setRemetenteMatricula(remetenteMatricula);
        mensagem.setRemetenteNome(user != null ? safe(user.nome()) : safe(request.remetenteNome));
        mensagem.setRemetenteCargo(user != null ? safe(user.cargo()) : safe(request.remetenteCargo));
        // Foto não é gravada por mensagem: a tela usa a da lista de online (antes cada mensagem levava a foto em base64).
        mensagem.setRemetenteFoto("");
        mensagem.setDestinatarioMatricula(destinatarioMatricula);
        mensagem.setDestinatarioNome(safe(request.destinatarioNome));
        mensagem.setDestinatarioFoto("");
        mensagem.setPrioridade(URGENTE.equalsIgnoreCase(safe(request.prioridade)) ? URGENTE : "NORMAL");

        if (request.anexo != null) {
            mensagem.setAnexoNome(safe(request.anexo.nome));
            mensagem.setAnexoTipo(safe(request.anexo.tipo));
            mensagem.setAnexoTamanho(request.anexo.tamanho);
            mensagem.setAnexoConteudo(safe(request.anexo.conteudo));
        }

        mensagem.persist();
        ChatMensagemDto dto = toDto(mensagem);
        Map<String, Object> evento = Map.of("event", "message", "message", dto);
        if ("private".equals(tipo)) publicar(evento, destinatarioMatricula, remetenteMatricula);
        else publicarUnidade(unidade, evento);
        return Response.ok(dto).build();
    }

    private ChatMensagem buscar(int id) {
        return chatMensagemRepository.find("idchatmensagem", id).firstResult();
    }

    private List<ChatMensagemDto> ordenarVisiveis(List<ChatMensagem> mensagens, String usuarioMatricula) {
        Map<String, Optional<ChatConversaLimpa>> limpezas = new HashMap<>();
        List<ChatMensagem> ordenadas = new ArrayList<>(mensagens.stream()
                .filter(mensagem -> visivelParaUsuario(mensagem, usuarioMatricula, limpezas))
                .toList());
        ordenadas.sort((a, b) -> Integer.compare(a.getIdchatmensagem(), b.getIdchatmensagem()));
        return ordenadas.stream().map(this::toDto).toList();
    }

    private void publicar(Map<String, Object> evento, String... matriculas) {
        try {
            ChatPresenceEndpoint.publicarParaUsuarios(objectMapper.writeValueAsString(evento), matriculas);
        } catch (Exception ignored) {
            // Tempo real é melhor esforço: a tela também faz busca incremental.
        }
    }

    private void publicarUnidade(String unidade, Map<String, Object> evento) {
        try {
            ChatPresenceEndpoint.publicarParaUnidade(unidade, objectMapper.writeValueAsString(evento));
        } catch (Exception ignored) {
            // Idem.
        }
    }

    private AuthenticatedUser usuarioDaSessao() {
        Object value = requestContext == null ? null : requestContext.getProperty("ghbam.user");
        if (value instanceof AuthenticatedUser user && user.matricula() != null && !user.matricula().isBlank()) return user;
        return null;
    }

    private String matriculaDaSessao(String informada) {
        AuthenticatedUser user = usuarioDaSessao();
        if (user != null) return user.matricula();
        return legadoPermitido() ? safe(informada) : "";
    }

    // Unidade da sessão (claim do JWT, inclui troca de unidade); a informada só quando não há claim.
    private String unidadeDaSessao(String informada) {
        String daSessao = requestContext == null ? "" : safe(securityPermissionService.unidadeDaSessao(requestContext));
        return daSessao.isBlank() ? safe(informada) : daSessao;
    }

    // Sem usuário resolvido só em dev com header legado; com Bearer o filtro sempre resolve o usuário.
    private boolean legadoPermitido() {
        return requestContext != null && requestContext.getHeaderString("Authorization") == null
                && requestContext.getHeaderString("X-GHBAM-MATRICULA") != null;
    }

    private Response naoAutenticado() {
        return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", "Sessão do chat não identificada.")).build();
    }

    private ChatMensagemDto toDto(ChatMensagem mensagem) {
        ChatMensagemDto dto = new ChatMensagemDto();
        dto.id = String.valueOf(mensagem.getIdchatmensagem());
        dto.type = mensagem.getTipo();
        dto.tipo = mensagem.getTipo();
        dto.room = mensagem.getSala();
        dto.sala = mensagem.getSala();
        dto.text = mensagem.getMensagem();
        dto.mensagem = mensagem.getMensagem();
        dto.unit = mensagem.getUnidade();
        dto.unidade = mensagem.getUnidade();
        dto.createdAt = mensagem.getCriadoEm() != null ? mensagem.getCriadoEm().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
        dto.dataHora = dto.createdAt;
        dto.read = mensagem.getVisualizadaEm() != null;
        dto.prioridade = mensagem.getPrioridade() == null ? "NORMAL" : mensagem.getPrioridade();
        dto.cienciaEm = mensagem.getCienciaEm() != null ? mensagem.getCienciaEm().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
        dto.cienciaPor = mensagem.getCienciaPor();
        dto.sender = new ChatUserDto(mensagem.getRemetenteMatricula(), mensagem.getRemetenteNome(), mensagem.getRemetenteCargo(), "");
        dto.remetente = dto.sender;
        dto.recipient = new ChatUserDto(mensagem.getDestinatarioMatricula(), mensagem.getDestinatarioNome(), "", "");
        dto.destinatario = dto.recipient;
        if (mensagem.getAnexoConteudo() != null && !mensagem.getAnexoConteudo().isBlank()) {
            // Só metadados: o conteúdo é baixado sob demanda em /{id}/anexo.
            dto.attachment = new ChatAnexoDto(mensagem.getAnexoNome(), mensagem.getAnexoTipo(), mensagem.getAnexoTamanho(), null);
            dto.attachment.url = "api/chat/mensagens/" + mensagem.getIdchatmensagem() + "/anexo";
            dto.anexo = dto.attachment;
        }
        return dto;
    }

    private String normalizarTipo(String tipo) {
        return "private".equalsIgnoreCase(safe(tipo)) ? "private" : "room";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeOrDefault(String value, String fallback) {
        String safeValue = safe(value);
        return safeValue.isBlank() ? fallback : safeValue;
    }

    // Uma consulta de limpeza por conversa (antes era uma por mensagem: até 100 consultas a cada 3 s por usuário).
    private boolean visivelParaUsuario(ChatMensagem mensagem, String usuarioMatricula, Map<String, Optional<ChatConversaLimpa>> cache) {
        boolean privado = "private".equals(mensagem.getTipo());
        String outro = "";
        if (privado) {
            outro = usuarioMatricula.equals(mensagem.getRemetenteMatricula())
                    ? safe(mensagem.getDestinatarioMatricula())
                    : safe(mensagem.getRemetenteMatricula());
            if (outro.isBlank()) return true;
        }
        String sala = safeOrDefault(mensagem.getSala(), "geral");
        String chave = (privado ? "p|" + outro : "r|" + sala) + "|" + mensagem.getUnidade();
        final String outroFinal = outro;
        ChatConversaLimpa registro = cache.computeIfAbsent(chave, k -> Optional.ofNullable(privado
                        ? chatConversaLimpaRepository.buscarPrivado(mensagem.getUnidade(), usuarioMatricula, outroFinal)
                        : chatConversaLimpaRepository.buscarSala(mensagem.getUnidade(), usuarioMatricula, sala)))
                .orElse(null);
        if (registro == null || registro.getUltimoIdLimpo() == null) {
            return true;
        }
        return mensagem.getIdchatmensagem() > registro.getUltimoIdLimpo();
    }

    private int calcularUltimoIdLimpo(String tipo, String unidade, String usuarioMatricula, String destinatarioMatricula, String sala, Integer ultimoIdInformado) {
        if (ultimoIdInformado != null && ultimoIdInformado > 0) {
            return ultimoIdInformado;
        }

        List<ChatMensagem> mensagens = "private".equals(tipo)
                ? chatMensagemRepository.listarPrivado(unidade, usuarioMatricula, destinatarioMatricula, 1)
                : chatMensagemRepository.listarSala(unidade, sala, 1);
        if (mensagens.isEmpty()) {
            return 0;
        }
        return mensagens.get(0).getIdchatmensagem();
    }

    public static class ChatMensagemRequest {
        public String tipo;
        public String sala;
        public String unidade;
        public String mensagem;
        public String remetenteMatricula;
        public String remetenteNome;
        public String remetenteCargo;
        public String remetenteFoto;
        public String destinatarioMatricula;
        public String destinatarioNome;
        public String destinatarioFoto;
        public String prioridade;
        public ChatAnexoDto anexo;
    }

    public static class ChatLimparRequest {
        public String tipo;
        public String sala;
        public String unidade;
        public String remetenteMatricula;
        public String destinatarioMatricula;
        public Integer ultimoId;
    }

    public static class ChatVisualizacaoRequest {
        public Integer messageId;
        public String matricula;
    }

    public static class ChatLimparResponse {
        public String tipo;
        public String sala;
        public String unidade;
        public String usuarioMatricula;
        public String outroMatricula;
        public Integer ultimoIdLimpo;
    }

    public static class ChatMensagemDto {
        public String id;
        public String type;
        public String tipo;
        public String room;
        public String sala;
        public String text;
        public String mensagem;
        public String unit;
        public String unidade;
        public String createdAt;
        public String dataHora;
        public boolean read;
        public String prioridade;
        public String cienciaEm;
        public String cienciaPor;
        public ChatUserDto sender;
        public ChatUserDto remetente;
        public ChatUserDto recipient;
        public ChatUserDto destinatario;
        public ChatAnexoDto attachment;
        public ChatAnexoDto anexo;
    }

    public static class ChatUserDto {
        public String matricula;
        public String nome;
        public String cargo;
        public String foto;

        public ChatUserDto(String matricula, String nome, String cargo) {
            this(matricula, nome, cargo, "");
        }

        public ChatUserDto(String matricula, String nome, String cargo, String foto) {
            this.matricula = matricula;
            this.nome = nome;
            this.cargo = cargo;
            this.foto = foto;
        }
    }

    public static class ChatAnexoDto {
        public String nome;
        public String tipo;
        public Long tamanho;
        public String conteudo;
        public String url;

        public ChatAnexoDto() {
        }

        public ChatAnexoDto(String nome, String tipo, Long tamanho, String conteudo) {
            this.nome = nome;
            this.tipo = tipo;
            this.tamanho = tamanho;
            this.conteudo = conteudo;
        }
    }
}
