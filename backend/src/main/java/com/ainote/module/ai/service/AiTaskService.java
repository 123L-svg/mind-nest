package com.ainote.module.ai.service;

import com.ainote.module.ai.entity.AiTask;
import com.ainote.module.ai.mapper.AiTaskMapper;
import com.ainote.module.ai.message.AiAsyncMessage;
import com.ainote.common.mq.MqProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 异步任务服务：生产者。
 * <p>提交任务：写入任务记录（待处理）→ 立即返回 taskId → 发布消息到 MQ，由消费者异步执行并回写。
 * 返回快、可削峰，是「生产与处理解耦」的入口。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskService {

    private final AiTaskMapper taskMapper;
    private final RabbitTemplate rabbitTemplate;
    private final MqProperties mqProps;

    /**
     * 提交异步任务并发布 MQ 消息。
     *
     * @return 任务ID（对外 taskId，可轮询状态）
     */
    public Long submit(AiTask task) {
        task.setStatus(AiTask.STATUS_PENDING);
        task.setRetryCount(0);
        taskMapper.insert(task);

        AiAsyncMessage msg = new AiAsyncMessage(
                task.getId(), task.getUserId(), task.getAction(),
                task.getTitle(), task.getContent(), task.getQuestion(), task.getParams());
        try {
            rabbitTemplate.convertAndSend(
                    mqProps.getAiTaskExchange(), mqProps.getAiTaskRoutingKey(), msg);
        } catch (Exception e) {
            // 发布失败：将任务标记为失败，避免留下永远无人处理的孤儿任务，再抛出
            log.error("AI 异步任务发布 MQ 失败，taskId={}", task.getId(), e);
            fail(task.getId(), "消息发布失败：" + e.getMessage());
            throw e;
        }
        log.info("AI 异步任务已入队：taskId={} action={}", task.getId(), task.getAction());
        return task.getId();
    }

    private void fail(Long taskId, String errorMsg) {
        AiTask update = new AiTask();
        update.setId(taskId);
        update.setStatus(AiTask.STATUS_FAILED);
        update.setErrorMsg(errorMsg != null && errorMsg.length() > 500
                ? errorMsg.substring(0, 500) : errorMsg);
        taskMapper.updateById(update);
    }

    /**
     * 查询任务（用于状态轮询/结果回调）
     */
    public AiTask getById(Long taskId) {
        return taskMapper.selectById(taskId);
    }
}