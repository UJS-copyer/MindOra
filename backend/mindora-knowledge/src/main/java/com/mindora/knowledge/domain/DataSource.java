package com.mindora.knowledge.domain;

import java.time.Instant;
import java.util.UUID;

public record DataSource(
        UUID id,
        String name,
        KnowledgeEnums.SourceType sourceType,
        String repositoryUrl,
        String branch,
        String accessToken,
        String rootPath,
        String defaultVisibility,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
    public DataSource {
        sourceType = sourceType == null ? KnowledgeEnums.SourceType.GITEE : sourceType;
        branch = branch == null || branch.isBlank() ? "master" : branch.trim();
        rootPath = rootPath == null ? "" : rootPath.trim();
        defaultVisibility = defaultVisibility == null || defaultVisibility.isBlank()
                ? "private"
                : defaultVisibility.trim();
    }

    public DataSource update(
            String name,
            String repositoryUrl,
            String branch,
            String accessToken,
            String rootPath,
            String defaultVisibility,
            boolean enabled,
            Instant updatedAt) {
        return new DataSource(
                id,
                name,
                sourceType,
                repositoryUrl,
                branch,
                accessToken,
                rootPath,
                defaultVisibility,
                enabled,
                createdAt,
                updatedAt);
    }
}
