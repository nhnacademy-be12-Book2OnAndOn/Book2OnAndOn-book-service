package org.nhnacademy.book2onandonbookservice.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMqConfig {

    public static final String SEARCH_SYNC_QUEUE = "book2.search.sync.queue.test";
    public static final String SEARCH_SYNC_EXCHANGE = "book2.search.sync.exchange.test";
    public static final String SEARCH_SYNC_ROUTING_KEY = "book2.search.sync.test";
    public static final String SEARCH_SYNC_DLQ_ROUTING_KEY="book2.search.sync.cancel.dlq.test";

    public static final String SEARCH_SYNC_DLQ = "book2.search.sync.dlq.test";
    public static final String SEARCH_SYNC_DLX = "book2.search.sync.dlx.test";

    /*
    기본 인덱싱 시 큐
     */
    @Bean
    public Queue searchSyncQueue() {
        return QueueBuilder.durable(SEARCH_SYNC_QUEUE)
                .deadLetterExchange(SEARCH_SYNC_DLX)
                .deadLetterRoutingKey(SEARCH_SYNC_DLQ_ROUTING_KEY)
                .build();// true는 RabbitMQ가 재시작되어도 큐가 살아남게
    }

    @Bean
    public DirectExchange searchSyncExchange() {
        return new DirectExchange(SEARCH_SYNC_EXCHANGE);
    }

    @Bean
    public Binding searchSyncBinding(Queue searchSyncQueue, DirectExchange searchSyncExchange) {
        return BindingBuilder.bind(searchSyncQueue).to(searchSyncExchange).with(SEARCH_SYNC_ROUTING_KEY);
    }

    /*
    실패시 dlq-----------------
     */
    @Bean
    public Queue searchSyncDlq(){
        return new Queue(SEARCH_SYNC_DLQ, true);
    }
    @Bean
    public DirectExchange searchSyncDlx(){
        return new DirectExchange(SEARCH_SYNC_DLX);
    }

    @Bean
    public Binding searchSyncDlqBinding(Queue searchSyncDlq, DirectExchange searchSyncDlx){
        return BindingBuilder.bind(searchSyncDlq).to(searchSyncDlx).with(SEARCH_SYNC_DLQ_ROUTING_KEY);
    }
    //------------------

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
