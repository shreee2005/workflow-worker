package com.workflow_worker.demo.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();

        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);

        // prevent infinite redelivery loops
        factory.setDefaultRequeueRejected(false);

        // make behavior explicit and easier to control
        factory.setConcurrentConsumers(1);  // one consumer per instance
        factory.setPrefetchCount(1);        // process 1 message at a time

        return factory;
    }

    @Bean
    public Queue workflowTasksQueue() {
        // durable main queue, same name as before
        return QueueBuilder.durable("workflow.tasks").build();
    }

    @Bean
    public Queue workflowTasksDlq() {
        // durable DLQ, same name as before
        return QueueBuilder.durable("workflow.tasks.dlq").build();
    }
}
