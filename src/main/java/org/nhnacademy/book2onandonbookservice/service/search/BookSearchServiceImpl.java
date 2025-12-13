package org.nhnacademy.book2onandonbookservice.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.GeminiSearchClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiSearchClient.AiRecommendation;
import org.nhnacademy.book2onandonbookservice.client.OllamaApiClient;
import org.nhnacademy.book2onandonbookservice.client.RerankerApiClient;
import org.nhnacademy.book2onandonbookservice.client.RerankerApiClient.RerankResult;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookSearchServiceImpl implements BookSearchService {

    private final CategoryRepository categoryRepository;
    private final OllamaApiClient ollamaApiClient;
    private final RerankerApiClient rerankerApiClient;
    private final ElasticsearchClient elasticsearchClient;
    private final GeminiSearchClient geminiSearchClient;

    private static final String INDEX_NAME = "book2onandon-books";
    private static final int CANDIDATE_SIZE_FOR_RERANK = 15; // Reranker에게 보낼 1차 후보군 (속도 최적화)
    private static final int INPUT_SIZE_FOR_LLM = 5;         // Gemini에게 보낼 최종 후보군 (토큰 절약)

    @Override
    public Page<BookListResponse> search(BookSearchCondition condition, Pageable pageable) {
        log.info("검색 요청 들어왔다!: keyword=[{}], sort=[{}]", condition.getKeyword(), pageable.getSort());

        // 1. 기본 검색 수행 (일단 20개 가져옴)
        //    AI를 쓰든 안 쓰든, 검색 결과는 있어야 하므로 먼저 가져옵니다.
        Pageable candidatePageable = PageRequest.of(0, CANDIDATE_SIZE_FOR_RERANK);
        Page<BookSearchDocument> candidates = searchHybrid(condition, candidatePageable, true);

        if (candidates.isEmpty()) {
            return Page.empty(pageable);
        }

        // 2. [스마트 판단] 과연 Gemini(비싼 AI)를 불러야 할까?
        //    조건: 키워드가 있고 + 정확도순 정렬이고 + 판단 로직(shouldTriggerGemini) 통과
        boolean useGemini = StringUtils.hasText(condition.getKeyword())
                && isRelevanceSort(pageable)
                && shouldTriggerGemini(condition.getKeyword(), candidates);

        List<BookListResponse> resultList;

        if (useGemini) {
            log.info("AI(Gemini) 자동 발동! (키워드: {})", condition.getKeyword());
            resultList = processWithGemini(condition.getKeyword(), candidates.getContent());
        } else {
            log.info("일반/Rerank 검색 수행 (Gemini Skip)");
            resultList = processSimple(condition.getKeyword(), candidates.getContent(), pageable);
        }

        // 3. 페이징 처리 및 반환
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), resultList.size());

        if (start > resultList.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, resultList.size());
        }

        return new PageImpl<>(resultList.subList(start, end), pageable, resultList.size());
    }

    /**
     * AI를 호출할지 말지 결정
     */
    private boolean shouldTriggerGemini(String keyword, Page<BookSearchDocument> normalResults) {
        if (!StringUtils.hasText(keyword)) return false;

        // 조건 1. 검색 결과가 너무 적으면 AI로 혹시 모를 추천을 시도 (3개 미만)
        if (normalResults.getTotalElements() < 3) return true;

        // 조건 2. 검색어에 "질문/추천" 의도가 포함된 단어가 있는가?
        String[] triggerWords = {"추천", "입문", "순서", "초보", "학습", "공부", "어때", "알려줘", "뭐야", "best", "top", "이유", "관련", "해줘"};
        for (String word : triggerWords) {
            if (keyword.contains(word)) return true;
        }

        // 조건 3. 검색어가 너무 길면(문장형) AI가 필요함 (예: 10글자 이상 & 공백 포함)
        if (keyword.length() > 20 && keyword.contains(" ")) return true;

        // 그 외 단순 검색어("자바", "김영한")는 AI 안 부름
        return false;
    }

    /**
     * Gemini를 사용하는 AI 프로세스 (Funnel)
     */
    private List<BookListResponse> processWithGemini(String keyword, List<BookSearchDocument> candidates) {
        // Reranking 수행
        List<BookSearchDocument> rerankedDocs = executeReranking(keyword, candidates);

        // Gemini에게 보낼 상위 N개 추출
        int cutSize = Math.min(rerankedDocs.size(), INPUT_SIZE_FOR_LLM);
        List<BookSearchDocument> topDocsForLlm = rerankedDocs.subList(0, cutSize);

        // Gemini 호출
        List<AiRecommendation> aiRecommendations = geminiSearchClient.selectBestBooks(keyword, topDocsForLlm);

        // 결과 매핑
        if (aiRecommendations.isEmpty()) {
            // AI가 실패하거나 추천을 거부하면 -> Reranker 결과 그대로 반환 (Fallback)
            log.info("Gemini 결과 없음(API 오류 또는 추천 거부). Rerank 결과로 대체합니다.");
            return rerankedDocs.stream()
                    .map(doc -> toBookListResponse(doc, null))
                    .toList();
        } else {
            // AI가 성공하면 -> 추천된 책 + 멘트 매핑
            return mapToResponseWithAiComments(rerankedDocs, aiRecommendations);
        }
    }

    /**
     * 일반 프로세스 (Reranker는 사용하되 Gemini는 안 씀 -> 가성비 최고)
     */
    private List<BookListResponse> processSimple(String keyword, List<BookSearchDocument> candidates, Pageable pageable) {
        List<BookSearchDocument> finalDocs = candidates;

        // 키워드 검색이고 정확도 정렬이면 Reranker는 돌려준다
        if (StringUtils.hasText(keyword) && isRelevanceSort(pageable)) {
            finalDocs = executeReranking(keyword, candidates);
        }

        return finalDocs.stream()
                .map(doc -> toBookListResponse(doc, null))
                .toList();
    }

    // --- Helper Methods ---

    private Page<BookSearchDocument> searchHybrid(BookSearchCondition condition, Pageable pageable, boolean useVector) {
        try {
            List<Float> queryVector = (useVector && StringUtils.hasText(condition.getKeyword()))
                    ? ollamaApiClient.getEmbedding(condition.getKeyword())
                    : Collections.emptyList();

            SearchResponse<BookSearchDocument> response = elasticsearchClient.search(s -> {
                s.index(INDEX_NAME);
                s.query(q -> q.bool(b -> configureBoolQuery(b, condition)));

                if (useVector && !queryVector.isEmpty()) {
                    s.knn(k -> k
                            .field("embedding")
                            .queryVector(queryVector)
                            .k(pageable.getPageSize())
                            .numCandidates(100)
                            .boost(0.5f)
                    );
                }

                s.from((int) pageable.getOffset());
                s.size(pageable.getPageSize());

                if (pageable.getSort().isSorted()) {
                    s.sort(convertSort(pageable.getSort()));
                }
                return s;
            }, BookSearchDocument.class);

            List<BookSearchDocument> content = response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());

            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;
            return new PageImpl<>(content, pageable, totalHits);

        } catch (IOException e) {
            log.error("Elasticsearch 검색 실패", e);
            throw new RuntimeException("Search failed", e);
        }
    }

    private List<BookSearchDocument> executeReranking(String query, List<BookSearchDocument> candidates) {
        if (candidates.isEmpty()) return candidates;

        List<String> texts = candidates.stream()
                .map(doc -> "제목: " + doc.getTitle() + " 설명: " + doc.getDescription())
                .toList();

        List<RerankResult> results = rerankerApiClient.rerank(query, texts);

        if (results.isEmpty()) return candidates;

        // 점수 높은 순 정렬
        results.sort(Comparator.comparingDouble(RerankResult::getScore).reversed());

        List<BookSearchDocument> sortedDocs = new ArrayList<>();
        for (RerankResult result : results) {
            if (result.getIndex() < candidates.size()) {
                sortedDocs.add(candidates.get(result.getIndex()));
            }
        }
        return sortedDocs;
    }

    private List<BookListResponse> mapToResponseWithAiComments(List<BookSearchDocument> allDocs, List<AiRecommendation> recommendations) {
        Map<Long, BookSearchDocument> docMap = allDocs.stream()
                .collect(Collectors.toMap(BookSearchDocument::getId, d -> d));

        Map<Long, String> reasonMap = recommendations.stream()
                .collect(Collectors.toMap(AiRecommendation::getId, AiRecommendation::getReason, (a, b) -> a));

        List<BookListResponse> finalResponse = new ArrayList<>();

        // AI 추천
        for (AiRecommendation rec : recommendations) {
            if (docMap.containsKey(rec.getId())) {
                finalResponse.add(toBookListResponse(docMap.get(rec.getId()), rec.getReason()));
                docMap.remove(rec.getId());
            }
        }
        // 나머지 Rerank 결과 (Fallback)
        for (BookSearchDocument doc : allDocs) {
            if (docMap.containsKey(doc.getId())) {
                finalResponse.add(toBookListResponse(doc, null));
            }
        }
        return finalResponse;
    }

    private BoolQuery.Builder configureBoolQuery(BoolQuery.Builder b, BookSearchCondition condition) {
        if (StringUtils.hasText(condition.getKeyword())) {
            b.must(m -> m.multiMatch(mm -> mm
                    .query(condition.getKeyword())
                    .fields("title^100", "contributorNames^90", "tagNames^80", "isbn^70", "publisherNames^60", "description^50")
            ));
        }
        addSimpleFilters(b, condition);
        addCategoryFilter(b, condition);
        return b;
    }

    private void addSimpleFilters(BoolQuery.Builder b, BookSearchCondition condition) {
        if (StringUtils.hasText(condition.getContributorName())) {
            b.filter(f -> f.match(m -> m.field("contributorNames.keyword").query(condition.getContributorName())));
        }
        if (StringUtils.hasText(condition.getPublisherName())) {
            b.filter(f -> f.match(m -> m.field("publisherNames.keyword").query(condition.getPublisherName())));
        }
        if (StringUtils.hasText(condition.getTagName())) {
            b.filter(f -> f.match(m -> m.field("tagNames.keyword").query(condition.getTagName())));
        }
    }

    private void addCategoryFilter(BoolQuery.Builder b, BookSearchCondition condition) {
        if (condition.getCategoryId() != null) {
            categoryRepository.findById(condition.getCategoryId())
                    .map(Category::getCategoryName)
                    .ifPresent(name -> b.filter(f -> f.term(t -> t.field("categoryNames.keyword").value(name))));
        }
    }

    private boolean isRelevanceSort(Pageable pageable) {
        return pageable.getSort().isUnsorted() ||
                pageable.getSort().stream().anyMatch(o -> o.getProperty().equals("score"));
    }

    private List<SortOptions> convertSort(Sort sort) {
        List<SortOptions> sortOptions = new ArrayList<>();
        sort.forEach(order -> {
            if ("score".equals(order.getProperty())) {
                sortOptions.add(SortOptions.of(so -> so.score(sc -> sc.order(SortOrder.Desc))));
            } else {
                sortOptions.add(SortOptions.of(so -> so.field(f -> f
                        .field(order.getProperty())
                        .order(order.isAscending() ? SortOrder.Asc : SortOrder.Desc))));
            }
        });
        return sortOptions;
    }

    private BookListResponse toBookListResponse(BookSearchDocument doc, String aiComment) {
        return BookListResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .volume(doc.getVolume())
                .priceStandard(doc.getPriceStandard())
                .priceSales(doc.getPriceSales())
                .contributorNames(doc.getContributorNames())
                .publisherNames(doc.getPublisherNames())
                .categoryNames(doc.getCategoryNames())
                .tagNames(doc.getTagNames())
                .thumbnail(doc.getImagePath())
                .aiRecommendation(aiComment)
                .build();
    }
}