package com.mindora.knowledge.infrastructure.adapter;

import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.KnowledgeHash;
import com.mindora.knowledge.application.MarkdownDocumentParser;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import com.mindora.knowledge.domain.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

public class GiteeApiRepositoryAdapter implements KnowledgeAdapters.GiteeRepositoryPort {
    private final RestClient restClient;
    private final String defaultAccessToken;
    private final MarkdownDocumentParser parser = new MarkdownDocumentParser();

    public GiteeApiRepositoryAdapter(String baseUrl) {
        this(baseUrl, null);
    }

    public GiteeApiRepositoryAdapter(String baseUrl, String defaultAccessToken) {
        this.defaultAccessToken = defaultAccessToken;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? "https://gitee.com/api/v5" : baseUrl)
                .build();
    }

    @Override
    public List<KnowledgeAdapters.RemoteFile> listMarkdownFiles(DataSource source) {
        Repository repository = parseRepository(source.repositoryUrl());
        List<KnowledgeAdapters.RemoteFile> files = new ArrayList<>();
        fetchPath(repository, source, source.rootPath(), files);
        return List.copyOf(files);
    }

    @SuppressWarnings("unchecked")
    private void fetchPath(Repository repository, DataSource source, String path, List<KnowledgeAdapters.RemoteFile> files) {
        Object response = restClient.get()
                .uri(uri -> contentsUri(uri, repository, path, source))
                .retrieve()
                .body(Object.class);
        if (response instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String type = string(map.get("type"));
                    String itemPath = string(map.get("path"));
                    if ("dir".equals(type)) {
                        fetchPath(repository, source, itemPath, files);
                    } else if (itemPath != null && itemPath.toLowerCase(Locale.ROOT).endsWith(".md")) {
                        fetchPath(repository, source, itemPath, files);
                    }
                }
            }
            return;
        }
        if (response instanceof Map<?, ?> map) {
            String filePath = string(map.get("path"));
            if (filePath == null || !filePath.toLowerCase(Locale.ROOT).endsWith(".md")) {
                return;
            }
            String content = decodeContent(string(map.get("content")));
            MarkdownDocumentParser.ParsedDocument parsed = parser.parse(filePath, content);
            String sha = string(map.get("sha"));
            files.add(new KnowledgeAdapters.RemoteFile(
                    filePath, parsed.title(), content, sha == null ? KnowledgeHash.sha256(content) : sha,
                    Map.of("source", "gitee"), parsed.imageReferences()));
            return;
        }
        throw new BusinessException("gitee_response_invalid", "Gitee returned an unsupported response");
    }

    private java.net.URI contentsUri(UriBuilder uri, Repository repository, String path, DataSource source) {
        UriBuilder builder = uri.path("/repos/{owner}/{repo}/contents");
        if (path != null && !path.isBlank()) {
            builder.path("/");
            builder.pathSegment(path.split("/"));
        }
        builder.queryParam("ref", source.branch());
        String accessToken = source.accessToken() == null || source.accessToken().isBlank()
                ? defaultAccessToken
                : source.accessToken();
        if (accessToken != null && !accessToken.isBlank()) {
            builder.queryParam("access_token", accessToken);
        }
        return path == null || path.isBlank()
                ? builder.build(repository.owner(), repository.repo())
                : builder.build(repository.owner(), repository.repo());
    }

    private String decodeContent(String content) {
        if (content == null) {
            return "";
        }
        return new String(Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8);
    }

    private Repository parseRepository(String url) {
        String normalized = url == null ? "" : url.trim();
        int marker = normalized.indexOf("gitee.com/");
        if (marker >= 0) {
            normalized = normalized.substring(marker + "gitee.com/".length());
        }
        normalized = normalized.replace(".git", "");
        String[] parts = normalized.split("/");
        if (parts.length < 2) {
            throw new BusinessException("gitee_repository_invalid", "Gitee repository URL is invalid");
        }
        return new Repository(parts[0], parts[1]);
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record Repository(String owner, String repo) {
    }
}
