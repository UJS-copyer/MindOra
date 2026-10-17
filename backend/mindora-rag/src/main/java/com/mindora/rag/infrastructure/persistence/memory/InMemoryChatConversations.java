package com.mindora.rag.infrastructure.persistence.memory;

import com.mindora.common.exception.BusinessException;
import com.mindora.rag.application.port.RagAdapters.ChatConversationRepository;
import com.mindora.rag.application.port.RagAdapters.ChatConversationRepository.ConversationSummary;
import com.mindora.rag.application.port.RagAdapters.StoredMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话历史仓储的内存实现：单测与 test profile 使用（测试数据源约定：不连 MySQL）。
 *
 * <p>与 JDBC 实现同契约（归属校验、软删、排序、计数维护），契约由单元测试共同覆盖；
 * JDBC 路径靠实机验收兜底（单人项目不为它搭 testcontainer）。
 */
public class InMemoryChatConversations implements ChatConversationRepository {
    private final Map<String, ConversationRow> conversations = new LinkedHashMap<>();
    private final Map<String, List<MessageRow>> messages = new LinkedHashMap<>();

    @Override
    public String create(String userId, String title, Instant now) {
        String id = UUID.randomUUID().toString();
        conversations.put(id, new ConversationRow(id, userId, title, 0, null, now, now, false));
        messages.put(id, new ArrayList<>());
        return id;
    }

    @Override
    public void appendMessage(
            String conversationId, String userId, String role, String content,
            String citationsJson, String status, String metaJson, Instant now) {
        ConversationRow conversation = requireConversation(conversationId, userId);
        messages.get(conversationId).add(
                new MessageRow(UUID.randomUUID().toString(), role, content, citationsJson, status, metaJson, now));
        conversations.put(conversationId, new ConversationRow(
                conversation.id(), conversation.userId(), conversation.title(),
                conversation.messageCount() + 1, now, conversation.createdAt(), now, conversation.deleted()));
    }

    @Override
    public List<StoredMessage> recentMessages(String conversationId, String userId, int limit) {
        List<MessageRow> all = messages.getOrDefault(conversationId, List.of());
        if (!visible(conversationId, userId) || all.isEmpty()) {
            return List.of();
        }
        return all.stream()
                .sorted(Comparator.comparing(MessageRow::createdAt))
                .skip(Math.max(0, all.size() - limit))
                .map(row -> new StoredMessage(row.role(), row.content(), row.createdAt().toEpochMilli()))
                .toList();
    }

    @Override
    public boolean existsForUser(String conversationId, String userId) {
        return visible(conversationId, userId);
    }

    @Override
    public List<ConversationSummary> listConversations(String userId, int limit) {
        return conversations.values().stream()
                .filter(row -> row.userId().equals(userId) && !row.deleted())
                .sorted(Comparator.comparing(
                                ConversationRow::lastMessageAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .reversed())
                .limit(limit)
                .map(row -> new ConversationSummary(
                        row.id(), row.title(), row.messageCount(), row.lastMessageAt(), row.createdAt()))
                .toList();
    }

    @Override
    public List<StoredMessage> messages(String conversationId, String userId) {
        if (!visible(conversationId, userId)) {
            return List.of();
        }
        return messages.getOrDefault(conversationId, List.of()).stream()
                .sorted(Comparator.comparing(MessageRow::createdAt))
                .map(row -> new StoredMessage(row.role(), row.content(), row.createdAt().toEpochMilli()))
                .toList();
    }

    @Override
    public boolean softDelete(String conversationId, String userId) {
        ConversationRow conversation = conversations.get(conversationId);
        if (conversation == null || !conversation.userId().equals(userId) || conversation.deleted()) {
            return false;
        }
        conversations.put(conversationId, new ConversationRow(
                conversation.id(), conversation.userId(), conversation.title(),
                conversation.messageCount(), conversation.lastMessageAt(),
                conversation.createdAt(), Instant.now(), true));
        return true;
    }

    private boolean visible(String conversationId, String userId) {
        ConversationRow conversation = conversations.get(conversationId);
        return conversation != null && !conversation.deleted() && conversation.userId().equals(userId);
    }

    private ConversationRow requireConversation(String conversationId, String userId) {
        ConversationRow conversation = conversations.get(conversationId);
        if (conversation == null || !conversation.userId().equals(userId) || conversation.deleted()) {
            throw new BusinessException("chat_conversation_not_found", "Conversation not found");
        }
        return conversation;
    }

    private record ConversationRow(
            String id, String userId, String title, int messageCount,
            Instant lastMessageAt, Instant createdAt, Instant updatedAt, boolean deleted) {
    }

    private record MessageRow(
            String id, String role, String content, String citationsJson,
            String status, String metaJson, Instant createdAt) {
    }
}
