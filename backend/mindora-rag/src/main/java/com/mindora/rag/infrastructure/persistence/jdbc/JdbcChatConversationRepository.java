package com.mindora.rag.infrastructure.persistence.jdbc;

import com.mindora.common.exception.BusinessException;
import com.mindora.common.id.PublicIds;
import com.mindora.rag.application.port.RagAdapters.ChatConversationRepository;
import com.mindora.rag.application.port.RagAdapters.ChatConversationRepository.ConversationSummary;
import com.mindora.rag.application.port.RagAdapters.StoredMessage;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 会话历史仓储的 JDBC 实现（表结构见 V8__stage_3_5_chat_history.sql）。
 *
 * <p>归属校验策略：所有读写都带 {@code user_id} 条件，归属不匹配按「不存在」处理——
 * 不区分「不存在/不是你的」，避免会话 id 枚举（方案 §3.3）。
 *
 * <p>并发约定：单人项目不处理同会话并发写，后写覆盖（appendMessage 的计数维护
 * 不是原子的 read-modify-write；真出现并发也只是计数偏小，不影响正确性关键路径）。
 */
public class JdbcChatConversationRepository implements ChatConversationRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcChatConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * userId 归一：身份端口给的是 JWT sub（36 位带连字符 UUID），表列沿用仓库 32 位惯例
     * （user_account.id 为 CHAR(32)）。两种形式都归一为 32 位，测试/调用方传哪种都能对上。
     */
    private static String normalizeUserId(String userId) {
        return userId == null ? null : userId.replace("-", "").toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String create(String rawUserId, String title, Instant now) {
        String userId = normalizeUserId(rawUserId);
        String id = PublicIds.toPublicId(UUID.randomUUID());
        jdbcTemplate.update(
                """
                INSERT INTO chat_conversation (id, user_id, title, message_count, last_message_at, created_at, updated_at, deleted)
                VALUES (?, ?, ?, 0, NULL, ?, ?, FALSE)
                """,
                id, userId, title, timestamp(now), timestamp(now));
        return id;
    }

    @Override
    public void appendMessage(
            String conversationId, String rawUserId, String role, String content,
            String citationsJson, String status, String metaJson, Instant now) {
        String userId = normalizeUserId(rawUserId);
        requireConversation(conversationId, userId);
        jdbcTemplate.update(
                """
                INSERT INTO chat_message (id, conversation_id, role, content, citations_json, status, meta_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                PublicIds.toPublicId(UUID.randomUUID()),
                conversationId, role, content, citationsJson, status, metaJson, timestamp(now));
        jdbcTemplate.update(
                """
                UPDATE chat_conversation
                SET message_count = message_count + 1, last_message_at = ?, updated_at = ?
                WHERE id = ? AND user_id = ? AND deleted = FALSE
                """,
                timestamp(now), timestamp(now), conversationId, userId);
    }

    @Override
    public List<StoredMessage> recentMessages(String conversationId, String rawUserId, int limit) {
        String userId = normalizeUserId(rawUserId);
        if (!visible(conversationId, userId)) {
            return List.of();
        }
        // 时间正序取最近 limit 条：先倒序取 limit 再反回正序，避免全表排序
        List<StoredMessage> reversed = jdbcTemplate.query(
                """
                SELECT role, content, created_at FROM chat_message
                WHERE conversation_id = ? ORDER BY created_at DESC, id DESC LIMIT ?
                """,
                (rs, rowNum) -> new StoredMessage(
                        rs.getString("role"), rs.getString("content"), instant(rs.getTimestamp("created_at")).toEpochMilli()),
                conversationId, limit);
        return List.copyOf(reversed.reversed());
    }

    @Override
    public boolean existsForUser(String conversationId, String rawUserId) {
        return visible(conversationId, normalizeUserId(rawUserId));
    }

    @Override
    public List<ConversationSummary> listConversations(String rawUserId, int limit) {
        String userId = normalizeUserId(rawUserId);
        return jdbcTemplate.query(
                """
                SELECT id, title, message_count, last_message_at, created_at FROM chat_conversation
                WHERE user_id = ? AND deleted = FALSE
                ORDER BY COALESCE(last_message_at, created_at) DESC LIMIT ?
                """,
                (rs, rowNum) -> new ConversationSummary(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getInt("message_count"),
                        instant(rs.getTimestamp("last_message_at")),
                        instant(rs.getTimestamp("created_at"))),
                userId, limit);
    }

    @Override
    public List<StoredMessage> messages(String conversationId, String rawUserId) {
        String userId = normalizeUserId(rawUserId);
        if (!visible(conversationId, userId)) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT role, content, created_at FROM chat_message
                WHERE conversation_id = ? ORDER BY created_at ASC, id ASC
                """,
                (rs, rowNum) -> new StoredMessage(
                        rs.getString("role"), rs.getString("content"), instant(rs.getTimestamp("created_at")).toEpochMilli()),
                conversationId);
    }

    @Override
    public boolean softDelete(String conversationId, String rawUserId) {
        String userId = normalizeUserId(rawUserId);
        return jdbcTemplate.update(
                "UPDATE chat_conversation SET deleted = TRUE, updated_at = ? WHERE id = ? AND user_id = ? AND deleted = FALSE",
                timestamp(Instant.now()), conversationId, userId) > 0;
    }

    /** 可见 = 存在 + 未软删 + 归属该用户。归属不匹配与不存在同样返回 false。 */
    private boolean visible(String conversationId, String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_conversation WHERE id = ? AND user_id = ? AND deleted = FALSE",
                Integer.class, conversationId, userId);
        return count != null && count > 0;
    }

    private void requireConversation(String conversationId, String userId) {
        if (!visible(conversationId, userId)) {
            throw new BusinessException("chat_conversation_not_found", "Conversation not found");
        }
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
