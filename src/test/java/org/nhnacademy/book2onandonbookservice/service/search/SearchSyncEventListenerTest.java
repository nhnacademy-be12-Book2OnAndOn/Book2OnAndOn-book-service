package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.config.RabbitMqConfig;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage.SyncType;
import org.nhnacademy.book2onandonbookservice.event.CategoryUpdatedEvent;
import org.nhnacademy.book2onandonbookservice.event.TagUpdatedEvent;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class SearchSyncEventListenerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private SearchSyncEventListener searchSyncEventListener;

    @Test
    @DisplayName("성공: 카테고리 업데이트 이벤트 처리 및 메시지 전송")
    void handleCategoryNameUpdated_Success() {
        Long categoryId = 10L;
        CategoryUpdatedEvent event = new CategoryUpdatedEvent(categoryId, "Old Name", "New Name");

        searchSyncEventListener.handleCategoryNameUpdated(event);

        ArgumentCaptor<SearchSyncMessage> messageCaptor = ArgumentCaptor.forClass(SearchSyncMessage.class);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.SEARCH_SYNC_EXCHANGE),
                eq(RabbitMqConfig.SEARCH_SYNC_ROUTING_KEY),
                messageCaptor.capture()
        );

        SearchSyncMessage sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.getTargetId()).isEqualTo(categoryId);
        assertThat(sentMessage.getType()).isEqualTo(SyncType.CATEGORY);
    }

    @Test
    @DisplayName("성공: 태그 업데이트 이벤트 처리 및 메시지 전송")
    void handleTagNameUpdated_Success() {
        Long tagId = 20L;
        TagUpdatedEvent event = new TagUpdatedEvent(tagId, "Old Tag", "New Tag");

        searchSyncEventListener.handleTagNameUpdated(event);

        ArgumentCaptor<SearchSyncMessage> messageCaptor = ArgumentCaptor.forClass(SearchSyncMessage.class);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.SEARCH_SYNC_EXCHANGE),
                eq(RabbitMqConfig.SEARCH_SYNC_ROUTING_KEY),
                messageCaptor.capture()
        );

        SearchSyncMessage sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.getTargetId()).isEqualTo(tagId);
        assertThat(sentMessage.getType()).isEqualTo(SyncType.TAG);
    }

    @Test
    @DisplayName("실패: RabbitMQ 전송 중 예외 발생 시 예외 전파 (카테고리)")
    void handleCategoryNameUpdated_RabbitMqError() {
        Long categoryId = 10L;
        CategoryUpdatedEvent event = new CategoryUpdatedEvent(categoryId, "Old Name", "New Name");

        doThrow(new AmqpException("Connection Failed"))
                .when(rabbitTemplate).convertAndSend(
                        anyString(),
                        anyString(),
                        any(Object.class)
                );

        assertThatThrownBy(() -> searchSyncEventListener.handleCategoryNameUpdated(event))
                .isInstanceOf(AmqpException.class);
    }
}