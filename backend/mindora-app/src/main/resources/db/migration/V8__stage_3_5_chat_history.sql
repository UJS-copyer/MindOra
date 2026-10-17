-- Stage 3.5：T6 会话历史（登录用户的多轮问答持久化）。
-- 匿名访客不落库（沿用 design-04 权限模型：访客只用短期会话，无长期历史）。
-- 方案与取舍见 docs/stage-3.5-implementation-plan-cn.md §3。

CREATE TABLE chat_conversation (
    id CHAR(32) NOT NULL,
    user_id CHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,
    message_count INT NOT NULL DEFAULT 0,
    last_message_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_chat_conversation_user (user_id, last_message_at),
    CONSTRAINT fk_chat_conversation_user FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE chat_message (
    id CHAR(32) NOT NULL,
    conversation_id CHAR(32) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    citations_json MEDIUMTEXT NULL,
    status VARCHAR(32) NOT NULL,
    meta_json VARCHAR(1024) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_message_conversation (conversation_id, created_at),
    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id)
        REFERENCES chat_conversation (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
