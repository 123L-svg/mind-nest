package com.ainote.common.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

/**
 * RabbitMQ 拓扑声明：
 * <ul>
 *   <li>工作队列采用 Direct 直连交换机 + 路由键转发；</li>
 *   <li>工作队列绑定死信交换机(DLX)，业务消费重试耗尽后 NACK 不重入队，由 Broker 转入死信队列兜底；</li>
 *   <li>统一使用 Jackson2JsonMessageConverter，生产、消费收发强类型对象。</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class RabbitConfig {

    private final MqProperties props;

    /** 任务直连交换机 */
    @Bean
    public DirectExchange aiTaskExchange() {
        return new DirectExchange(props.getAiTaskExchange(), true, false);
    }

    /** 工作队列：绑定死信交换机，消息重试耗尽后转入死信 */
    @Bean
    public Queue aiTaskQueue() {
        return QueueBuilder.durable(props.getAiTaskQueue())
                .withArgument("x-dead-letter-exchange", props.getAiTaskDlxExchange())
                .withArgument("x-dead-letter-routing-key", props.getAiTaskDlxRoutingKey())
                .build();
    }

    /** 死信交换机（Direct） */
    @Bean
    public DirectExchange aiTaskDlxExchange() {
        return new DirectExchange(props.getAiTaskDlxExchange(), true, false);
    }

    /** 死信队列：消费失败兜底，供人工/对账处理 */
    @Bean
    public Queue aiTaskDlxQueue() {
        return QueueBuilder.durable(props.getAiTaskDlxQueue()).build();
    }

    /** 工作队列绑定 */
    @Bean
    public Binding aiTaskBinding() {
        return BindingBuilder.bind(aiTaskQueue())
                .to(aiTaskExchange())
                .with(props.getAiTaskRoutingKey());
    }

    /** 死信队列绑定 */
    @Bean
    public Binding aiTaskDlxBinding() {
        return BindingBuilder.bind(aiTaskDlxQueue())
                .to(aiTaskDlxExchange())
                .with(props.getAiTaskDlxRoutingKey());
    }

    /** 使用 Jackson 序列化消息体，收发对象时自动转换 */
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}