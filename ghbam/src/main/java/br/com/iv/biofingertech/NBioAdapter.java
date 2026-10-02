package br.com.iv.biofingertech;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NBioAdapter {

    private static final Logger LOG = Logger.getLogger(NBioAdapter.class.getName());

    // Tentativa de usar o wrapper JNI do NBio se estiver disponível na classpath.
    // Caso contrário, reverte para heurística local (BiometricMatcher).

    public static boolean matchFIRs(String firToVerify, String firFromDb) {
        // garante que as DLLs foram carregadas
        try {
            NativeLibLoader.load();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Falha ao carregar bibliotecas nativas: " + e.getMessage());
        }

        // Tentar chamar uma classe do JAR JNI por reflexão (evita erro de compilação
        // se o JAR estiver ausente/corrompido). Busca por classes conhecidas.
        String[] candidateClassNames = new String[] {
            "NBioAPI", // possíveis nomes encontrados em demos
            "NBioAPI_JavaDemo",
            "com.nitgen.NBioAPI", // variações comuns
            "NBioBSPJNI"
        };

        for (String className : candidateClassNames) {
            try {
                Class<?> cls = Class.forName(className);
                // Procurar por um método de comparação simples (heurística): procurar 'Match' ou 'Verify'
                for (Method m : cls.getMethods()) {
                    String mn = m.getName().toLowerCase();
                    if (mn.contains("match") || mn.contains("verify") || mn.contains("compare")) {
                        // Tentar invocar com duas strings como fallback. Muitos demos usam estruturas
                        // complexas, então a chamada por reflexão pode falhar — tratamos e passamos adiante.
                        try {
                            Object instance = null;
                            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                                instance = cls.getDeclaredConstructor().newInstance();
                            }
                            Object res = null;
                            if (m.getParameterCount() == 2) {
                                res = m.invoke(instance, firToVerify, firFromDb);
                            } else if (m.getParameterCount() == 1) {
                                res = m.invoke(instance, firToVerify);
                            } else {
                                continue;
                            }
                            if (res instanceof Boolean) {
                                return (Boolean) res;
                            }
                            // Alguns métodos retornam scores (int/float)
                            if (res instanceof Number) {
                                double v = ((Number) res).doubleValue();
                                return v >= 0.9d; // limiar conservador
                            }
                        } catch (Throwable t) {
                            LOG.log(Level.FINE, "Chamada reflexiva falhou em " + className + ": " + t.getMessage());
                        }
                    }
                }
            } catch (ClassNotFoundException cnf) {
                // classe não encontrada, próximo candidato
            } catch (Throwable t) {
                LOG.log(Level.FINE, "Erro ao analisar classe " + className + ": " + t.getMessage());
            }
        }

        // Se chegamos aqui, não conseguimos usar o SDK nativo: usar heurística existente
        try {
            return BiometricMatcher.matchTwoFirStrings(firToVerify, firFromDb);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Falha na heurística de fallback: " + e.getMessage());
            return false;
        }
    }
}
