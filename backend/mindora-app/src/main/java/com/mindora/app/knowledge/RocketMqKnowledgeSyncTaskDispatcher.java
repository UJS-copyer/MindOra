package com.mindora.app.knowledge;

import com.mindora.knowledge.application.port.KnowledgeAdapters.SyncTaskExecutorPort;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

public class RocketMqKnowledgeSyncTaskDispatcher implements SyncTaskExecutorPort {
    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;

    public RocketMqKnowledgeSyncTaskDispatcher(RocketMQTemplate rocketMQTemplate, String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
    }

    @Override
    public void execute(long taskId) {
        rocketMQTemplate.convertAndSend(topic, String.valueOf(taskId));
    }
}
