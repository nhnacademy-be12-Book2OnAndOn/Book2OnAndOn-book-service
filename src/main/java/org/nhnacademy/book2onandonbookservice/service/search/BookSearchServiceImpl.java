package org.nhnacademy.book2onandonbookservice.service.search;

import java.util.List;
import java.util.stream.Collectors;
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
        // NativeQuery로 쿼리 구성
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {

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

                    // 기여자 필터
                    if (StringUtils.hasText(condition.getContributorName())) {
                        b.filter(f -> f.match(m -> m
                                .field("contributorNames.keyword")
                                .query(condition.getContributorName())));
                    }

                    // 출판사 필터
                    if (StringUtils.hasText(condition.getPublisherName())) {
                        b.filter(f -> f.match(m -> m
                                .field("publisherNames.keyword")
                                .query(condition.getPublisherName())));
                    }

                    // 태그 필터
                    if (StringUtils.hasText(condition.getTagName())) {
                        b.filter(f -> f.match(m -> m
                                .field("tagNames.keyword")
                                .query(condition.getTagName())));
                    }

                    // 카테고리 필터
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

                    return b;
                }))
                .withPageable(sortedPageable)
                .build();

        // 검색 실행
        SearchHits<BookSearchDocument> hits =
                elasticsearchOperations.search(query, BookSearchDocument.class);

        List<BookListResponse> content = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toBookListResponse)
                .collect(Collectors.toList());

        long totalHits = hits.getTotalHits();

        return new PageImpl<>(content, sortedPageable, totalHits);
    }

    // 정렬 조건을 빌더에 적용
    private Pageable applySort(Pageable pageable, BookSearchCondition condition) {
        String sort = condition.getSort();
        if (!StringUtils.hasText(sort)) {
            return pageable; // 정렬 조건이 없으면 그대로 사용
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