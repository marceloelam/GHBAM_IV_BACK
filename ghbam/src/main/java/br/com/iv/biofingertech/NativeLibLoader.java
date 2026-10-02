package br.com.iv.biofingertech;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public final class NativeLibLoader {

    private static volatile boolean loaded = false;

    private NativeLibLoader() {}

    public static synchronized void load() throws IOException {
        if (loaded) return;

        String[] libs = {"NBioBSP.dll", "NBioBSPJNI.dll"};
        String resourceBase = "/lib2/";

        for (String lib : libs) {
            try (InputStream is = NativeLibLoader.class.getResourceAsStream(resourceBase + lib)) {
                if (is == null) continue; // se não existir no classpath, pula
                Path out = Paths.get(System.getProperty("java.io.tmpdir"), lib);
                Files.copy(is, out, StandardCopyOption.REPLACE_EXISTING);
                System.load(out.toAbsolutePath().toString());
            }
        }

        loaded = true;
    }
}
