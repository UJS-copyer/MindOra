package com.mindora.rag.infrastructure.adapter;

import com.mindora.common.exception.BusinessException;
import com.mindora.rag.application.port.RagAdapters.ChatClientPort;
import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.application.port.RagAdapters.ChatOptions;
import com.mindora.rag.application.port.RagAdapters.ChatReply;
import java.util.List;

/**
 * 未配置聊天模型时的降级实现。
 *
 * <p>语义与 knowledge 的 UnavailableKnowledgeAdapters 一致：调用即抛
 * {@code rag_chat_unavailable}，使 RagAnswerService 走「只返回检索片段」的降级路径，
 * 而不是让整个问答接口 500。这样测试环境（mindora.rag.chat.enabled=false）无需真实密钥。
 */
public class UnavailableRagAdapters {
    private UnavailableRagAdapters() {
    }

    public static class ChatClient implements ChatClientPort {
        @Override
        public String provider() {
            return "unavailable";
        }

        @Override
        public String model() {
            return "unavailable";
        }

        @Override
        public ChatReply chat(List<ChatMessage> messages, ChatOptions options) {
            throw new BusinessException(
                    "rag_chat_unavailable",
                    "RAG chat client is not configured in this runtime");
        }
    }
}
