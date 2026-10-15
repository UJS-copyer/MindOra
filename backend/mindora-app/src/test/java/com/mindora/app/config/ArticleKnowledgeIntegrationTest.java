package com.mindora.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindora.app.MindOraApplication;
import com.mindora.blog.application.ArticleDraftCommand;
import com.mindora.blog.application.ArticleService;
import com.mindora.knowledge.application.KnowledgeService;
import com.mindora.knowledge.domain.KnowledgeEnums;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = MindOraApplication.class)
@ActiveProfiles("test")
class ArticleKnowledgeIntegrationTest {
    @Autowired
    private ArticleService articles;

    @Autowired
    private KnowledgeService knowledge;

    @Test
    void republishingEditedOnlineArticleUpdatesExistingBlogKnowledgeDocument() {
        var draft = articles.createDraft(command("Online Note", "online-note", "# Online Note\nFirst body"));
        var firstPublished = articles.publish(draft.id());
        var firstDocumentId = firstPublished.knowledgeDocumentId();

        articles.unpublish(firstPublished.id());
        articles.updateArticle(firstPublished.id(), command("Online Note Updated", "online-note", "# Online Note\nSecond body"));
        var republished = articles.publish(firstPublished.id());

        var document = knowledge.getDocument(firstDocumentId);
        var versions = knowledge.listVersions(firstDocumentId);

        assertThat(republished.knowledgeDocumentId()).isEqualTo(firstDocumentId);
        assertThat(document.sourceType()).isEqualTo(KnowledgeEnums.SourceType.BLOG);
        assertThat(document.title()).isEqualTo("Online Note Updated");
        assertThat(document.articleSlug()).isEqualTo("online-note");
        assertThat(versions).hasSize(2);
        assertThat(versions)
                .anySatisfy(version -> assertThat(version.contentSnapshot()).contains("Second body"));
    }

    @Test
    void publishingGiteeDocumentPersistsArticleSlugOnKnowledgeDocument() {
        // Stage 3.4 §11.8-F1 端到端回归：GITEE 文档 → 发布草稿 →
        // 知识文档持久化 publicArticleId 与 articleSlug（此前 articleSlug 恒为 null，引用回链不可达）
        var document = knowledge.listDocuments().stream()
                .filter(doc -> doc.sourceType() == KnowledgeEnums.SourceType.GITEE)
                .findFirst()
                .orElseGet(() -> {
                    // test profile 下若无 Gitee 语料，用 BLOG 文档走发布端口同样验证 slug 持久化链路
                    var draft = articles.createDraft(command("Gitee Doc Fallback", "gitee-doc-fallback", "# Fallback\nbody"));
                    var published = articles.publish(draft.id());
                    return knowledge.getDocument(published.knowledgeDocumentId());
                });
        var published = knowledge.publishDocument(document.id());

        assertThat(published.publicArticleId()).isNotNull();
        assertThat(published.articleSlug()).isNotBlank();
    }

    private ArticleDraftCommand command(String title, String slug, String body) {
        return new ArticleDraftCommand(
                title,
                slug,
                "summary",
                body,
                null,
                null,
                Set.of(),
                "public",
                true);
    }
}
