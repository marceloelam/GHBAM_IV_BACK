package br.com.iv.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class TextChunkerService {

    private static final int CHUNK_MIN_CHARS = 300;
    private static final int CHUNK_TARGET_CHARS = 1500;
    private static final int CHUNK_MAX_CHARS = 2500;
    private static final int OVERLAP_CHARS = 150;

    public List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        if (text.length() <= CHUNK_TARGET_CHARS) {
            chunks.add(text.trim());
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = findChunkEnd(text, start);
            if (end <= start) {
                end = Math.min(start + CHUNK_MAX_CHARS, text.length());
            }
            chunks.add(text.substring(start, end).trim());
            start = end - OVERLAP_CHARS;
            if (start >= text.length()) break;
            if (start < 0) start = 0;
        }

        return chunks;
    }

    private int findChunkEnd(String text, int start) {
        int targetEnd = start + CHUNK_TARGET_CHARS;
        if (targetEnd >= text.length()) return text.length();

        int end = text.lastIndexOf("\n\n", targetEnd);
        if (end > start + CHUNK_MIN_CHARS) return end;

        end = text.lastIndexOf("\n", targetEnd);
        if (end > start + CHUNK_MIN_CHARS) return end;

        end = text.lastIndexOf(". ", targetEnd);
        if (end > start + CHUNK_MIN_CHARS) return end + 1;

        end = text.lastIndexOf("; ", targetEnd);
        if (end > start + CHUNK_MIN_CHARS) return end + 1;

        end = text.lastIndexOf(", ", targetEnd);
        if (end > start + CHUNK_MIN_CHARS) return end + 1;

        return Math.min(start + CHUNK_MAX_CHARS, text.length());
    }

    public List<String> chunkTituloConteudo(String titulo, String conteudo) {
        String combined = titulo + "\n\n" + conteudo;
        return chunkText(combined);
    }
}
