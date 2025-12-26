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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMqConfig {

    public static final String SEARCH_SYNC_QUEUE = "book2.search.sync.queue.test";
    public static final String SEARCH_SYNC_EXCHANGE = "book2.search.sync.exchange.test";
    public static final String SEARCH_SYNC_ROUTING_KEY = "book2.search.sync.test";
    public static final String SEARCH_SYNC_DLQ_ROUTING_KEY="book2.search.sync.cancel.dlq.test";
    public static final String SEARCH_WARMUP_QUEUE = "book2.search.warmup.queue";
    public static final String SEARCH_WARMUP_EXCHANGE = "book2.search.warmup.exchange";
    public static final String SEARCH_WARMUP_ROUTING_KEY = "book2.search.warmup.key";

    public static final String SEARCH_SYNC_DLQ = "book2.search.sync.dlq.test";
    public static final String SEARCH_SYNC_DLX = "book2.search.sync.dlx.test";

    public static final String ORDER_EXCHANGE = "book2.dev.order-payment.exchange";
    public static final String ROUTING_KEY_CANCEL = "book.cancel";
    public static final String ROUTING_KEY_CONFIRM = "book.confirm";
    public static final String QUEUE_STOCK_CONFIRM = "book2.dev.stock.confirm.queue";
    public static final String QUEUE_STOCK_CANCEL = "book2.dev.stock.cancel.queue";
    public static final String STOCK_DLX = "book2.dev.stock.dlx";
    public static final String QUEUE_STOCK_CONFIRM_DLQ = "book2.dev.stock.confirm.dlq";
    public static final String QUEUE_STOCK_CANCEL_DLQ = "book2.dev.stock.cancel.dlq";
    public static final String ROUTING_KEY_CONFIRM_DLQ = "book.confirm.dlq";
    public static final String ROUTING_KEY_CANCEL_DLQ = "book.cancel.dlq";



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
    엘라스틱 (Reranking + gemini)
     */
    @Bean
    public Queue searchWarmupQueue(){
        return QueueBuilder.durable(SEARCH_WARMUP_QUEUE).build();
    }

    @Bean
    public DirectExchange searchWarmupExchange(){
        return new DirectExchange(SEARCH_WARMUP_EXCHANGE);
    }

    @Bean
    public Binding searchWarmupBinding(Queue searchWarmupQueue, DirectExchange searchWarmupExchange){
        return BindingBuilder.bind(searchWarmupQueue).to(searchWarmupExchange).with(SEARCH_WARMUP_ROUTING_KEY);
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

    /*
    order-payment 재고차감 로직 관련
     */

    @Bean
    public DirectExchange orderExchange() {
        return ExchangeBuilder.directExchange(ORDER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue stockConfirmQueue() {
        return QueueBuilder.durable(QUEUE_STOCK_CONFIRM)
                .deadLetterExchange(STOCK_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_CONFIRM_DLQ)
                .build();
    }

    @Bean
    public Queue stockCancelQueue() {
        return QueueBuilder.durable(QUEUE_STOCK_CANCEL)
                .deadLetterExchange(STOCK_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_CANCEL_DLQ)
                .build();
    }

    @Bean
    public DirectExchange stockDlx() {
        return new DirectExchange(STOCK_DLX);
    }

    @Bean
    public Queue stockConfirmDlq() {
        return QueueBuilder.durable(QUEUE_STOCK_CONFIRM_DLQ).build();
    }
    @Bean
    public Binding bindingStockConfirmDlq(@Qualifier("stockConfirmDlq") Queue queue,
                                          @Qualifier("stockDlx") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY_CONFIRM_DLQ);
    }

    @Bean
    public Queue stockCancelDlq() {
        return QueueBuilder.durable(QUEUE_STOCK_CANCEL_DLQ).build();
    }
    @Bean
    public Binding bindingStockCancelDlq(@Qualifier("stockCancelDlq") Queue queue,
                                         @Qualifier("stockDlx") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY_CANCEL_DLQ);
    }

    @Bean
    public Binding bindingStockConfirm(@Qualifier("orderExchange") DirectExchange exchange,
                                       @Qualifier("stockConfirmQueue") Queue queue) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY_CONFIRM);
    }
    @Bean
    public Binding bindingStockCancel(@Qualifier("orderExchange") DirectExchange exchange,
                                      @Qualifier("stockCancelQueue") Queue queue) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY_CANCEL);
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
