package com.ainote.module.ai.consumer;

import com.ainote.module.ai.entity.AiTask;
import com.ainote.module.ai.mapper.AiTaskMapper;
import com.ainote.module.ai.message.AiAsyncMessage;
import com.ainote.module.ai.service.AiService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * AI 异步任务消费者：
 * <ul>
 *   <li>手动 ACK：成功后才确认，避免消息丢失；</li>
 *   <li>幂等：按 taskId 抢占(PENDING/PROCESSING→PROCESSING)，重复投递时抢占失败直接跳过；</li>
 *   <li>重试：失败后 NACK 重入队，重试次数持久化在任务记录；</li>
 *   <li>死信：重试耗尽后 NACK 不重入队，交由 Broker 转入死信队列兜底。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAsyncConsumer {

    /** 最大尝试次数（含首次） */
    private static final int MAX_ATTEMPTS = 3;

    private final AiTaskMapper taskMapper;
    private final AiService aiService;

    /**
     * 监听任务队列；ackMode=MANUAL 表示由业务决定 ack/nack。
     */
    @RabbitListener(queues = "${mq.ai-task-queue}", ackMode = "MANUAL")
    public void handle(AiAsyncMessage msg, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (msg == null || msg.getTaskId() == null) {
            ack(channel, deliveryTag);
            return;
        }
        AiTask task = taskMapper.selectById(msg.getTaskId());
        // 任务不存在或已到达终态(成功/失败) → 视为已处理，幂等去重
        if (task == null || isTerminal(task.getStatus())) {
            ack(channel, deliveryTag);
            return;
        }
        // 抢占为处理中；抢占失败说明该任务已被其他消费/重复投递处理，跳过
        if (taskMapper.tryProcess(task.getId()) == 0) {
            ack(channel, deliveryTag);
            return;
        }
        try {
            String result = aiService.execute(
                    task.getUserId(), task.getNoteId(), task.getAction(),
                    task.getTitle(), task.getContent(), task.getQuestion());
            finish(task.getId(), AiTask.STATUS_SUCCESS, result, null, task.getRetryCount());
            ack(channel, deliveryTag);
        } catch (Throwable e) {
            log.warn("AI 异步任务执行失败，taskId={}：{}", task.getId(), e.getMessage());
            int attempt = safeRetryCount(task) + 1;
            if (attempt >= MAX_ATTEMPTS) {
                finish(task.getId(), AiTask.STATUS_FAILED, null,
                        truncate(e.getMessage()), attempt);
                // 重试耗尽：NACK 不重入队 → 消息转入死信队列兜底
                nack(channel, deliveryTag, false);
            } else {
                // 重试：先把任务恢复为「待处理」，保证下次投递仍能从 PENDING 抢占
                finish(task.getId(), AiTask.STATUS_PENDING, null,
                        truncate(e.getMessage()), attempt);
                // NACK 重入队(requeue=true) → 重新投递重试
                nack(channel, deliveryTag, true);
            }
        }
    }

    private boolean isTerminal(Integer status) {
        return status != null
                && (status == AiTask.STATUS_SUCCESS || status == AiTask.STATUS_FAILED);
    }

    private int safeRetryCount(AiTask task) {
        return task.getRetryCount() == null ? 0 : task.getRetryCount();
    }

    /** 回写任务状态/结果（updateById 仅更新非空字段） */
    private void finish(Long taskId, int status, String result, String errorMsg, int retryCount) {
        AiTask update = new AiTask();
        update.setId(taskId);
        update.setStatus(status);
        if (result != null) {
            update.setResult(result);
        }
        if (errorMsg != null) {
            update.setErrorMsg(errorMsg);
        }
        update.setRetryCount(retryCount);
        taskMapper.updateById(update);
    }

    private void ack(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("MQ ack 失败", e);
        }
    }

    private void nack(Channel channel, long deliveryTag, boolean requeue) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
        } catch (IOException e) {
            log.error("MQ nack 失败", e);
        }
    }

    private String truncate(String msg) {
        if (msg == null) {
            return "AI 处理失败";
        }
        return msg.length() <= 500 ? msg : msg.substring(0, 500);
    }
}