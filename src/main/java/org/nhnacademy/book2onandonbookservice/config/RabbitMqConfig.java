package org.nhnacademy.book2onandonbookservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMqConfig {

    @Value("${rabbitmq.queue.search-sync}")
    private String searchSyncQueueName;
    @Value("${rabbitmq.exchange.search-sync}")
    private String searchSyncExchangeName;
    @Value("${rabbitmq.routing.search-sync}")
    private String searchSyncRoutingKey;
    @Value("${rabbitmq.routing.search-sync-dlq}")
    private String searchSyncDlqRoutingKey;
    @Value("${rabbitmq.queue.search-sync-dlq}")
    private String searchSyncDlqName;
    @Value("${rabbitmq.exchange.search-sync-dlx}")
    private String searchSyncDlxName;

    @Value("${rabbitmq.queue.search-warmup}")
    private String searchWarmupQueueName;
    @Value("${rabbitmq.exchange.search-warmup}")
    private String searchWarmupExchangeName;
    @Value("${rabbitmq.routing.search-warmup}")
    private String searchWarmupRoutingKey;

    @Value("${rabbitmq.exchange.order}")
    private String orderExchangeName;
    @Value("${rabbitmq.routing.confirm}")
    private String confirmRoutingKey;
    @Value("${rabbitmq.routing.cancel}")
    private String cancelRoutingKey;
    @Value("${rabbitmq.queue.confirm}")
    private String confirmQueueName;
    @Value("${rabbitmq.queue.cancel}")
    private String cancelQueueName;
    @Value("${rabbitmq.exchange.stock-dlx}")
    private String stockDlxName;
    @Value("${rabbitmq.queue.confirm-dlq}")
    private String confirmDlqName;
    @Value("${rabbitmq.queue.cancel-dlq}")
    private String cancelDlqName;
    @Value("${rabbitmq.routing.confirm-dlq}")
    private String confirmDlqRoutingKey;
    @Value("${rabbitmq.routing.cancel-dlq}")
    private String cancelDlqRoutingKey;

    /*
    기본 인덱싱 시 큐
     */
    @Bean
    public Queue searchSyncQueue() {
        return QueueBuilder.durable(searchSyncQueueName)
                .deadLetterExchange(searchSyncDlxName)
                .deadLetterRoutingKey(searchSyncDlqRoutingKey)
                .build();
    }

    @Bean
    public DirectExchange searchSyncExchange() {
        return new DirectExchange(searchSyncExchangeName);
    }

    @Bean
    public Binding searchSyncBinding(Queue searchSyncQueue, DirectExchange searchSyncExchange) {
        return BindingBuilder.bind(searchSyncQueue).to(searchSyncExchange).with(searchSyncRoutingKey);
    }

    /*
    엘라스틱 (Reranking + gemini)
     */
    @Bean
    public Queue searchWarmupQueue(){
        return QueueBuilder.durable(searchWarmupQueueName).build();
    }

    @Bean
    public DirectExchange searchWarmupExchange(){
        return new DirectExchange(searchWarmupExchangeName);
    }

    @Bean
    public Binding searchWarmupBinding(Queue searchWarmupQueue, DirectExchange searchWarmupExchange){
        return BindingBuilder.bind(searchWarmupQueue).to(searchWarmupExchange).with(searchWarmupRoutingKey);
    }

    /*
    실패시 dlq-----------------
     */
    @Bean
    public Queue searchSyncDlq(){
        return new Queue(searchSyncDlqName, true);
    }
    @Bean
    public DirectExchange searchSyncDlx(){
        return new DirectExchange(searchSyncDlxName);
    }

    @Bean
    public Binding searchSyncDlqBinding(Queue searchSyncDlq, DirectExchange searchSyncDlx){
        return BindingBuilder.bind(searchSyncDlq).to(searchSyncDlx).with(searchSyncDlqRoutingKey);
    }
    //------------------

    /*
    order-payment 재고차감 로직 관련
     */

    @Bean
    public DirectExchange orderExchange() {
        return ExchangeBuilder.directExchange(orderExchangeName).durable(true).build();
    }

    @Bean
    public Queue stockConfirmQueue() {
        return QueueBuilder.durable(confirmQueueName)
                .deadLetterExchange(stockDlxName)
                .deadLetterRoutingKey(confirmDlqRoutingKey)
                .build();
    }

    @Bean
    public Queue stockCancelQueue() {
        return QueueBuilder.durable(cancelQueueName)
                .deadLetterExchange(stockDlxName)
                .deadLetterRoutingKey(cancelDlqRoutingKey)
                .build();
    }

    @Bean
    public DirectExchange stockDlx() {
        return new DirectExchange(stockDlxName);
    }

    @Bean
    public Queue stockConfirmDlq() {
        return QueueBuilder.durable(confirmDlqName).build();
    }
    @Bean
    public Binding bindingStockConfirmDlq(@Qualifier("stockConfirmDlq") Queue queue,
                                          @Qualifier("stockDlx") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(confirmDlqRoutingKey);
    }

    @Bean
    public Queue stockCancelDlq() {
        return QueueBuilder.durable(cancelDlqName).build();
    }
    @Bean
    public Binding bindingStockCancelDlq(@Qualifier("stockCancelDlq") Queue queue,
                                         @Qualifier("stockDlx") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(cancelDlqRoutingKey);
    }

    @Bean
    public Binding bindingStockConfirm(@Qualifier("orderExchange") DirectExchange exchange,
                                       @Qualifier("stockConfirmQueue") Queue queue) {
        return BindingBuilder.bind(queue).to(exchange).with(confirmRoutingKey);
    }
    @Bean
    public Binding bindingStockCancel(@Qualifier("orderExchange") DirectExchange exchange,
                                      @Qualifier("stockCancelQueue") Queue queue) {
        return BindingBuilder.bind(queue).to(exchange).with(cancelRoutingKey);
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
