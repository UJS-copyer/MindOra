package com.mindora.knowledge.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownDocumentParser {
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[[^]]*]\\(([^)]+)\\)");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^\\s{0,3}#{1,6}\\s+(.+?)\\s*$");

    public ParsedDocument parse(String path, String rawMarkdown) {
        String raw = rawMarkdown == null ? "" : rawMarkdown.replace("\r\n", "\n");
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        String body = raw;
        if (raw.startsWith("---\n")) {
            int end = raw.indexOf("\n---", 4);
            if (end >= 0) {
                String header = raw.substring(4, end);
                frontmatter.putAll(parseFrontmatter(header));
                body = raw.substring(Math.min(raw.length(), end + 4)).replaceFirst("^\\n", "");
            }
        }
        String title = string(frontmatter.get("title"));
        if (title == null || title.isBlank()) {
            Matcher heading = HEADING_PATTERN.matcher(body);
            title = heading.find() ? heading.group(1).trim() : fileName(path);
        }
        List<String> images = new ArrayList<>();
        Matcher imageMatcher = IMAGE_PATTERN.matcher(body);
        while (imageMatcher.find()) {
            images.add(imageMatcher.group(1).trim());
        }
        return new ParsedDocument(path, title, body.trim(), frontmatter, List.copyOf(images));
    }

    private Map<String, Object> parseFrontmatter(String header) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String line : header.split("\n")) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "Untitled";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    public record ParsedDocument(
            String sourcePath,
            String title,
            String body,
            Map<String, Object> frontmatter,
            List<String> imageReferences) {
    }
}
