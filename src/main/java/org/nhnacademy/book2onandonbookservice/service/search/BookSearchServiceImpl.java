package org.nhnacademy.book2onandonbookservice.service.search;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.BookSearchQueryClient;
import org.nhnacademy.book2onandonbookservice.client.OllamaApiClient;

import org.nhnacademy.book2onandonbookservice.config.RabbitMqConfig;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchWarmupMessage;
import org.nhnacademy.book2onandonbookservice.exception.EmbeddingGenerationException;
import org.nhnacademy.book2onandonbookservice.service.mapper.BookListResponseMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
    private final Map<String, List<Float>> embeddingCache = new ConcurrentHashMap<>();

    private static final int MAX_CACHE_SIZE = 1000;
    private static final int EMBEDDING_TIMEOUT_SECONDS = 2; // 타임아웃 3초



    @Override
    public Page<BookListResponse> search(BookSearchCondition condition, Pageable pageable) {
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
            rabbitTemplate.convertAndSend(RabbitMqConfig.SEARCH_WARMUP_EXCHANGE, RabbitMqConfig.SEARCH_WARMUP_ROUTING_KEY,msg);
        }

        return mapToPageResponse(searchResult.getContent(), pageable, searchResult.getTotalElements());

    }


    private List<Float> generateEmbeddingIfPossible(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }

        if (embeddingCache.containsKey(keyword)) {
            log.debug("캐시에서 임베딩 반환: {}", keyword);
            return embeddingCache.get(keyword);
        }

        try {
            CompletableFuture<List<Float>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return ollamaApiClient.getEmbedding(keyword);
                } catch (Exception e) {
                    throw new EmbeddingGenerationException("AI 검색 기능 처리 중 오류가 발생했습니다.");
                }
            });

            List<Float> vector = future.get(EMBEDDING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // 3. 캐시 저장
            if (embeddingCache.size() < MAX_CACHE_SIZE) {
                embeddingCache.put(keyword, vector);
            }

            return vector;

        } catch (TimeoutException e) {
            log.warn("임베딩 생성 타임아웃 ({}초): {} - 텍스트 검색만 수행", EMBEDDING_TIMEOUT_SECONDS, keyword);
            return Collections.emptyList();
        }catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("스레드 인터럽트 발생", e);
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("임베딩 생성 실패: {} - {}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Page<BookListResponse> mapToPageResponse(List<BookSearchDocument> docs, Pageable pageable, long total) {
        List<BookListResponse> responses = docs.stream().map(bookListResponseMapper::fromDocument).toList();
        return new PageImpl<>(responses, pageable, total);
    }


}