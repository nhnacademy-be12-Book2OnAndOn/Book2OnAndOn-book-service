package org.nhnacademy.book2onandonbookservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMqConfig {

    public static final String SEARCH_SYNC_QUEUE = "book2.search.sync.queue";
    public static final String SEARCH_SYNC_EXCHANGE = "book2.search.sync.exchange";
    public static final String SEARCH_SYNC_ROUTING_KEY = "book2.search.sync";

    @Bean
    public Queue searchSyncQueue() {
        return new Queue(SEARCH_SYNC_QUEUE, true); // true는 RabbitMQ가 재시작되어도 큐가 살아남게
    }

    @Bean
    public DirectExchange searchSyncExchange() {
        return new DirectExchange(SEARCH_SYNC_EXCHANGE);
    }

    @Bean
    public Binding searchSyncBinding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(SEARCH_SYNC_ROUTING_KEY);
    }

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
