import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Serviço de voz do painel de chamada do GHBAM (Kokoro via sherpa-onnx).
 *
 * Roda separado do backend: carrega o modelo UMA vez e fica ouvindo só em
 * 127.0.0.1. Gera a frase em trechos (vírgula, ponto) e junta com pausas de
 * respiração, porque o Kokoro em português quase não pausa na vírgula.
 * Se este serviço cair, o backend tenta o modo por linha de comando e, por
 * fim, o painel usa a voz do navegador.
 *
 * Não registra o texto (contém nome de paciente). Nada é gravado em disco.
 *
 * Uso: java -cp "sherpa-onnx-jvm.jar;sherpa-onnx-native-lib-<so>.jar" ServidorVoz.java
 * Variáveis: VOZ_MODELO_DIR (pasta kokoro-multi-lang-v1_0), VOZ_PORTA (8099),
 * VOZ_INSTANCIAS (cópias do modelo, ~400 MB cada; padrão 1), VOZ_THREADS (padrão: núcleos / cópias)
 */
public class ServidorVoz {

    private static final int MAX_CARACTERES = 600;
    private static final Pattern TRECHO = Pattern.compile("[^,.;:!?]+[,.;:!?]*");

    // Várias cópias do modelo: chamadas simultâneas (várias unidades) são geradas em paralelo.
    // Cada cópia atende uma frase por vez; quem chega com todas ocupadas espera na fila.
    private static final BlockingQueue<OfflineTts> MODELOS = new LinkedBlockingQueue<>();
    private static int numVozes;
    private static int taxa;

    public static void main(String[] args) throws Exception {
        String dir = env("VOZ_MODELO_DIR", "/opt/tts/kokoro-multi-lang-v1_0");
        dir = dir.endsWith("/") || dir.endsWith("\\") ? dir : dir + "/";
        int porta = Integer.parseInt(env("VOZ_PORTA", "8099"));
        // Medido no servidor de homologação (8 núcleos virtuais): 1 cópia com 8 núcleos foi melhor
        // que 2 cópias com 4 (sozinha 4,2 s contra 6,6 s; 3 simultâneas 4,4/8,7/12,7 s contra
        // 6,8/9,3/14,0 s). Mais cópias só compensam com mais núcleos físicos.
        int instancias = Math.max(1, Integer.parseInt(env("VOZ_INSTANCIAS", "1")));
        // Núcleos por cópia: padrão = núcleos da máquina divididos entre as cópias.
        int threads = Integer.parseInt(env("VOZ_THREADS",
                String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors() / instancias))));

        long inicio = System.currentTimeMillis();
        OfflineTtsKokoroModelConfig kokoro = OfflineTtsKokoroModelConfig.builder()
                .setModel(dir + "model.onnx")
                .setVoices(dir + "voices.bin")
                .setTokens(dir + "tokens.txt")
                .setDataDir(dir + "espeak-ng-data")
                .setLang("pt-br")
                .build();
        OfflineTtsModelConfig modelo = OfflineTtsModelConfig.builder()
                .setKokoro(kokoro).setNumThreads(threads).setDebug(false).setProvider("cpu").build();
        OfflineTtsConfig config = OfflineTtsConfig.builder().setModel(modelo).setMaxNumSentences(1).build();
        for (int i = 0; i < instancias; i++) {
            MODELOS.add(new OfflineTts(config));
        }
        OfflineTts primeiro = MODELOS.peek();
        taxa = primeiro.getSampleRate();
        numVozes = primeiro.getNumSpeakers();
        System.out.printf("%d cópia(s) do modelo carregada(s) em %d ms (%d núcleos cada, %d Hz, %d vozes)%n",
                instancias, System.currentTimeMillis() - inicio, threads, taxa, numVozes);

        // Só na própria máquina: quem acessa é o backend do GHBAM.
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", porta), 32);
        servidor.createContext("/saude", t -> responder(t, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8)));
        servidor.createContext("/falar", ServidorVoz::falar);
        servidor.setExecutor(Executors.newFixedThreadPool(instancias + 2));
        servidor.start();
        System.out.println("Serviço de voz ouvindo em http://127.0.0.1:" + porta);
    }

    private static void falar(HttpExchange t) throws IOException {
        try {
            if (!"POST".equals(t.getRequestMethod())) {
                responder(t, 405, "text/plain", new byte[0]);
                return;
            }
            Map<String, String> q = consulta(t.getRequestURI().getRawQuery());
            int sid = Integer.parseInt(q.getOrDefault("sid", "42"));
            // Falado em trechos o Kokoro alonga o fim de cada um; 1.1 compensa sem acelerar demais.
            float velocidade = Float.parseFloat(q.getOrDefault("velocidade", "1.1"));
            String texto;
            try (InputStream in = t.getRequestBody()) {
                texto = new String(in.readNBytes(MAX_CARACTERES * 4 + 1), StandardCharsets.UTF_8).trim();
            }
            if (texto.isEmpty() || texto.length() > MAX_CARACTERES || sid < 0 || sid >= numVozes
                    || velocidade < 0.5f || velocidade > 2f) {
                responder(t, 400, "text/plain", new byte[0]);
                return;
            }
            long inicio = System.currentTimeMillis();
            byte[] wav = sintetizarComPausas(texto, sid, velocidade);
            System.out.printf("Locução gerada em %d ms%n", System.currentTimeMillis() - inicio);
            responder(t, 200, "audio/wav", wav);
        } catch (Exception e) {
            System.err.println("Falha na síntese: " + e.getClass().getSimpleName());
            responder(t, 500, "text/plain", new byte[0]);
        }
    }

    /**
     * Gera trecho a trecho e junta com pausas. Somadas às margens de cada trecho (30 + 80 ms),
     * a respiração fica em ~350 ms na vírgula, ~400 ms em ; e : e ~460 ms no ponto (medido).
     */
    // ---------------------------------------------------------------- cache de trechos
    // Entre chamadas quase tudo se repete ("Boa tarde,", "compareça ao Consultório 1,",
    // "no setor de Clínica Médica."); só o trecho do nome muda. Guardar os trechos prontos
    // faz cada chamada gerar, em geral, só o nome. Só em memória, limitado e com validade.
    private static final int MAX_TRECHOS_CACHE = 1000;          // ~60 MB no pior caso
    private static final long VALIDADE_TRECHO_MS = 30L * 60 * 1000;

    private record Trecho(byte[] pcm, long criadoEm) {
    }

    private static final Map<String, Trecho> CACHE_TRECHOS = new java.util.LinkedHashMap<>(256, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Trecho> maisAntigo) {
            return size() > MAX_TRECHOS_CACHE;
        }
    };

    private static byte[] trechoDoCache(String chave) {
        synchronized (CACHE_TRECHOS) {
            Trecho t = CACHE_TRECHOS.get(chave);
            if (t == null) return null;
            if (System.currentTimeMillis() - t.criadoEm() > VALIDADE_TRECHO_MS) {
                CACHE_TRECHOS.remove(chave);
                return null;
            }
            return t.pcm();
        }
    }

    private static void guardarTrecho(String chave, byte[] pcm) {
        synchronized (CACHE_TRECHOS) {
            CACHE_TRECHOS.put(chave, new Trecho(pcm, System.currentTimeMillis()));
        }
    }

    static byte[] sintetizarComPausas(String texto, int sid, float velocidade) throws InterruptedException {
        List<String> trechos = new ArrayList<>();
        Matcher m = TRECHO.matcher(texto);
        while (m.find()) {
            String trecho = m.group().trim();
            if (trecho.replaceAll("[,.;:!?\\s]", "").length() > 0) {
                trechos.add(trecho);
            }
        }

        // Primeiro o cache: se tudo já estiver pronto, nem ocupa o modelo.
        byte[][] partes = new byte[trechos.size()][];
        String[] chaves = new String[trechos.size()];
        List<Integer> faltando = new ArrayList<>();
        for (int i = 0; i < trechos.size(); i++) {
            chaves[i] = sid + "|" + velocidade + "|" + trechos.get(i);
            partes[i] = trechoDoCache(chaves[i]);
            if (partes[i] == null) faltando.add(i);
        }

        if (!faltando.isEmpty()) {
            // Pega uma cópia livre do modelo e devolve no fim (fila se todas ocupadas).
            OfflineTts tts = MODELOS.take();
            try {
                for (int i : faltando) {
                    GeneratedAudio audio = tts.generate(trechos.get(i), sid, velocidade);
                    partes[i] = pcmSemSilencioNasPontas(audio.getSamples());
                    guardarTrecho(chaves[i], partes[i]);
                }
            } finally {
                MODELOS.add(tts);
            }
        }
        System.out.printf("  %d de %d trechos vieram do cache%n", trechos.size() - faltando.size(), trechos.size());

        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (int i = 0; i < trechos.size(); i++) {
            pcm.writeBytes(partes[i]);
            if (i < trechos.size() - 1) {
                String trecho = trechos.get(i);
                char fim = trecho.charAt(trecho.length() - 1);
                int ms = fim == ',' ? 220 : (fim == ';' || fim == ':') ? 270 : 330;
                pcm.writeBytes(new byte[taxa * 2 * ms / 1000]);
            }
        }
        return wav(pcm.toByteArray());
    }

    private static byte[] pcmSemSilencioNasPontas(float[] s) {
        float limiar = 0.01f;
        int ini = 0, fim = s.length - 1;
        while (ini < fim && Math.abs(s[ini]) < limiar) ini++;
        while (fim > ini && Math.abs(s[fim]) < limiar) fim--;
        ini = Math.max(0, ini - taxa * 30 / 1000);     // 30 ms antes da fala
        fim = Math.min(s.length - 1, fim + taxa * 80 / 1000); // 80 ms de cauda
        ByteBuffer b = ByteBuffer.allocate((fim - ini + 1) * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = ini; i <= fim; i++) {
            b.putShort((short) Math.max(-32768, Math.min(32767, Math.round(s[i] * 32767f))));
        }
        return b.array();
    }

    private static byte[] wav(byte[] dados) {
        ByteBuffer h = ByteBuffer.allocate(44 + dados.length).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dados.length)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII)).put("fmt ".getBytes(StandardCharsets.US_ASCII))
                .putInt(16).putShort((short) 1).putShort((short) 1).putInt(taxa).putInt(taxa * 2)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dados.length).put(dados);
        return h.array();
    }

    private static void responder(HttpExchange t, int status, String tipo, byte[] corpo) throws IOException {
        t.getResponseHeaders().set("Content-Type", tipo);
        t.sendResponseHeaders(status, corpo.length == 0 ? -1 : corpo.length);
        try (OutputStream out = t.getResponseBody()) {
            out.write(corpo);
        }
    }

    private static Map<String, String> consulta(String raw) {
        Map<String, String> mapa = new HashMap<>();
        if (raw == null) return mapa;
        for (String par : raw.split("&")) {
            int i = par.indexOf('=');
            if (i > 0) mapa.put(par.substring(0, i), URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8));
        }
        return mapa;
    }

    private static String env(String nome, String padrao) {
        String v = System.getenv(nome);
        return v == null || v.isBlank() ? padrao : v;
    }
}
