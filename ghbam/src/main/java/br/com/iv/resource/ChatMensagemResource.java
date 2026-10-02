package br.com.iv.resource;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import br.com.iv.entity.ChatConversaLimpa;
import br.com.iv.entity.ChatMensagem;
import br.com.iv.repository.ChatConversaLimpaRepository;
import br.com.iv.repository.ChatMensagemRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/chat/mensagens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatMensagemResource {
    private static final int LIMITE_MENSAGENS = 100;
    private static final long MAX_ANEXO_BYTES = 5 * 1024 * 1024;

    @Inject
    ChatMensagemRepository chatMensagemRepository;

    @Inject
    ChatConversaLimpaRepository chatConversaLimpaRepository;

    @GET
    public Response listar(@QueryParam("tipo") String tipo,
                           @QueryParam("sala") String sala,
                           @QueryParam("unidade") String unidade,
                           @QueryParam("remetenteMatricula") String remetenteMatricula,
                           @QueryParam("destinatarioMatricula") String destinatarioMatricula) {
        String tipoNormalizado = normalizarTipo(tipo);
        String unidadeNormalizada = safe(unidade);
        String usuarioMatricula = safe(remetenteMatricula);

        List<ChatMensagem> mensagens;
        if ("private".equals(tipoNormalizado)) {
            String atual = usuarioMatricula;
            String destinatario = safe(destinatarioMatricula);
            if (atual.isBlank()) {
                return Response.ok(Collections.emptyList()).build();
            }
            if (destinatario.isBlank()) {
                mensagens = chatMensagemRepository.listarPrivadasRecebidas(atual, LIMITE_MENSAGENS);
            } else {
                if (unidadeNormalizada.isBlank()) {
                    return Response.status(Response.Status.BAD_REQUEST).entity("Parametro 'unidade' obrigatorio.").build();
                }
                mensagens = chatMensagemRepository.listarPrivado(unidadeNormalizada, atual, destinatario, LIMITE_MENSAGENS);
            }
        } else {
            if (unidadeNormalizada.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST).entity("Parametro 'unidade' obrigatorio.").build();
            }
            mensagens = chatMensagemRepository.listarSala(unidadeNormalizada, safeOrDefault(sala, "geral"), LIMITE_MENSAGENS);
        }

        if (!usuarioMatricula.isBlank()) {
            mensagens = mensagens.stream()
                    .filter(mensagem -> visivelParaUsuario(mensagem, usuarioMatricula))
                    .toList();
        }

        List<ChatMensagem> ordenadas = new ArrayList<>(mensagens);
        Collections.reverse(ordenadas);
        return Response.ok(ordenadas.stream().map(this::toDto).toList()).build();
    }

    @POST
    @Path("/limpar")
    @Transactional
    public Response limpar(ChatLimparRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Dados da conversa obrigatorios.").build();
        }

        String tipo = normalizarTipo(request.tipo);
        String unidade = safe(request.unidade);
        String usuarioMatricula = safe(request.remetenteMatricula);
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
        if (request == null || request.messageId == null || safe(request.matricula).isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Mensagem e matricula sao obrigatorias.").build();
        }
        int atualizadas = chatMensagemRepository.marcarVisualizada(request.messageId, safe(request.matricula));
        return Response.ok(java.util.Map.of("visualizada", atualizadas > 0)).build();
    }

    @POST
    @Transactional
    public Response criar(ChatMensagemRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Mensagem obrigatoria.").build();
        }

        String tipo = normalizarTipo(request.tipo);
        String unidade = safe(request.unidade);
        String remetenteMatricula = safe(request.remetenteMatricula);
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
        mensagem.setRemetenteNome(safe(request.remetenteNome));
        mensagem.setRemetenteCargo(safe(request.remetenteCargo));
        mensagem.setRemetenteFoto(safe(request.remetenteFoto));
        mensagem.setDestinatarioMatricula(destinatarioMatricula);
        mensagem.setDestinatarioNome(safe(request.destinatarioNome));
        mensagem.setDestinatarioFoto(safe(request.destinatarioFoto));

        if (request.anexo != null) {
            mensagem.setAnexoNome(safe(request.anexo.nome));
            mensagem.setAnexoTipo(safe(request.anexo.tipo));
            mensagem.setAnexoTamanho(request.anexo.tamanho);
            mensagem.setAnexoConteudo(safe(request.anexo.conteudo));
        }

        mensagem.persist();
        return Response.ok(toDto(mensagem)).build();
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
        dto.sender = new ChatUserDto(mensagem.getRemetenteMatricula(), mensagem.getRemetenteNome(), mensagem.getRemetenteCargo(), mensagem.getRemetenteFoto());
        dto.remetente = dto.sender;
        dto.recipient = new ChatUserDto(mensagem.getDestinatarioMatricula(), mensagem.getDestinatarioNome(), "", mensagem.getDestinatarioFoto());
        dto.destinatario = dto.recipient;
        if (mensagem.getAnexoConteudo() != null && !mensagem.getAnexoConteudo().isBlank()) {
            dto.attachment = new ChatAnexoDto(mensagem.getAnexoNome(), mensagem.getAnexoTipo(), mensagem.getAnexoTamanho(), mensagem.getAnexoConteudo());
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

    private boolean visivelParaUsuario(ChatMensagem mensagem, String usuarioMatricula) {
        ChatConversaLimpa registro = buscarLimpeza(mensagem, usuarioMatricula);
        if (registro == null || registro.getUltimoIdLimpo() == null) {
            return true;
        }
        return mensagem.getIdchatmensagem() > registro.getUltimoIdLimpo();
    }

    private ChatConversaLimpa buscarLimpeza(ChatMensagem mensagem, String usuarioMatricula) {
        if ("private".equals(mensagem.getTipo())) {
            String outro = usuarioMatricula.equals(mensagem.getRemetenteMatricula())
                    ? safe(mensagem.getDestinatarioMatricula())
                    : safe(mensagem.getRemetenteMatricula());
            if (outro.isBlank()) {
                return null;
            }
            return chatConversaLimpaRepository.buscarPrivado(mensagem.getUnidade(), usuarioMatricula, outro);
        }
        return chatConversaLimpaRepository.buscarSala(mensagem.getUnidade(), usuarioMatricula, safeOrDefault(mensagem.getSala(), "geral"));
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
