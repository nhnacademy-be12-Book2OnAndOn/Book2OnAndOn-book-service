package org.nhnacademy.book2onandonbookservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage;
import org.nhnacademy.book2onandonbookservice.service.search.BookReindexService;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReindexControllerTest {

    @Mock
    private BookReindexService bookReindexService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ReindexController reindexController;

    private static final String SEARCH_SYNC_EXCHANGE = "test-exchange";
    private static final String SEARCH_SYNC_ROUTING_KEY = "test-key";

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reindexController, "searchSyncExchange", SEARCH_SYNC_EXCHANGE);
        ReflectionTestUtils.setField(reindexController, "searchSyncRoutingKey", SEARCH_SYNC_ROUTING_KEY);
    }

    @Test
    @DisplayName("성공: 전체 재인덱싱 요청 시 서비스 호출 및 200 응답")
    void reindexAll_Success() {
        ResponseEntity<String> response = reindexController.reindexAll();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("전체 재인덱싱 작업이 백그라운드에서 시작되었습니다");

        verify(bookReindexService).reindexAll();
    }

    @Test
    @DisplayName("실패: 전체 재인덱싱 서비스 실행 중 예외 발생")
    void reindexAll_Fail_ServiceException() {
        doThrow(new RuntimeException("Indexing Failed")).when(bookReindexService).reindexAll();

        assertThatThrownBy(() -> reindexController.reindexAll())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Indexing Failed");
    }

    @Test
    @DisplayName("성공: 카테고리 강제 재인덱싱 요청 시 RabbitMQ 메시지 전송")
    void manualReindexCategory_Success() {
        Long categoryId = 100L;

        ResponseEntity<String> response = reindexController.manualReindexCategory(categoryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("카테고리(ID:100) 재인덱싱 요청");

        ArgumentCaptor<SearchSyncMessage> messageCaptor = ArgumentCaptor.forClass(SearchSyncMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(SEARCH_SYNC_EXCHANGE),
                eq(SEARCH_SYNC_ROUTING_KEY),
                messageCaptor.capture()
        );

        SearchSyncMessage sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.getTargetId()).isEqualTo(categoryId);
        assertThat(sentMessage.getType()).isEqualTo(SearchSyncMessage.SyncType.CATEGORY);
    }

    @Test
    @DisplayName("실패: 카테고리 재인덱싱 RabbitMQ 전송 실패")
    void manualReindexCategory_Fail_AmqpException() {
        Long categoryId = 100L;

        doThrow(new AmqpException("Connection Failed"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(SearchSyncMessage.class));

        assertThatThrownBy(() -> reindexController.manualReindexCategory(categoryId))
                .isInstanceOf(AmqpException.class)
                .hasMessage("Connection Failed");
    }

    @Test
    @DisplayName("성공: 태그 강제 재인덱싱 요청 시 RabbitMQ 메시지 전송")
    void manualReindexTag_Success() {
        Long tagId = 50L;

        ResponseEntity<String> response = reindexController.manualReindexTag(tagId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("태그(ID:50) 재인덱싱 요청");

        ArgumentCaptor<SearchSyncMessage> messageCaptor = ArgumentCaptor.forClass(SearchSyncMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(SEARCH_SYNC_EXCHANGE),
                eq(SEARCH_SYNC_ROUTING_KEY),
                messageCaptor.capture()
        );

        SearchSyncMessage sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.getTargetId()).isEqualTo(tagId);
        assertThat(sentMessage.getType()).isEqualTo(SearchSyncMessage.SyncType.TAG);
    }

    @Test
    @DisplayName("실패: 태그 재인덱싱 RabbitMQ 전송 실패")
    void manualReindexTag_Fail_AmqpException() {
        Long tagId = 50L;

        doThrow(new AmqpException("Connection Failed"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(SearchSyncMessage.class));

        assertThatThrownBy(() -> reindexController.manualReindexTag(tagId))
                .isInstanceOf(AmqpException.class)
                .hasMessage("Connection Failed");
    }
}
