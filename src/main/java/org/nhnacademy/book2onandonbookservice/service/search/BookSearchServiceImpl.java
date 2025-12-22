package org.nhnacademy.book2onandonbookservice.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.GeminiSearchClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiSearchClient.AiRecommendation;
import org.nhnacademy.book2onandonbookservice.client.OllamaApiClient;
import org.nhnacademy.book2onandonbookservice.client.RerankerApiClient;
import org.nhnacademy.book2onandonbookservice.client.RerankerApiClient.RerankResult;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.exception.SearchExecutionException;
import org.nhnacademy.book2onandonbookservice.service.mapper.BookListResponseMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookSearchServiceImpl implements BookSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final OllamaApiClient ollamaApiClient;
    private final RerankerApiClient rerankerApiClient;
    private final GeminiSearchClient geminiSearchClient;
    private final BookListResponseMapper bookListResponseMapper;
    private final Map<String, List<Float>> embeddingCache = new ConcurrentHashMap<>();


    // --- Constants ---
    private static final String INDEX_NAME = "book2onandon-books";
    private static final String FIELD_EMBEDDING = "embedding";

    // 검색 대상 필드 및 가중치
    private static final String[] SEARCH_FIELDS_WITH_BOOST = {
            "title^80.0",            // 제목
            "tagNames^20.0",         // 태그
            "categoryNames^10.0",    // 카테고리명
            "contributorNames^5.0", // 저자
            "publisherNames^5.0"   // 출판사

    };

    private static final int GEMINI_INPUT_SIZE = 5;
    private static final int VECTOR_K = 30;
    private static final int VECTOR_NUM_CANDIDATES = 50;
    private static final int MAX_CACHE_SIZE = 1000;
    private static final int EMBEDDING_TIMEOUT_SECONDS = 2; // 타임아웃 3초



    @Override
    public Page<BookListResponse> search(BookSearchCondition condition, Pageable pageable) {
        // 1. Elasticsearch 검색 실행
        Page<BookSearchDocument> searchResult = executeElasticsearchQuery(condition, pageable);

        if (searchResult.isEmpty()) {
            return Page.empty(pageable);
        }

        List<BookSearchDocument> documents = searchResult.getContent();

        // 2. Reranking (옵션)
        if (shouldExecuteReranking(condition, pageable)) {
            documents = applyReranking(condition.getKeyword(), documents);
        }

        // 3. AI 추천 (옵션)
        if (Boolean.TRUE.equals(condition.getUseAiSearch())) {
            return applyAiRecommendation(condition.getKeyword(), documents, pageable);
        }

        return mapToPageResponse(documents, pageable, searchResult.getTotalElements());
    }

    private Page<BookSearchDocument> executeElasticsearchQuery(BookSearchCondition condition, Pageable pageable) {
        try {
            // 1) 텍스트 검색용 쿼리 (Must 조건)
            BoolQuery textQuery = buildTextQuery(condition);

            // 2) 공통 필터 쿼리 (카테고리, 태그 등) -> 중요! 이것만 kNN 필터에 넣어야 함
            BoolQuery filterQuery = buildFilterQuery(condition);
            boolean hasFilter = !filterQuery.filter().isEmpty();

            // 3) 벡터 생성
            List<Float> vector = generateEmbeddingIfPossible(condition.getKeyword());

            boolean hasSortOption = pageable.getSort().isSorted();
            boolean useVectorSearch = !vector.isEmpty() && !hasSortOption;

            if (!vector.isEmpty()) {
                log.info("생성된 검색 벡터 차원 수: {}", vector.size()); // 로그 확인용
            }

            SearchResponse<BookSearchDocument> response = elasticsearchClient.search(s -> {
                s.index(INDEX_NAME);

                // [A] 메인 쿼리: 텍스트 검색 + 필터 적용
                s.query(q -> q.bool(b -> {
                    b.must(textQuery.must()); // 텍스트 매칭
                    if (hasFilter) {
                        b.filter(filterQuery.filter()); // 필터 적용
                    }
                    return b;
                }));

                // [B] 벡터 검색 (Hybrid): 필터만 적용 (키워드 매칭은 강제하지 않음)
                if (useVectorSearch) {
                    s.knn(k -> {
                        k.field(FIELD_EMBEDDING)
                                .queryVector(vector)
                                .k(VECTOR_K)
                                .numCandidates(VECTOR_NUM_CANDIDATES)
                                .boost(0.5f);
                        if (hasFilter) {
                            k.filter(f -> f.bool(filterQuery));
                        }
                        return k;
                    });
                }
                if(StringUtils.hasText(condition.getKeyword())){
                    s.minScore(0.3d);
                }

                s.from((int) pageable.getOffset())
                        .size(pageable.getPageSize());

                if (!useVectorSearch && hasSortOption) {
                    s.sort(convertSort(pageable.getSort()));
                }

                return s;
            }, BookSearchDocument.class);

            List<BookSearchDocument> content = response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();

            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            return new PageImpl<>(content, pageable, total);

        } catch (IOException e) {
            log.error("Elasticsearch search failed: {}", e.getMessage());
            throw new SearchExecutionException("검색 중 오류 발생", e);
        }
    }

    /**
     * 텍스트 검색 조건 (Keyword Match) 생성
     */
    private BoolQuery buildTextQuery(BookSearchCondition condition) {
        BoolQuery.Builder builder = new BoolQuery.Builder();
        if (StringUtils.hasText(condition.getKeyword())) {
            builder.must(m -> m.multiMatch(mm -> mm
                    .query(condition.getKeyword())
                    .fields(List.of(SEARCH_FIELDS_WITH_BOOST))
                    .type(TextQueryType.BestFields)
                    .operator(Operator.And)
                    .minimumShouldMatch("1<50%")
            ));
        } else {
            builder.must(m -> m.matchAll(ma -> ma));
        }
        return builder.build();
    }

    /**
     * 필터 조건 (Filter) 생성 - 카테고리, 태그 등
     */
    private BoolQuery buildFilterQuery(BookSearchCondition condition) {
        BoolQuery.Builder builder = new BoolQuery.Builder();

        builder.mustNot(m -> m.term(t -> t
                .field("status.keyword")
                .value("BOOK_DELETED")
        ));

        addTermFilter(builder, "categoryIds", condition.getCategoryId());
        addTermFilter(builder, "categoryNames.keyword", condition.getCategoryName());
        addTermFilter(builder, "tagNames.keyword", condition.getTagName());
        addTermFilter(builder, "contributorNames.keyword", condition.getContributorName());
        addTermFilter(builder, "publisherNames.keyword", condition.getPublisherName());

        return builder.build();
    }

    // --- Helper Methods ---

    private void addTermFilter(BoolQuery.Builder builder, String field, Object value) {
        if (value != null && StringUtils.hasText(value.toString())) {
            builder.filter(f -> f.term(t -> t.field(field).value(value.toString())));
        }
    }

    // (기존 generateEmbeddingIfPossible, applyReranking, applyAiRecommendation, mapToPageResponse, convertSort 등 나머지 메서드는 그대로 유지)
    // 아래 코드는 위에서 사용된 메서드들을 위해 복사해두시면 됩니다.

    private boolean shouldExecuteReranking(BookSearchCondition condition, Pageable pageable) {
        return StringUtils.hasText(condition.getKeyword()) && pageable.getSort().isUnsorted();
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
                    throw new RuntimeException(e);
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
        } catch (Exception e) {
            log.warn("임베딩 생성 실패: {} - {}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ... 나머지 rerank, ai, mapper 메서드들 ... (이전 코드와 동일하므로 생략하지 않고 필요하면 붙여넣으세요)
    // [중요] 아래 메서드들은 꼭 있어야 합니다.
    private List<BookSearchDocument> applyReranking(String query, List<BookSearchDocument> docs) {
        if (docs.isEmpty()) return docs;
        List<String> texts = docs.stream().map(this::formatDocumentForRerank).toList();
        List<RerankResult> results = rerankerApiClient.rerank(query, texts);
        if (results.isEmpty()) return docs;
        results.sort(Comparator.comparingDouble(RerankResult::getScore).reversed());
        List<BookSearchDocument> sorted = new ArrayList<>();
        for (RerankResult res : results) {
            if (res.getIndex() < docs.size()) sorted.add(docs.get(res.getIndex()));
        }
        return sorted;
    }

    private Page<BookListResponse> applyAiRecommendation(String keyword, List<BookSearchDocument> docs, Pageable pageable) {
        try {
            int cutSize = Math.min(docs.size(), GEMINI_INPUT_SIZE);
            if (cutSize == 0) return new PageImpl<>(Collections.emptyList(), pageable, 0);

            List<BookSearchDocument> candidates = docs.subList(0, cutSize);

            List<AiRecommendation> recommendations = geminiSearchClient.selectBestBooks(keyword, candidates);

            Map<Long, String> reasonMap = recommendations.stream()
                    .collect(Collectors.toMap(AiRecommendation::getId, AiRecommendation::getReason, (a, b) -> a));

            List<BookListResponse> responses = docs.stream().map(doc -> {
                BookListResponse res = bookListResponseMapper.fromDocument(doc);
                if (reasonMap.containsKey(doc.getId())) {
                    res.setAiRecommendation(reasonMap.get(doc.getId()));
                }
                return res;
            }).toList();

            return new PageImpl<>(responses, pageable, docs.size());

        } catch (Exception e) {
            // AI가 터져도(429 Error) 검색 결과는 정상적으로 리턴해야 함!
            log.error("[Gemini] AI 추천 실패 (API Quota 초과 등) - 기본 결과 반환. 이유: {}", e.getMessage());
            // 원본 그대로 반환
            return mapToPageResponse(docs, pageable, docs.size());
        }
    }

    private String formatDocumentForRerank(BookSearchDocument doc) {
        return String.format("제목: %s | 저자: %s | 설명: %s", doc.getTitle(), doc.getContributorNames() != null ? String.join(", ", doc.getContributorNames()) : "", doc.getDescription());
    }

    private Page<BookListResponse> mapToPageResponse(List<BookSearchDocument> docs, Pageable pageable, long total) {
        List<BookListResponse> responses = docs.stream().map(bookListResponseMapper::fromDocument).toList();
        return new PageImpl<>(responses, pageable, total);
    }

    private List<SortOptions> convertSort(Sort sort) {
        return sort.stream().map(order -> SortOptions.of(so -> so
                .field(f -> f.field(order.getProperty()).order(order.isAscending() ? SortOrder.Asc : SortOrder.Desc)))
        ).toList();
    }
}