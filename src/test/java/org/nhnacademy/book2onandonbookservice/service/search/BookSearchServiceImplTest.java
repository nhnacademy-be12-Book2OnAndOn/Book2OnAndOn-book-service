package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.BookSearchQueryClient;
import org.nhnacademy.book2onandonbookservice.client.OllamaApiClient;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchWarmupMessage;
import org.nhnacademy.book2onandonbookservice.service.mapper.BookListResponseMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private BookSearchServiceImpl bookSearchService;

    private static final String SEARCH_WARMUP_EXCHANGE = "warmup-exchange";
    private static final String SEARCH_WARMUP_ROUTING_KEY = "warmup-key";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bookSearchService, "searchWarmupExchange", SEARCH_WARMUP_EXCHANGE);
        ReflectionTestUtils.setField(bookSearchService, "searchWarmupRoutingKey", SEARCH_WARMUP_ROUTING_KEY);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("성공: 키워드 검색 -> 임베딩 생성 -> 검색 실행 -> RabbitMQ 발송 (Happy Path)")
    void search_HappyPath() throws JsonProcessingException {
        String keyword = "Spring Boot";
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword(keyword);
        Pageable pageable = PageRequest.of(0, 10);

        when(valueOperations.get(anyString())).thenReturn(null);
        List<Float> mockVector = List.of(0.1f, 0.2f);
        when(ollamaApiClient.getEmbedding(keyword)).thenReturn(mockVector);
        when(objectMapper.writeValueAsString(mockVector)).thenReturn("[0.1, 0.2]");

        BookSearchDocument doc = mock(BookSearchDocument.class);
        Page<BookSearchDocument> searchResult = new PageImpl<>(List.of(doc));
        when(bookSearchQueryClient.search(condition, pageable, mockVector))
                .thenReturn(searchResult);

        when(bookListResponseMapper.fromDocument(doc)).thenReturn(mock(BookListResponse.class));

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result).hasSize(1);
        verify(ollamaApiClient).getEmbedding(keyword);
        verify(bookSearchQueryClient).search(any(), any(), eq(mockVector));
        verify(rabbitTemplate).convertAndSend(
                eq(SEARCH_WARMUP_EXCHANGE),
                eq(SEARCH_WARMUP_ROUTING_KEY),
                any(SearchWarmupMessage.class)
        );
        verify(valueOperations).set(contains(keyword), eq("[0.1, 0.2]"), eq(1L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("성공: 캐시에 이미 존재하는 키워드 검색 -> API 호출 없이 캐시 사용")
    void search_CacheHit() throws JsonProcessingException {
        String keyword = "Cached Keyword";
        List<Float> cachedVector = List.of(0.9f, 0.9f);
        when(valueOperations.get(anyString())).thenReturn("[0.9, 0.9]");
        when(objectMapper.readValue(eq("[0.9, 0.9]"), any(TypeReference.class))).thenReturn(cachedVector);

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

        // 타임아웃을 0으로 설정하여 즉시 타임아웃 유도
        ReflectionTestUtils.setField(bookSearchService, "embeddingTimeoutSeconds", 0);

        when(valueOperations.get(anyString())).thenReturn(null);
        
        CountDownLatch latch = new CountDownLatch(1);
        when(ollamaApiClient.getEmbedding(keyword)).thenAnswer(invocation -> {
            latch.await(5, TimeUnit.SECONDS);
            return List.of(0.1f);
        });

        Page<BookSearchDocument> searchResult = new PageImpl<>(Collections.emptyList());
        when(bookSearchQueryClient.search(any(), any(), anyList())).thenReturn(searchResult);

        bookSearchService.search(condition, pageable);

        verify(bookSearchQueryClient).search(any(), any(), eq(Collections.emptyList()));
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        
        latch.countDown();
    }

    @Test
    @DisplayName("실패: 임베딩 생성 중 예외 발생 -> 빈 벡터로 검색 수행 (Fallback)")
    void search_EmbeddingException() {
        String keyword = "Error Keyword";
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword(keyword);
        Pageable pageable = PageRequest.of(0, 10);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(ollamaApiClient.getEmbedding(keyword)).thenThrow(new RuntimeException("API Error"));

        Page<BookSearchDocument> searchResult = new PageImpl<>(Collections.emptyList());
        when(bookSearchQueryClient.search(any(), any(), anyList())).thenReturn(searchResult);

        bookSearchService.search(condition, pageable);

        verify(bookSearchQueryClient).search(any(), any(), eq(Collections.emptyList()));
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
    void search_KeywordExists_But_NoResult() throws JsonProcessingException {
        String keyword = "NoResult Keyword";
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword(keyword);
        Pageable pageable = PageRequest.of(0, 10);

        when(valueOperations.get(anyString())).thenReturn(null);
        List<Float> mockVector = List.of(0.1f);
        when(ollamaApiClient.getEmbedding(keyword)).thenReturn(mockVector);
        when(objectMapper.writeValueAsString(mockVector)).thenReturn("[0.1]");

        Page<BookSearchDocument> searchResult = new PageImpl<>(Collections.emptyList());
        when(bookSearchQueryClient.search(any(), any(), anyList())).thenReturn(searchResult);

        bookSearchService.search(condition, pageable);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(SearchWarmupMessage.class));
    }
}
