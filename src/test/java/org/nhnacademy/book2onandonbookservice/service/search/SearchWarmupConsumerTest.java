package org.nhnacademy.book2onandonbookservice.service.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.BookSearchQueryClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiSearchClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiSearchClient.AiRecommendation;
import org.nhnacademy.book2onandonbookservice.client.OllamaApiClient;
import org.nhnacademy.book2onandonbookservice.client.RerankerApiClient;
import org.nhnacademy.book2onandonbookservice.client.RerankerApiClient.RerankResult;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchWarmupMessage;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SearchWarmupConsumerTest {

    @InjectMocks
    private SearchWarmupConsumer searchWarmupConsumer;

    @Mock
    private BookSearchQueryClient bookSearchQueryClient;

    @Mock
    private RerankerApiClient rerankerApiClient;

    @Mock
    private GeminiSearchClient geminiSearchClient;

    @Mock
    private OllamaApiClient ollamaApiClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("정상 동작: 전체 프로세스 실행 및 Redis 저장 성공")
    void consumeWarmupMessage_Success() throws JsonProcessingException {
        SearchWarmupMessage message = new SearchWarmupMessage(
                "test-keyword", 1L, "Category", "Tag", "Contributor", "Publisher"
        );
        String cacheKey = "ai:result:test-keyword:1";

        given(redisTemplate.hasKey(cacheKey)).willReturn(false);

        List<Float> vector = List.of(0.1f, 0.2f);
        given(ollamaApiClient.getEmbedding(message.getKeyword())).willReturn(vector);

        BookSearchDocument doc1 = BookSearchDocument.builder()
                .id(10L)
                .title("Book 1")
                .description("Desc 1")
                .build();
        BookSearchDocument doc2 = BookSearchDocument.builder()
                .id(20L)
                .title("Book 2")
                .description("Desc 2")
                .build();
        List<BookSearchDocument> candidates = List.of(doc1, doc2);

        given(bookSearchQueryClient.search(any(BookSearchCondition.class), any(Pageable.class), eq(vector)))
                .willReturn(new PageImpl<>(candidates));

        RerankResult rr1 = new RerankResult(0, 0.9);
        RerankResult rr2 = new RerankResult(1, 0.8);
        given(rerankerApiClient.rerank(anyString(), anyList())).willReturn(List.of(rr1, rr2));

        // 수정됨: 첫 번째 인자로 String 대신 Long ID(10L)를 전달
        AiRecommendation rec = new AiRecommendation(10L, "Reason");
        List<AiRecommendation> recommendations = List.of(rec);
        given(geminiSearchClient.selectBestBooks(anyString(), anyList())).willReturn(recommendations);

        given(objectMapper.writeValueAsString(recommendations)).willReturn("[json]");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        searchWarmupConsumer.consumeWarmupMessage(message);

        verify(redisTemplate).hasKey(cacheKey);
        verify(ollamaApiClient).getEmbedding(message.getKeyword());
        verify(bookSearchQueryClient).search(any(BookSearchCondition.class), any(Pageable.class), eq(vector));
        verify(rerankerApiClient).rerank(eq(message.getKeyword()), anyList());
        verify(geminiSearchClient).selectBestBooks(eq(message.getKeyword()), anyList());
        verify(valueOperations).set(eq(cacheKey), eq("[json]"), any(Duration.class));
    }

    @Test
    @DisplayName("정상 동작: 이미 캐시가 존재하는 경우 조기 종료")
    void consumeWarmupMessage_CacheHit() {
        SearchWarmupMessage message = new SearchWarmupMessage("keyword", 1L, null, null, null, null);
        String cacheKey = "ai:result:keyword:1";

        given(redisTemplate.hasKey(cacheKey)).willReturn(true);

        searchWarmupConsumer.consumeWarmupMessage(message);

        verify(redisTemplate).hasKey(cacheKey);
        verify(ollamaApiClient, never()).getEmbedding(anyString());
        verify(bookSearchQueryClient, never()).search(any(), any(), any());
    }

    @Test
    @DisplayName("정상 동작: 검색 결과가 없을(Empty) 경우 조기 종료")
    void consumeWarmupMessage_NoCandidates() {
        SearchWarmupMessage message = new SearchWarmupMessage("keyword", null, null, null, null, null);
        String cacheKey = "ai:result:keyword:all";

        given(redisTemplate.hasKey(cacheKey)).willReturn(false);
        given(ollamaApiClient.getEmbedding(anyString())).willReturn(List.of(0.1f));
        given(bookSearchQueryClient.search(any(), any(), any()))
                .willReturn(new PageImpl<>(Collections.emptyList()));

        searchWarmupConsumer.consumeWarmupMessage(message);

        verify(rerankerApiClient, never()).rerank(anyString(), anyList());
        verify(geminiSearchClient, never()).selectBestBooks(anyString(), anyList());
    }

    @Test
    @DisplayName("정상 동작: Reranker 결과가 비어있을 경우 원본 리스트 제한으로 대체")
    void consumeWarmupMessage_RerankerEmpty() throws JsonProcessingException {
        SearchWarmupMessage message = new SearchWarmupMessage("keyword", 1L, null, null, null, null);
        String cacheKey = "ai:result:keyword:1";

        given(redisTemplate.hasKey(cacheKey)).willReturn(false);
        given(ollamaApiClient.getEmbedding(anyString())).willReturn(List.of(0.1f));

        BookSearchDocument doc = BookSearchDocument.builder()
                .id(10L)
                .title("Title")
                .description("Desc")
                .build();
        given(bookSearchQueryClient.search(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(doc)));

        given(rerankerApiClient.rerank(anyString(), anyList())).willReturn(Collections.emptyList());

        // 수정됨: 첫 번째 인자로 String 대신 Long ID(10L)를 전달
        List<AiRecommendation> recommendations = List.of(new AiRecommendation(10L, "Reason"));
        given(geminiSearchClient.selectBestBooks(anyString(), anyList())).willReturn(recommendations);
        given(objectMapper.writeValueAsString(any())).willReturn("json");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        searchWarmupConsumer.consumeWarmupMessage(message);

        verify(geminiSearchClient).selectBestBooks(anyString(), anyList());
        verify(redisTemplate.opsForValue()).set(eq(cacheKey), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("정상 동작: AI 추천 결과가 비어있을 경우 Redis에 저장하지 않음")
    void consumeWarmupMessage_EmptyRecommendations() {
        SearchWarmupMessage message = new SearchWarmupMessage("keyword", 1L, null, null, null, null);

        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(ollamaApiClient.getEmbedding(anyString())).willReturn(List.of(0.1f));

        BookSearchDocument doc = BookSearchDocument.builder()
                .id(10L)
                .title("Title")
                .description("Desc")
                .build();
        given(bookSearchQueryClient.search(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(doc)));

        given(rerankerApiClient.rerank(anyString(), anyList())).willReturn(List.of(new RerankResult(0, 1.0)));
        given(geminiSearchClient.selectBestBooks(anyString(), anyList())).willReturn(Collections.emptyList());

        searchWarmupConsumer.consumeWarmupMessage(message);

        verify(geminiSearchClient).selectBestBooks(anyString(), anyList());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("예외 발생: 처리 중 예외 발생 시 로그를 남기고 종료 (throw 하지 않음)")
    void consumeWarmupMessage_ExceptionHandling() {
        SearchWarmupMessage message = new SearchWarmupMessage("keyword", 1L, null, null, null, null);

        given(redisTemplate.hasKey(anyString())).willReturn(false);
        doThrow(new RuntimeException("Ollama API Fail"))
                .when(ollamaApiClient).getEmbedding(anyString());

        searchWarmupConsumer.consumeWarmupMessage(message);

        verify(ollamaApiClient).getEmbedding(anyString());
        verify(bookSearchQueryClient, never()).search(any(), any(), any());
    }
}