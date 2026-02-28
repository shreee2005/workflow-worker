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

    @Bean
    public Queue retry5sQueue(){
        return QueueBuilder.durable("workflow.retry.5s")
                .withArgument("x-message-ttl" , 5000)
                .withArgument("x-dead-letter-exchange" , "")
                .withArgument("x-dead-letter-routing-key" , "workflow.tasks")
                .build();
    }
    @Bean
    public Queue retry10sQueue() {
        return QueueBuilder.durable("workflow.retry.10s")
                .withArgument("x-message-ttl", 10000)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", "workflow.tasks")
                .build();
    }

    @Bean
    public Queue retry20sQueue() {
        return QueueBuilder.durable("workflow.retry.20s")
                .withArgument("x-message-ttl", 20000)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", "workflow.tasks")
                .build();
    }

    @Bean
    public Queue retry40sQueue() {
        return QueueBuilder.durable("workflow.retry.40s")
                .withArgument("x-message-ttl", 40000)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", "workflow.tasks")
                .build();
    }

}
