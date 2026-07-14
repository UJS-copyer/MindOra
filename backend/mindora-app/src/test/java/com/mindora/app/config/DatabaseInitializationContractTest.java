package com.mindora.app.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DatabaseInitializationContractTest {
    @Test
    void stageOneMigrationDefinesBlogContentTables() throws IOException {
        String migration = readResource("db/migration/V1__stage_1_content_schema.sql");

        assertTrue(migration.contains("CREATE TABLE category"));
        assertTrue(migration.contains("CREATE TABLE tag"));
        assertTrue(migration.contains("CREATE TABLE blog_article"));
        assertTrue(migration.contains("CREATE TABLE blog_article_tag"));
    }

    @Test
    void localSeedContainsPublishedArticleAndTaxonomy() throws IOException {
        String seed = readResource("db/seed/dev-content.sql");

        assertTrue(seed.contains("INSERT INTO category"));
        assertTrue(seed.contains("INSERT INTO tag"));
        assertTrue(seed.contains("INSERT INTO blog_article"));
        assertTrue(seed.contains("published"));
    }

    private String readResource(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertTrue(input != null, "Missing resource: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
