package org.nhnacademy.book2onandonbookservice.service.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.BookSearchQueryClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiSearchClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiSearchClient.AiRecommendation;
import org.nhnacademy.book2onandonbookservice.client.OllamaApiClient;
import org.nhnacademy.book2onandonbookservice.client.RerankerApiClient;
import org.nhnacademy.book2onandonbookservice.client.RerankerApiClient.RerankResult;
import org.nhnacademy.book2onandonbookservice.config.RabbitMqConfig;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchWarmupMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchWarmupConsumer {
    private final BookSearchQueryClient bookSearchQueryClient;
    private final RerankerApiClient rerankerApiClient;
    private final GeminiSearchClient geminiSearchClient;
    private final OllamaApiClient ollamaApiClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final int CANDIDATE_SIZE = 50;
    private static final int TOP_K =10;

    @RabbitListener(queues = RabbitMqConfig.SEARCH_WARMUP_QUEUE)
    public void consumeWarmupMessage(SearchWarmupMessage message){
        log.info("[AI-Warmup] 작업 시작: {}", message);

        try{
            BookSearchCondition condition = mapToCondition(message);

            String cacheKey = "ai:result:" + message.getKeyword() + ":" +
                    (message.getCategoryId() != null ? message.getCategoryId() : "all");

            if(Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))){
                return;
            }

            List<Float> vector = ollamaApiClient.getEmbedding(message.getKeyword());

            Pageable pageable = PageRequest.of(0, CANDIDATE_SIZE);

            List<BookSearchDocument> candidates = bookSearchQueryClient
                    .search(condition, pageable, vector)
                    .getContent();

            if(candidates.isEmpty()) return;

            List<String> texts = candidates.stream()
                    .map(doc -> "제목: "+ doc.getTitle() + " | 설명: "+ doc.getDescription())
                    .toList();
            List<RerankResult> rerankResults = rerankerApiClient.rerank(message.getKeyword(), texts);

            List<BookSearchDocument> topBooks = sortAndLimit(candidates, rerankResults);
            List<AiRecommendation> recommendations = geminiSearchClient.selectBestBooks(message.getKeyword(), topBooks);


            // 6. [Redis Save] 결과 저장
            if (!recommendations.isEmpty()) {
                String jsonResult = objectMapper.writeValueAsString(recommendations);
                redisTemplate.opsForValue().set(cacheKey, jsonResult, CACHE_TTL);
                log.info("[AI-Warmup] 저장 완료. Key={}", cacheKey);
            }

        } catch (Exception e) {
            log.error("[AI-Warmup] 오류 발생: {}", e.getMessage(), e);
        }
    }

    private BookSearchCondition mapToCondition(SearchWarmupMessage msg) {
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword(msg.getKeyword());
        condition.setCategoryId(msg.getCategoryId());
        condition.setCategoryName(msg.getCategoryName());
        condition.setTagName(msg.getTagName());
        condition.setContributorName(msg.getContributorName());
        condition.setPublisherName(msg.getPublisherName());
        return condition;
    }
    private List<BookSearchDocument> sortAndLimit(List<BookSearchDocument> docs, List<RerankResult> ranks) {
        if (ranks.isEmpty()) return docs.stream().limit(TOP_K).toList();

        return ranks.stream()
                .sorted(Comparator.comparingDouble(RerankResult::getScore).reversed())
                .limit(TOP_K)
                .map(r -> docs.get(r.getIndex()))
                .toList();
    }

}
