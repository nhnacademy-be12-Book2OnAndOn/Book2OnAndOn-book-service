package org.nhnacademy.book2onandonbookservice.service.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.BookSearchQueryClient;
import org.nhnacademy.book2onandonbookservice.client.OllamaApiClient;
import org.nhnacademy.book2onandonbookservice.config.RabbitMqConfig;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchWarmupMessage;
import org.nhnacademy.book2onandonbookservice.service.mapper.BookListResponseMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookSearchServiceImplTest {

    @Mock
    private OllamaApiClient ollamaApiClient;

    @Mock
    private BookListResponseMapper bookListResponseMapper;

    @Mock
    private BookSearchQueryClient bookSearchQueryClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private BookSearchServiceImpl bookSearchService;

    private Map<String, List<Float>> embeddingCache;

    @BeforeEach
    void setUp() {
        // 테스트 전 캐시 초기화 확인 (Reflection을 통해 접근)
        embeddingCache = (Map<String, List<Float>>) ReflectionTestUtils.getField(bookSearchService, "embeddingCache");
        if (embeddingCache != null) {
            embeddingCache.clear();
        }
    }

    @Test
    @DisplayName("성공: 키워드 검색 -> 임베딩 생성 -> 검색 실행 -> RabbitMQ 발송 (Happy Path)")
    void search_HappyPath() {
        String keyword = "Spring Boot";
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword(keyword);
        Pageable pageable = PageRequest.of(0, 10);

        List<Float> mockVector = List.of(0.1f, 0.2f);
        when(ollamaApiClient.getEmbedding(keyword)).thenReturn(mockVector);

        BookSearchDocument doc = mock(BookSearchDocument.class);
        Page<BookSearchDocument> searchResult = new PageImpl<>(List.of(doc));
        when(bookSearchQueryClient.search(eq(condition), eq(pageable), eq(mockVector)))
                .thenReturn(searchResult);

        when(bookListResponseMapper.fromDocument(doc)).thenReturn(mock(BookListResponse.class));

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result).hasSize(1);
        verify(ollamaApiClient).getEmbedding(keyword);
        verify(bookSearchQueryClient).search(any(), any(), eq(mockVector));
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.SEARCH_WARMUP_EXCHANGE),
                eq(RabbitMqConfig.SEARCH_WARMUP_ROUTING_KEY),
                any(SearchWarmupMessage.class)
        );

        assertThat(embeddingCache).containsKey(keyword);
    }

    @Test
    @DisplayName("성공: 캐시에 이미 존재하는 키워드 검색 -> API 호출 없이 캐시 사용")
    void search_CacheHit() {
        String keyword = "Cached Keyword";
        List<Float> cachedVector = List.of(0.9f, 0.9f);
        embeddingCache.put(keyword, cachedVector);

        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword(keyword);
        Pageable pageable = PageRequest.of(0, 10);

        Page<BookSearchDocument> searchResult = new PageImpl<>(Collections.emptyList());
        when(bookSearchQueryClient.search(any(), any(), eq(cachedVector))).thenReturn(searchResult);

        bookSearchService.search(condition, pageable);

        verify(ollamaApiClient, never()).getEmbedding(anyString());
        verify(bookSearchQueryClient).search(any(), any(), eq(cachedVector));
    }

    @Test
    @DisplayName("성공: 키워드가 없거나 공백인 경우 -> 임베딩 생성 안 함, RabbitMQ 발송 안 함")
    void search_NoKeyword() {
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword("");
        Pageable pageable = PageRequest.of(0, 10);

        Page<BookSearchDocument> searchResult = new PageImpl<>(Collections.emptyList());
        when(bookSearchQueryClient.search(any(), any(), eq(Collections.emptyList()))).thenReturn(searchResult);

        bookSearchService.search(condition, pageable);

        verify(ollamaApiClient, never()).getEmbedding(anyString());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(SearchWarmupMessage.class));
    }

    @Test
    @DisplayName("실패: 임베딩 생성 타임아웃 발생 -> 빈 벡터로 검색 수행 (Fallback)")
    void search_EmbeddingTimeout() {
        String keyword = "Slow Keyword";
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword(keyword);
        Pageable pageable = PageRequest.of(0, 10);

        when(ollamaApiClient.getEmbedding(keyword)).thenAnswer(invocation -> {
            try {
                Thread.sleep(2100);
            } catch (InterruptedException e) {
                // ignore
            }
            return List.of(0.1f);
        });

        Page<BookSearchDocument> searchResult = new PageImpl<>(Collections.emptyList());
        when(bookSearchQueryClient.search(any(), any(), anyList())).thenReturn(searchResult);

        bookSearchService.search(condition, pageable);

        verify(bookSearchQueryClient).search(any(), any(), eq(Collections.emptyList()));

        assertThat(embeddingCache).doesNotContainKey(keyword);
    }

    @Test
    @DisplayName("실패: 임베딩 생성 중 예외 발생 -> 빈 벡터로 검색 수행 (Fallback)")
    void search_EmbeddingException() {
        String keyword = "Error Keyword";
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword(keyword);
        Pageable pageable = PageRequest.of(0, 10);

        when(ollamaApiClient.getEmbedding(keyword)).thenThrow(new RuntimeException("API Error"));

        Page<BookSearchDocument> searchResult = new PageImpl<>(Collections.emptyList());
        when(bookSearchQueryClient.search(any(), any(), anyList())).thenReturn(searchResult);

        bookSearchService.search(condition, pageable);

        verify(bookSearchQueryClient).search(any(), any(), eq(Collections.emptyList()));
        assertThat(embeddingCache).doesNotContainKey(keyword);
    }

    @Test
    @DisplayName("Edge Case: 캐시가 가득 찼을 때 (MAX_SIZE=1000) -> 오래된 항목 제거 로직은 없으므로 추가 안함")
    void search_CacheFull() {

        for (int i = 0; i < 1000; i++) {
            embeddingCache.put("key" + i, List.of(1.0f));
        }

        String newKeyword = "New Keyword";
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword(newKeyword);
        Pageable pageable = PageRequest.of(0, 10);

        when(ollamaApiClient.getEmbedding(newKeyword)).thenReturn(List.of(0.5f));
        when(bookSearchQueryClient.search(any(), any(), any())).thenReturn(new PageImpl<>(Collections.emptyList()));

        bookSearchService.search(condition, pageable);


        assertThat(embeddingCache).hasSize(1000);
        assertThat(embeddingCache).doesNotContainKey(newKeyword);
    }

    @Test
    @DisplayName("성공: 검색 결과가 있지만 키워드가 없는 경우 -> RabbitMQ 발송 안 함")
    void search_ResultExists_But_NoKeyword() {
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword(null);
        Pageable pageable = PageRequest.of(0, 10);

        BookSearchDocument doc = mock(BookSearchDocument.class);
        Page<BookSearchDocument> searchResult = new PageImpl<>(List.of(doc));

        when(bookSearchQueryClient.search(any(), any(), anyList())).thenReturn(searchResult);
        when(bookListResponseMapper.fromDocument(doc)).thenReturn(mock(BookListResponse.class));

        bookSearchService.search(condition, pageable);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(SearchWarmupMessage.class));
    }

    @Test
    @DisplayName("성공: 키워드는 있지만 검색 결과가 없는 경우 -> RabbitMQ 발송 안 함")
    void search_KeywordExists_But_NoResult() {
        String keyword = "NoResult Keyword";
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword(keyword);
        Pageable pageable = PageRequest.of(0, 10);

        when(ollamaApiClient.getEmbedding(keyword)).thenReturn(List.of(0.1f));

        Page<BookSearchDocument> searchResult = new PageImpl<>(Collections.emptyList());
        when(bookSearchQueryClient.search(any(), any(), anyList())).thenReturn(searchResult);

        bookSearchService.search(condition, pageable);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(SearchWarmupMessage.class));
    }
}