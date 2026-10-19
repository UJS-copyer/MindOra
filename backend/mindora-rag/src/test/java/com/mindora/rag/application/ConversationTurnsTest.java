package com.mindora.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindora.common.exception.BusinessException;
import com.mindora.rag.application.port.RagAdapters.CallerIdentityPort;
import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConversationTurnsTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";
    private static final RagAnswerOutcome OUTCOME =
            new RagAnswerOutcome("答案", List.of(), null, false, null, null, false, null, "test-model", Instant.now());

    private final ChatConversationService conversations = mock(ChatConversationService.class);
    private final AtomicReference<String> currentUser = new AtomicReference<>(USER);
    private final CallerIdentityPort identity = () -> Optional.ofNullable(currentUser.get());
    private final ConversationTurns turns = new ConversationTurns(conversations, identity);

    @Test
    void anonymousResolveIsEmptyAndRecordIsSkipped() {
        currentUser.set(null);

        ConversationTurns.Resolved resolved = turns.resolve("any-id");

        assertThat(resolved.valid()).isFalse();
        assertThat(resolved.requestedId()).isNull();
        assertThat(resolved.history()).isEmpty();
        assertThat(turns.recordQuietly(resolved, "问", OUTCOME)).isNull();
        verify(conversations, never()).recordTurn(any(), anyString(), anyString(), any());
    }

    @Test
    void foreignOrMissingConversationIsTreatedAsNoConversation() {
        when(conversations.resolveHistoryIfOwned("other-id", USER)).thenReturn(Optional.empty());

        ConversationTurns.Resolved resolved = turns.resolve("other-id");

        assertThat(resolved.valid()).isFalse();
        assertThat(resolved.requestedId()).isEqualTo("other-id");
        assertThat(resolved.history()).isEmpty();
    }

    @Test
    void ownedConversationReturnsTrimmedHistory() {
        List<ChatMessage> history = List.of(ChatMessage.user("上一问"), ChatMessage.assistant("上一答"));
        when(conversations.resolveHistoryIfOwned("mine", USER)).thenReturn(Optional.of(history));

        ConversationTurns.Resolved resolved = turns.resolve("mine");

        assertThat(resolved.valid()).isTrue();
        assertThat(resolved.requestedId()).isEqualTo("mine");
        assertThat(resolved.history()).containsExactlyElementsOf(history);
    }

    @Test
    void invalidRequestedIdIsDroppedSoRecordCreatesNewConversation() {
        when(conversations.resolveHistoryIfOwned("bogus", USER)).thenReturn(Optional.empty());
        when(conversations.recordTurn(isNull(), anyString(), anyString(), any())).thenReturn("new-id");

        ConversationTurns.Resolved resolved = turns.resolve("bogus");
        String conversationId = turns.record(resolved, "新问题", OUTCOME);

        assertThat(conversationId).isEqualTo("new-id");
        verify(conversations).recordTurn(null, USER, "新问题", OUTCOME);
    }

    @Test
    void recordFailureIsSwallowedByQuietVariant() {
        when(conversations.recordTurn(any(), anyString(), anyString(), any()))
                .thenThrow(new BusinessException("chat_conversation_not_found", "gone"));

        ConversationTurns.Resolved resolved = new ConversationTurns.Resolved(null, false, List.of());

        assertThat(turns.recordQuietly(resolved, "问", OUTCOME)).isNull();
    }

    @Test
    void effectiveIdOnlyEchoesOwnedConversation() {
        assertThat(turns.effectiveId(new ConversationTurns.Resolved("mine", true, List.of())))
                .isEqualTo("mine");
        assertThat(turns.effectiveId(new ConversationTurns.Resolved("bogus", false, List.of())))
                .isNull();
        assertThat(turns.effectiveId(null)).isNull();
    }
}
