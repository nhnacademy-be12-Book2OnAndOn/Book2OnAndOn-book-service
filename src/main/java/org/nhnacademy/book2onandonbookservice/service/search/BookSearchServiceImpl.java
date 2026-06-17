package org.nhnacademy.book2onandonbookservice.service.search;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.BookSearchQueryClient;
import org.nhnacademy.book2onandonbookservice.client.OllamaApiClient;

import org.nhnacademy.book2onandonbookservice.config.RedisKeyConstants;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchWarmupMessage;
import org.nhnacademy.book2onandonbookservice.exception.EmbeddingGenerationException;
import org.nhnacademy.book2onandonbookservice.service.mapper.BookListResponseMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class BookSearchServiceImpl implements BookSearchService {

    private final OllamaApiClient ollamaApiClient;
    private final BookListResponseMapper bookListResponseMapper;
    private final BookSearchQueryClient bookSearchQueryClient;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${search.embedding.timeout:2}")
    private int embeddingTimeoutSeconds = 2;

    @Value("${rabbitmq.exchange.search-warmup}")
    private String searchWarmupExchange;

    @Value("${rabbitmq.routing.search-warmup}")
    private String searchWarmupRoutingKey;



    @Override
    public Page<BookListResponse> search(BookSearchCondition condition, Pageable pageable) {
        if (condition == null) {
            return Page.empty(pageable);
        }
        List<Float> vector = generateEmbeddingIfPossible(condition.getKeyword());
        //FastPath 검색 수행 (우선 얘를 먼저 검색결과로 보여줌)
        Page<BookSearchDocument> searchResult = bookSearchQueryClient.search(condition, pageable, vector);
        //그다음 뒷단에서 RabbitMq로 Reranking 및 gemini 검색 결과 실행
        if(!searchResult.isEmpty() && StringUtils.hasText(condition.getKeyword())){
            SearchWarmupMessage msg = SearchWarmupMessage.builder()
                    .keyword(condition.getKeyword())
                    .categoryId(condition.getCategoryId())
                    .categoryName(condition.getCategoryName())
                    .contributorName(condition.getContributorName())
                    .publisherName(condition.getPublisherName())
                    .tagName(condition.getTagName())
                    .build();
            rabbitTemplate.convertAndSend(searchWarmupExchange, searchWarmupRoutingKey, msg);
        }

        return mapToPageResponse(searchResult.getContent(), pageable, searchResult.getTotalElements());

    }


    private List<Float> generateEmbeddingIfPossible(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }

        String cacheKey = RedisKeyConstants.EMBEDDING_CACHE_PREFIX + keyword;
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);

        if (cachedValue != null) {
            try {
                return objectMapper.readValue(cachedValue, new TypeReference<List<Float>>() {});
            } catch (Exception e) {
                log.warn("캐시 역직렬화 실패: {}", keyword);
            }
        }

        try {
            List<Float> vector = fetchEmbeddingWithTimeout(keyword);

            // 캐시 저장 (TTL 1시간)
            saveEmbeddingToCache(cacheKey, keyword, vector);

            return vector;

        } catch (TimeoutException e) {
            log.warn("임베딩 생성 타임아웃 ({}초): {} - 텍스트 검색만 수행", embeddingTimeoutSeconds, keyword);
            return Collections.emptyList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("스레드 인터럽트 발생", e);
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("임베딩 생성 실패: {} - {}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Float> fetchEmbeddingWithTimeout(String keyword) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<List<Float>> future = CompletableFuture.supplyAsync(() -> {
            try {
                return ollamaApiClient.getEmbedding(keyword);
            } catch (Exception e) {
                throw new EmbeddingGenerationException("AI 검색 기능 처리 중 오류가 발생했습니다.");
            }
        });

        return future.get(embeddingTimeoutSeconds, TimeUnit.SECONDS);
    }

    private void saveEmbeddingToCache(String cacheKey, String keyword, List<Float> vector) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(vector), 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("캐시 직렬화 실패: {}", keyword);
        }
    }

    private Page<BookListResponse> mapToPageResponse(List<BookSearchDocument> docs, Pageable pageable, long total) {
        List<BookListResponse> responses = docs.stream().map(bookListResponseMapper::fromDocument).toList();
        return new PageImpl<>(responses, pageable, total);
    }


}