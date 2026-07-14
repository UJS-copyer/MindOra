package com.mindora.blog.domain;

public enum ArticleStatus {
    DRAFT("draft"),
    PUBLISHED("published"),
    UNPUBLISHED("unpublished");

    private final String value;

    ArticleStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
