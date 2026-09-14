package com.mindora.blog.application;

import com.mindora.blog.domain.BlogArticle;
import java.util.UUID;

public interface ArticleKnowledgePort {
    ArticleKnowledgeState upsert(BlogArticle article);

    ArticleKnowledgeState disable(BlogArticle article);

    record ArticleKnowledgeState(UUID documentId, String indexStatus) {
    }

    static ArticleKnowledgePort noop() {
        return new ArticleKnowledgePort() {
            @Override
            public ArticleKnowledgeState upsert(BlogArticle article) {
                return new ArticleKnowledgeState(article.knowledgeDocumentId(), article.knowledgeIndexStatus());
            }

            @Override
            public ArticleKnowledgeState disable(BlogArticle article) {
                return new ArticleKnowledgeState(article.knowledgeDocumentId(), article.knowledgeIndexStatus());
            }
        };
    }
}
