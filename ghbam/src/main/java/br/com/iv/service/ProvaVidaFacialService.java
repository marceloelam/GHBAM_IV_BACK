package br.com.iv.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.iv.service.ReconhecimentoFacialService.ResultadoPose;
import br.com.iv.service.ReconhecimentoFacialService.ResultadoValidacao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Prova de vida do reset de senha por reconhecimento facial.
 *
 * <p>O servidor sorteia a ordem das etapas (virar para um lado, olhar de frente,
 * virar para o outro). O quadro "de frente" precisa bater com a foto cadastrada;
 * os laterais precisam ser a mesma pessoa daquele quadro com a cabeca realmente
 * girada para lados opostos (pose do CompreFace). Uma foto parada nao produz
 * esse giro. O desafio expira em poucos minutos e so pode ser usado uma vez.</p>
 */
@ApplicationScoped
public class ProvaVidaFacialService {

    public static final String ESQUERDA = "ESQUERDA";
    public static final String FRENTE = "FRENTE";
    public static final String DIREITA = "DIREITA";

    private static final Logger LOGGER = Logger.getLogger(ProvaVidaFacialService.class.getName());
    private static final long VALIDADE_SEGUNDOS = 120;
    private static final int MAX_DESAFIOS_PENDENTES = 5000;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Desafio> desafios = new ConcurrentHashMap<>();

    @Inject
    ReconhecimentoFacialService reconhecimentoFacialService;

    // Angulos em graus do yaw devolvido pelo plugin "pose" do CompreFace.
    // O yaw absoluto varia muito com a posicao da webcam; a protecao contra foto
    // esta no giro RELATIVO dos laterais. Este limite so barra perfil completo.
    @ConfigProperty(name = "ghbam.face.liveness.yaw-frontal-max", defaultValue = "45")
    double yawFrontalMax;

    @ConfigProperty(name = "ghbam.face.liveness.yaw-lateral-min", defaultValue = "12")
    double yawLateralMin;

    // Confianca minima de deteccao nos quadros laterais (rosto virado). A
    // identidade (frente x foto cadastrada) continua no padrao do CompreFace.
    @ConfigProperty(name = "ghbam.face.liveness.det-prob-lateral", defaultValue = "0.5")
    double limiarDeteccaoLateral;

    // Semelhanca minima entre os quadros da mesma sessao (mesma pessoa).
    @ConfigProperty(name = "ghbam.face.liveness.similaridade-sessao-min", defaultValue = "0.75")
    double similaridadeSessaoMin;

    public Desafio criar(String matricula) {
        removerExpirados();
        if (desafios.size() >= MAX_DESAFIOS_PENDENTES) {
            throw new IllegalStateException("Muitas validacoes faciais em andamento. Tente novamente em instantes.");
        }

        List<String> etapas = new ArrayList<>(List.of(ESQUERDA, FRENTE, DIREITA));
        Collections.shuffle(etapas, random);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Desafio desafio = new Desafio(id, normalizar(matricula), List.copyOf(etapas),
            Instant.now().plusSeconds(VALIDADE_SEGUNDOS));
        desafios.put(id, desafio);
        return desafio;
    }

    /**
     * Valida os quadros capturados. Falha da prova de vida lanca
     * IllegalArgumentException; o resultado devolvido diz respeito apenas a
     * identidade (quadro de frente x foto cadastrada).
     */
    public ResultadoValidacao validar(String desafioId, String matricula, List<String> imagens, String fotoCadastrada) {
        List<String> etapas = consumir(desafioId, matricula);
        if (imagens == null || imagens.size() != etapas.size()
            || imagens.stream().anyMatch(img -> img == null || img.isBlank())) {
            throw new IllegalArgumentException("Capture uma imagem para cada etapa da prova de vida.");
        }

        String frente = imagens.get(etapas.indexOf(FRENTE));
        ResultadoPose identidade = reconhecimentoFacialService.compararComPose(fotoCadastrada, frente);
        if (!identidade.ok()) {
            // Sem comparacao feita (rosto nao achado, servico fora): nao ha similaridade a mostrar.
            throw new IllegalArgumentException(identidade.mensagem().contains("No face")
                ? "Nao encontrei seu rosto na imagem de frente. Enquadre o rosto inteiro, com luz de frente, e tente novamente."
                : identidade.mensagem());
        }

        double threshold = reconhecimentoFacialService.getSimilarityThreshold();
        if (identidade.similaridade() < threshold) {
            return ResultadoValidacao.falha(identidade.similaridade(),
                "Rosto nao reconhecido para esta matricula. Similaridade "
                    + Math.round(identidade.similaridade() * 100) + "%; minimo exigido "
                    + Math.round(threshold * 100) + "%.");
        }
        LOGGER.info(String.format("[ProvaVida] etapa=FRENTE yaw=%.1f pitch=%.1f", identidade.yaw(), identidade.pitch()));
        if (Math.abs(identidade.yaw()) > yawFrontalMax) {
            throw new IllegalArgumentException(
                "Prova de vida nao confirmada: na etapa 'olhe de frente' o rosto estava virado. Tente novamente.");
        }

        Double yawPrimeiroLado = null;
        for (int i = 0; i < etapas.size(); i++) {
            String etapa = etapas.get(i);
            if (FRENTE.equals(etapa)) continue;

            ResultadoPose lateral = reconhecimentoFacialService.compararComPose(frente, imagens.get(i), limiarDeteccaoLateral);
            if (!lateral.ok()) {
                throw new IllegalArgumentException("Prova de vida nao confirmada: " + lateral.mensagem());
            }
            if (lateral.similaridade() < similaridadeSessaoMin) {
                throw new IllegalArgumentException(
                    "Prova de vida nao confirmada: as imagens das etapas nao sao da mesma pessoa.");
            }
            // Giro medido a partir da posicao "de frente" da propria pessoa, para nao
            // penalizar webcam fora do centro (acima, abaixo ou de lado).
            double giro = lateral.yaw() - identidade.yaw();
            LOGGER.info(String.format("[ProvaVida] etapa=%s yawFrente=%.1f yawLateral=%.1f giro=%.1f simSessao=%.2f",
                etapa, identidade.yaw(), lateral.yaw(), giro, lateral.similaridade()));
            if (Math.abs(giro) < yawLateralMin) {
                throw new IllegalArgumentException("Prova de vida nao confirmada: vire mais o rosto para a "
                    + (ESQUERDA.equals(etapa) ? "esquerda" : "direita") + " quando solicitado.");
            }
            if (yawPrimeiroLado == null) {
                yawPrimeiroLado = giro;
            } else if (Math.signum(yawPrimeiroLado) == Math.signum(giro)) {
                throw new IllegalArgumentException(
                    "Prova de vida nao confirmada: vire o rosto para um lado e depois para o outro, como indicado.");
            }
        }

        return ResultadoValidacao.sucesso(identidade.similaridade());
    }

    private List<String> consumir(String desafioId, String matricula) {
        Desafio desafio = desafioId == null ? null : desafios.remove(desafioId);
        if (desafio == null || Instant.now().isAfter(desafio.expiraEm())
            || !desafio.matricula().equals(normalizar(matricula))) {
            throw new IllegalArgumentException("Validacao facial expirada ou invalida. Comece novamente.");
        }
        return desafio.etapas();
    }

    private void removerExpirados() {
        Instant agora = Instant.now();
        desafios.values().removeIf(d -> agora.isAfter(d.expiraEm()));
    }

    private String normalizar(String matricula) {
        return matricula == null ? "" : matricula.trim();
    }

    public record Desafio(String id, String matricula, List<String> etapas, Instant expiraEm) {
        public long validadeSegundos() {
            return VALIDADE_SEGUNDOS;
        }
    }
}
