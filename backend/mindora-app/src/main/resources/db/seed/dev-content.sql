INSERT INTO category (id, name, created_at, updated_at)
VALUES
    ('11111111111111111111111111111111', 'Engineering', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('22222222222222222222222222222222', 'Mindfulness', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    updated_at = CURRENT_TIMESTAMP(6),
    deleted = FALSE;

INSERT INTO tag (id, name, created_at, updated_at)
VALUES
    ('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'Spring Boot', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'MindOra', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    updated_at = CURRENT_TIMESTAMP(6),
    deleted = FALSE;

INSERT INTO blog_article (
    id, title, slug, summary, body, category_id, status, visibility,
    read_count, created_at, updated_at, published_at
)
VALUES (
    '33333333333333333333333333333333',
    'MindOra 本地调试文章',
    'mindora-local-debug',
    '用于验证首页、文章列表和文章详情链路的示例文章。',
    '# MindOra 本地调试文章\n\n这是一篇由本地 seed 数据生成的示例文章。',
    '11111111111111111111111111111111',
    'published',
    'public',
    0,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    summary = VALUES(summary),
    body = VALUES(body),
    category_id = VALUES(category_id),
    status = VALUES(status),
    visibility = VALUES(visibility),
    published_at = VALUES(published_at),
    deleted = FALSE;

INSERT IGNORE INTO blog_article_tag (article_id, tag_id)
VALUES
    ('33333333333333333333333333333333', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'),
    ('33333333333333333333333333333333', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb');
