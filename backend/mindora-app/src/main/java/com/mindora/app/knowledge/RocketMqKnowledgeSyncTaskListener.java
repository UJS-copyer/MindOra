package com.mindora.app.knowledge;

import com.mindora.knowledge.application.KnowledgeService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mindora.knowledge.dispatcher", havingValue = "rocketmq")
@RocketMQMessageListener(
        topic = "${mindora.knowledge.sync-topic:mindora-knowledge-sync}",
        consumerGroup = "${mindora.knowledge.sync-consumer-group:mindora-knowledge-sync-consumer}")
public class RocketMqKnowledgeSyncTaskListener implements RocketMQListener<String> {
    private final KnowledgeService knowledgeService;

    public RocketMqKnowledgeSyncTaskListener(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public void onMessage(String message) {
        knowledgeService.executeTask(Long.parseLong(message));
    }
}
