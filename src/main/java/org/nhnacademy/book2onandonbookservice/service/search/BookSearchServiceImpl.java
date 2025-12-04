package org.nhnacademy.book2onandonbookservice.service.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BookSearchServiceImpl implements BookSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final CategoryRepository categoryRepository;

    @Override
    public Page<BookListResponse> search(BookSearchCondition condition, Pageable pageable) {
        Pageable sortedPageable = applySort(pageable, condition);

        // 1. 복잡한 쿼리 빌딩 로직을 configureBoolQuery 메서드로 위임하여 복잡도 감소
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> configureBoolQuery(b, condition)))
                .withPageable(sortedPageable)
                .build();

        // 2. 검색 실행
        SearchHits<BookSearchDocument> hits =
                elasticsearchOperations.search(query, BookSearchDocument.class);

        // 3. 결과 매핑 (.collect(Collectors.toList()) -> .toList() 로 변경하여 간결화)
        List<BookListResponse> content = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toBookListResponse)
                .toList();

        return new PageImpl<>(content, sortedPageable, hits.getTotalHits());
    }

    /**
     * 검색 조건(BoolQuery)을 구성하는 헬퍼 메서드 - if문 분기를 이곳으로 이동시켜 search 메서드의 인지적 복잡도를 낮춤
     */
    private BoolQuery.Builder configureBoolQuery(BoolQuery.Builder b, BookSearchCondition condition) {
        // 키워드: 제목/권/기여자/출판사/카테고리/태그 전체 검색
        if (StringUtils.hasText(condition.getKeyword())) {
            b.must(m -> m.multiMatch(mm -> mm
                    .query(condition.getKeyword())
                    .fields("title", "volume",
                            "contributorNames",
                            "publisherNames",
                            "categoryNames",
                            "tagNames")));
        }

        // 단순 필터 적용 (기여자, 출판사, 태그)
        addSimpleFilters(b, condition);

        // 카테고리 필터 적용
        addCategoryFilter(b, condition);

        return b;
    }

    // 반복되는 단순 필터 로직 분리
    private void addSimpleFilters(BoolQuery.Builder b, BookSearchCondition condition) {
        if (StringUtils.hasText(condition.getContributorName())) {
            b.filter(f -> f.match(m -> m
                    .field("contributorNames.keyword")
                    .query(condition.getContributorName())));
        }

        if (StringUtils.hasText(condition.getPublisherName())) {
            b.filter(f -> f.match(m -> m
                    .field("publisherNames.keyword")
                    .query(condition.getPublisherName())));
        }

        if (StringUtils.hasText(condition.getTagName())) {
            b.filter(f -> f.match(m -> m
                    .field("tagNames.keyword")
                    .query(condition.getTagName())));
        }
    }

    // 카테고리 로직 분리 (DB 조회 포함)
    private void addCategoryFilter(BoolQuery.Builder b, BookSearchCondition condition) {
        if (condition.getCategoryId() != null) {
            String categoryName = categoryRepository.findById(condition.getCategoryId())
                    .map(Category::getCategoryName)
                    .orElse(null);

            if (categoryName != null) {
                b.filter(f -> f.term(t -> t
                        .field("categoryNames.keyword")
                        .value(categoryName)
                ));
            }
        }
    }

    // 정렬 조건을 빌더에 적용
    private Pageable applySort(Pageable pageable, BookSearchCondition condition) {
        String sort = condition.getSort();
        if (!StringUtils.hasText(sort)) {
            return pageable;
        }

        Sort springSort = switch (sort) {
            case "PRICE_ASC" -> Sort.by(Sort.Order.asc("priceStandard"));
            case "PRICE_DESC" -> Sort.by(Sort.Order.desc("priceStandard"));
            case "RECENT" -> Sort.by(Sort.Order.desc("publishDate"));
            case "LIKE_DESC" -> Sort.by(Sort.Order.desc("likeCount"));
            default -> Sort.unsorted();
        };

        if (springSort.isUnsorted()) {
            return pageable;
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                springSort
        );
    }

    // ES 도큐먼트 -> 목록 DTO 변환
    private BookListResponse toBookListResponse(BookSearchDocument doc) {
        return BookListResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .volume(doc.getVolume())
                .priceStandard(doc.getPriceStandard())
                .priceSales(doc.getPriceSales())
                .contributorNames(doc.getContributorNames())
                .publisherNames(doc.getPublisherNames())
                .categoryIds(doc.getCategoryNames())
                .tagNames(doc.getTagNames())
                .build();
    }
}