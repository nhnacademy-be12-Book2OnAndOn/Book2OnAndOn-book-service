package org.nhnacademy.book2onandonbookservice.client;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookSearchQueryClient {
    private final ElasticsearchClient elasticsearchClient;

    private static final String INDEX_NAME = "book2onandon-books";
    private static final String FIELD_EMBEDDING = "embedding";

    private static final String[] SEARCH_FIELDS_WITH_BOOST = {
            "title^80.0",            // 제목
            "tagNames^20.0",         // 태그
            "categoryNames^10.0",    // 카테고리명
            "contributorNames^5.0", // 저자
            "publisherNames^5.0"   // 출판사
    };

    private static final int VECTOR_K = 30;
    private static final int VECTOR_NUM_CANDIDATES = 50;

    public Page<BookSearchDocument> search (BookSearchCondition condition, Pageable pageable, List<Float> vector){
        try{
            BoolQuery textQuery = buildTextQuery(condition);

            BoolQuery filterQuery = buildFilterQuery(condition);

            boolean hasFilter = !filterQuery.filter().isEmpty();
            boolean hasSortOption = pageable.getSort().isSorted();
            boolean useVectorSearch = (vector !=null && ! vector.isEmpty()) && !hasSortOption;

            SearchResponse<BookSearchDocument> response = elasticsearchClient.search( s ->{
                s.index(INDEX_NAME);

                s.query(q -> q.bool(b ->{
                    b.must(textQuery);
                    if(hasFilter){
                        b.filter(filterQuery.filter());
                    }
                    return b;
                }));

                if (useVectorSearch) {
                    s.knn(k ->
                        k.field(FIELD_EMBEDDING)
                                .queryVector(vector)
                                .k(VECTOR_K)
                                .numCandidates(VECTOR_NUM_CANDIDATES)
                                .boost(0.5f)
                                .filter(f -> f.bool(filterQuery))

                    );
                }

                if(StringUtils.hasText(condition.getKeyword())){
                    s.minScore(0.3d);
                }

                s.from((int) pageable.getOffset())
                        .size(pageable.getPageSize());

                if(!useVectorSearch && hasSortOption){
                    s.sort(convertSort(pageable.getSort()));
                }

                return s;
            }, BookSearchDocument.class);
            List<BookSearchDocument> content = response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();

            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            return new PageImpl<>(content, pageable, total);
        }catch (IOException e){
            log.error("Elasticsearch search failed: {}", e.getMessage());
            throw new RuntimeException("검색 중 오류 발생", e);
        }
    }

    private BoolQuery buildTextQuery(BookSearchCondition condition){
        BoolQuery.Builder builder = new BoolQuery.Builder();
        if(StringUtils.hasText(condition.getKeyword())){
            builder.must(m -> m.multiMatch(mm -> mm
                    .query(condition.getKeyword())
                    .fields(List.of(SEARCH_FIELDS_WITH_BOOST))
                    .type(TextQueryType.BestFields)
                    .operator(Operator.And)
                    .minimumShouldMatch("1<50%")
            ));
        }else{
            builder.must(m -> m.matchAll(ma -> ma));
        }
        return builder.build();
    }

    private BoolQuery buildFilterQuery(BookSearchCondition condition){
        BoolQuery.Builder builder = new BoolQuery.Builder();

        builder.mustNot(m -> m.term(t -> t.field("status.keyword").value("BOOK_DELETED")));
        addTermFilter(builder, "categoryIds", condition.getCategoryId());
        addTermFilter(builder, "categoryNames.keyword", condition.getCategoryName());
        addTermFilter(builder, "tagNames.keyword", condition.getTagName());
        addTermFilter(builder, "contributorNames.keyword", condition.getContributorName());
        addTermFilter(builder, "publisherNames.keyword", condition.getPublisherName());

        return builder.build();
    }
    private void addTermFilter(BoolQuery.Builder builder, String field, Object value) {
        if (value != null && StringUtils.hasText(value.toString())) {
            builder.filter(f -> f.term(t -> t.field(field).value(value.toString())));
        }
    }
    private List<SortOptions> convertSort(Sort sort) {
        return sort.stream().map(order -> SortOptions.of(so -> so
                .field(f -> f.field(order.getProperty()).order(order.isAscending() ? SortOrder.Asc : SortOrder.Desc)))
        ).toList();
    }
}
