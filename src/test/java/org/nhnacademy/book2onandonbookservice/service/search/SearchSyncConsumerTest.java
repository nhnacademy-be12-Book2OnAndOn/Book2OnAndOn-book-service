package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage.SyncType;

@ExtendWith(MockitoExtension.class)
class SearchSyncConsumerTest {
    @InjectMocks
    private SearchSyncConsumer searchSyncConsumer;

    @Mock
    private BookSearchSyncService bookSearchSyncService;

    @Test
    @DisplayName("Category 동기화 메시지 수신 시 reindexByCategoryId 호출 성공")
    void consumeSyncMessage_Category_Success() {
        Long targetId = 100L;

        SearchSyncMessage message = new SearchSyncMessage(targetId, SyncType.CATEGORY);

        given(bookSearchSyncService.reindexByCategoryId(targetId)).willReturn(5L);

        searchSyncConsumer.consumeSyncMessage(message);
        verify(bookSearchSyncService).reindexByCategoryId(targetId);
    }

    @Test
    @DisplayName("Tag 동기화 메시지 수신 시 reindexByTagId 호출 성공")
    void consumeSyncMessage_Tag_Success() {
        Long targetId = 100L;

        SearchSyncMessage message = new SearchSyncMessage(targetId, SyncType.TAG);

        given(bookSearchSyncService.reindexByTagId(targetId)).willReturn(5L);

        searchSyncConsumer.consumeSyncMessage(message);
        verify(bookSearchSyncService).reindexByTagId(targetId);
    }

    @Test
    @DisplayName("동기화서비스 중 예외 발 생시 예외를 다시 던져서 재시도 유도")
    void consumeSyncMessage_Exception_Throws() {
        Long targetId = 100L;

        SearchSyncMessage message = new SearchSyncMessage(targetId, SyncType.CATEGORY);

        willThrow(new RuntimeException("Elasticsearch Connection Timeout"))
                .given(bookSearchSyncService).reindexByCategoryId(targetId);

        assertThatThrownBy(() -> searchSyncConsumer.consumeSyncMessage(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Elasticsearch Connection Timeout");

        verify(bookSearchSyncService).reindexByCategoryId(targetId);
    }
}