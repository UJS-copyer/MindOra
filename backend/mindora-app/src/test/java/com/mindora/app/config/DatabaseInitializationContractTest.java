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

    @Test
    void stage36IdentityTokenGroundworkMigrationDefinesSchemaAndBackfill() throws IOException {
        String migration = readResource("db/migration/V9__stage_3_6_identity_token_groundwork.sql");

        assertTrue(migration.contains("ALTER TABLE user_account"));
        assertTrue(migration.contains("token_version"));
        assertTrue(migration.contains("CREATE TABLE auth_refresh_token"));
        assertTrue(migration.contains("uq_refresh_hash"));
        assertTrue(migration.contains("INSERT INTO auth_account"));
        assertTrue(migration.contains("NOT EXISTS"));
    }

    private String readResource(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertTrue(input != null, "Missing resource: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
