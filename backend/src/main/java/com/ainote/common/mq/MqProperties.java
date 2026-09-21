package com.ainote.common.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 异步任务 MQ 拓扑配置（交换机/队列/路由键）
 */
@Data
@Component
@ConfigurationProperties(prefix = "mq")
public class MqProperties {

    /** 任务交换机（Direct） */
    private String aiTaskExchange = "ai.task.exchange";

    /** 任务队列（消费端为工作队列） */
    private String aiTaskQueue = "ai.task.queue";

    /** 任务路由键 */
    private String aiTaskRoutingKey = "ai.task";

    /** 死信交换机（消息重试耗尽后转入） */
    private String aiTaskDlxExchange = "ai.task.dlx.exchange";

    /** 死信路由键 */
    private String aiTaskDlxRoutingKey = "ai.task.dead";

    /** 死信队列 */
    private String aiTaskDlxQueue = "ai.task.dead.queue";
}