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
        assertThat(versions).hasSize(2);
        assertThat(versions)
                .anySatisfy(version -> assertThat(version.contentSnapshot()).contains("Second body"));
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
