package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;

@ExtendWith(MockitoExtension.class)
class BookSearchServiceImplTest {
    @InjectMocks
    private BookSearchServiceImpl bookSearchService;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("기본 검색 성공: 조건없이 전체 검색 및 결과 매핑 확인")
    void search_Success() {
        BookSearchCondition condition = new BookSearchCondition();
        Pageable pageable = PageRequest.of(0, 10);

        BookSearchDocument doc = createSampleDocument(1L, "테스트 책");
        SearchHits<BookSearchDocument> mockHits = createMockSearchHits(List.of(doc), 1);

        given(elasticsearchOperations.search(any(NativeQuery.class), eq(BookSearchDocument.class))).willReturn(
                mockHits);
        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);

        BookListResponse response = result.getContent().get(0);
        assertThat(response.getTitle()).isEqualTo("테스트 책");
        assertThat(response.getPriceStandard()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("카테고리 필터 검색: DB에서 카테고리명을 조회해서 쿼리에 반영하나?")
    void search_WithCategory_filter() {
        Long categoryId = 100L;
        String categoryName = "카테고리";

        BookSearchCondition condition = new BookSearchCondition(null, categoryId, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10);

        Category category = mock(Category.class);
        given(category.getCategoryName()).willReturn(categoryName);
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(category));

        BookSearchDocument doc = createSampleDocument(1L, "Spring JPA");
        SearchHits<BookSearchDocument> mockHits = createMockSearchHits(List.of(doc), 1L);

        given(elasticsearchOperations.search(any(NativeQuery.class), eq(BookSearchDocument.class))).willReturn(
                mockHits);

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(categoryRepository).findById(categoryId);

    }

    @Test
    @DisplayName("단순 필터 검색: 기여자, 출판사, 태그 조건")
    void search_WithSimpleFilters() {
        BookSearchCondition condition = new BookSearchCondition(
                null, null, "IT", "전유진", "NHN", null, null, null
        );

        Pageable pageable = PageRequest.of(0, 10);

        BookSearchDocument doc = createSampleDocument(2L, "JPA 프로그래밍");
        SearchHits<BookSearchDocument> mockHits = createMockSearchHits(List.of(doc), 1L);

        given(elasticsearchOperations.search(any(NativeQuery.class), eq(BookSearchDocument.class)))
                .willReturn(mockHits);

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("JPA 프로그래밍");
    }

    @Test
    @DisplayName("정렬 조건 적용했을때 : 가격 오름차순")
    void search_WithSort() {
        BookSearchCondition condition = new BookSearchCondition(
                null, null, null, null, null, null, null, "PRICE_ASC"
        );
        Pageable pageable = PageRequest.of(0, 10);

        BookSearchDocument doc = createSampleDocument(1L, "JPA프로그래밍");
        SearchHits<BookSearchDocument> mockHits = createMockSearchHits(List.of(doc), 1L);

        given(elasticsearchOperations.search(any(NativeQuery.class), eq(BookSearchDocument.class)))
                .willReturn(mockHits);

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result.getContent()).isNotEmpty();

    }

    private BookSearchDocument createSampleDocument(Long id, String title) {
        return BookSearchDocument.builder()
                .id(id)
                .title(title)
                .isbn("1234567890123")
                .volume("1권")
                .priceStandard(10000L)
                .priceSales(9000L)
                .contributorNames(List.of("작가"))
                .publisherNames(List.of("출판사"))
                .contributorNames(List.of("카테고리"))
                .tagNames(List.of("태그"))
                .publishDate(LocalDate.now())
                .build();
    }

    private SearchHits<BookSearchDocument> createMockSearchHits(List<BookSearchDocument> documents, long totalHits) {
        List<SearchHit<BookSearchDocument>> searchHits = documents.stream()
                .map(doc -> new SearchHit<BookSearchDocument>(
                        "index-name",
                        String.valueOf(doc.getId()),
                        null,
                        1.0f,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        doc
                )).toList();

        // ★ [수정됨] 생성자 인자 10개로 맞춤 (Spring Data Elasticsearch 5.x)
        return new SearchHitsImpl<>(
                totalHits,                        // 1. totalHits (long): 전체 결과 수
                TotalHitsRelation.EQUAL_TO, // 2. totalHitsRelation: 정확한 개수인지 여부 (보통 EQUAL_TO 사용)
                1.0f,                       // 3. maxScore (float): 최대 스코어
                Duration.ofMillis(100),     // 4. executionDuration: 실행 시간 (Duration 객체 필수)
                null,                       // 5. scrollId (String): 스크롤 ID (없으면 null)
                null,                       // 6. pointInTimeId (String): PIT ID (없으면 null) <-- 이 부분이 추가된 것 같습니다
                searchHits,                       // 7. searchHits (List): 실제 데이터 리스트
                null,                       // 8. aggregations: 집계 데이터 (없으면 null)
                null,                       // 9. suggest: 추천 데이터 (없으면 null)
                null                        // 10. searchShardStatistics: 샤드 통계 (없으면 null)
        );
    }
}