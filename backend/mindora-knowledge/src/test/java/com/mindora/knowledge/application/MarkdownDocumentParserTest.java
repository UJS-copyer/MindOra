package com.mindora.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownDocumentParserTest {
    private final MarkdownDocumentParser parser = new MarkdownDocumentParser();

    @Test
    void removesFrontmatterFromBodyAndExtractsImages() {
        MarkdownDocumentParser.ParsedDocument document = parser.parse(
                "notes/demo.md",
                """
                ---
                title: Demo Title
                tags: [rag]
                ---
                # Heading

                Body ![image](attachments/a.png)
                """);

        assertThat(document.title()).isEqualTo("Demo Title");
        assertThat(document.body()).doesNotContain("title: Demo Title");
        assertThat(document.body()).contains("# Heading");
        assertThat(document.imageReferences()).containsExactly("attachments/a.png");
    }

    @Test
    void fallsBackToFileNameWhenFrontmatterAndHeadingAreMissing() {
        MarkdownDocumentParser.ParsedDocument document = parser.parse("folder/local-note.md", "plain body");

        assertThat(document.title()).isEqualTo("local-note");
        assertThat(document.body()).isEqualTo("plain body");
    }
}
