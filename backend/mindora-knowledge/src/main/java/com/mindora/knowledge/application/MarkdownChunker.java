package com.mindora.knowledge.application;

import com.mindora.knowledge.domain.ChunkingConfig;
import java.util.ArrayList;
import java.util.List;

public class MarkdownChunker {
    public List<String> chunk(String markdown, ChunkingConfig config) {
        String text = markdown == null ? "" : markdown.trim();
        if (text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        String[] sections = text.split("(?m)(?=^#{1,6}\\s+)");
        for (String section : sections) {
            appendSection(chunks, section.trim(), config);
        }
        return List.copyOf(chunks);
    }

    private void appendSection(List<String> chunks, String section, ChunkingConfig config) {
        if (section.isBlank()) {
            return;
        }
        int start = 0;
        while (start < section.length()) {
            int end = Math.min(section.length(), start + config.maximumSize());
            String chunk = section.substring(start, end).trim();
            if (chunk.length() >= config.minimumSize() || start == 0 || end == section.length()) {
                chunks.add(chunk);
            }
            if (end == section.length()) {
                break;
            }
            start = Math.max(start + 1, end - config.overlap());
        }
    }
}
