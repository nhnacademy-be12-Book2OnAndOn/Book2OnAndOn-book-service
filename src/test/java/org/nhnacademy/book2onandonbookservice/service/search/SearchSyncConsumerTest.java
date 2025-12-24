package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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

    @Mock
    private BookSearchSyncService bookSearchSyncService;

    @InjectMocks
    private SearchSyncConsumer searchSyncConsumer;

    @Test
    @DisplayName("성공: 카테고리 동기화 메시지 수신 시 reindexByCategoryId 호출")
    void consumeSyncMessage_Category_Success() {
        Long targetId = 100L;
        SearchSyncMessage message = SearchSyncMessage.builder()
                .type(SyncType.CATEGORY)
                .targetId(targetId)
                .build();

        searchSyncConsumer.consumeSyncMessage(message);

        verify(bookSearchSyncService).reindexByCategoryId(targetId);
        verifyNoMoreInteractions(bookSearchSyncService);
    }

    @Test
    @DisplayName("성공: 태그 동기화 메시지 수신 시 reindexByTagId 호출")
    void consumeSyncMessage_Tag_Success() {
        Long targetId = 200L;
        SearchSyncMessage message = SearchSyncMessage.builder()
                .type(SyncType.TAG)
                .targetId(targetId)
                .build();

        searchSyncConsumer.consumeSyncMessage(message);

        verify(bookSearchSyncService).reindexByTagId(targetId);
        verifyNoMoreInteractions(bookSearchSyncService);
    }

    @Test
    @DisplayName("실패: 동기화 처리 중 예외 발생 시 예외를 다시 던짐 (재시도 유도)")
    void consumeSyncMessage_Exception_Fail() {
        Long targetId = 300L;
        SearchSyncMessage message = SearchSyncMessage.builder()
                .type(SyncType.CATEGORY)
                .targetId(targetId)
                .build();

        doThrow(new RuntimeException("Sync Failed"))
                .when(bookSearchSyncService).reindexByCategoryId(targetId);

        assertThatThrownBy(() -> searchSyncConsumer.consumeSyncMessage(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Sync Failed");

        verify(bookSearchSyncService).reindexByCategoryId(targetId);
    }
}