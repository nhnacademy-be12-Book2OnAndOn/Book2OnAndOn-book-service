package org.nhnacademy.book2onandonbookservice.service.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.config.RabbitMqConfig;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage.SyncType;
import org.nhnacademy.book2onandonbookservice.event.CategoryUpdatedEvent;
import org.nhnacademy.book2onandonbookservice.event.TagUpdatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchSyncEventListener {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 카테고리명 변경 -> RabbitMQ로 메시지 전송
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCategoryNameUpdated(CategoryUpdatedEvent event) {
        SearchSyncMessage message = new SearchSyncMessage(event.categoryId(), SyncType.CATEGORY);

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.SEARCH_SYNC_EXCHANGE,
                RabbitMqConfig.SEARCH_SYNC_ROUTING_KEY,
                message
        );

        log.info("[MQ} 카테고리 업데이트 메시지 전송: {}", message);
    }

    /**
     * 태그명 변경 -> RabbitMQ로 메시지 전송
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTagNameUpdated(TagUpdatedEvent event) {
        SearchSyncMessage message = new SearchSyncMessage(event.tagId(), SyncType.TAG);

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.SEARCH_SYNC_EXCHANGE,
                RabbitMqConfig.SEARCH_SYNC_ROUTING_KEY,
                message
        );
        log.info("[MQ} 태그 업데이트 메시지 전송: {}", message);
    }

}
