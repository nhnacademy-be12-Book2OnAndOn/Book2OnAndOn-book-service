package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.config.RabbitMqConfig;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage;
import org.nhnacademy.book2onandonbookservice.event.CategoryUpdatedEvent;
import org.nhnacademy.book2onandonbookservice.event.TagUpdatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class SearchSyncEventListenerTest {

    @InjectMocks
    private SearchSyncEventListener searchSyncEventListener;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void handleCategoryNameUpdated() {
        Long categoryId = 1L;
        String oldName = "Old Category";
        String newName = "New Category";
        CategoryUpdatedEvent event = new CategoryUpdatedEvent(categoryId, oldName, newName);

        searchSyncEventListener.handleCategoryNameUpdated(event);

        ArgumentCaptor<SearchSyncMessage> messageCaptor = ArgumentCaptor.forClass(SearchSyncMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.SEARCH_SYNC_EXCHANGE),
                eq(RabbitMqConfig.SEARCH_SYNC_ROUTING_KEY),
                messageCaptor.capture()
        );

        SearchSyncMessage capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getTargetId()).isEqualTo(categoryId);
        assertThat(capturedMessage.getType()).isEqualTo(SearchSyncMessage.SyncType.CATEGORY);
    }

    @Test
    void handleTagNameUpdated() {
        Long tagId = 100L;
        String oldName = "Old Tag";
        String newName = "New Tag";
        TagUpdatedEvent event = new TagUpdatedEvent(tagId, oldName, newName);

        searchSyncEventListener.handleTagNameUpdated(event);

        ArgumentCaptor<SearchSyncMessage> messageCaptor = ArgumentCaptor.forClass(SearchSyncMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.SEARCH_SYNC_EXCHANGE),
                eq(RabbitMqConfig.SEARCH_SYNC_ROUTING_KEY),
                messageCaptor.capture()
        );

        SearchSyncMessage capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getTargetId()).isEqualTo(tagId);
        assertThat(capturedMessage.getType()).isEqualTo(SearchSyncMessage.SyncType.TAG);
    }
}