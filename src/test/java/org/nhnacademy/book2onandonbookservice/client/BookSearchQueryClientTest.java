package org.nhnacademy.book2onandonbookservice.client;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.util.ObjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookSearchQueryClientTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @InjectMocks
    private BookSearchQueryClient bookSearchQueryClient;

    @Test
    @DisplayName("성공: 키워드 없음, 필터 없음, 기본 검색 (MatchAll)")
    void search_NoKeyword_NoFilter() throws IOException {
        BookSearchCondition condition = new BookSearchCondition();
        Pageable pageable = PageRequest.of(0, 10);
        List<Float> vector = null;

        mockElasticsearchResponse();

        Page<BookSearchDocument> result = bookSearchQueryClient.search(condition, pageable, vector);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);

        ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> captor = ArgumentCaptor.forClass(Function.class);
        verify(elasticsearchClient).search(captor.capture(), eq(BookSearchDocument.class));

        SearchRequest request = applyBuilder(captor.getValue());

        assertThat(request.query().bool().must().get(0).bool().must().get(0).isMatchAll()).isTrue();
        assertThat(request.knn()).isEmpty();
    }

    @Test
    @DisplayName("성공: 키워드 검색, 모든 필터 적용")
    void search_WithKeyword_And_Filters() throws IOException {
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword("test-book");
        condition.setCategoryId(1L);
        condition.setCategoryName("IT");
        condition.setTagName("Java");
        condition.setContributorName("Author");
        condition.setPublisherName("Publisher");

        Pageable pageable = PageRequest.of(0, 10);

        mockElasticsearchResponse();

        bookSearchQueryClient.search(condition, pageable, null);

        ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> captor = ArgumentCaptor.forClass(Function.class);
        verify(elasticsearchClient).search(captor.capture(), eq(BookSearchDocument.class));

        SearchRequest request = applyBuilder(captor.getValue());

        assertThat(request.query().bool().must().get(0).bool().must().get(0).isMultiMatch()).isTrue();
        assertThat(request.minScore()).isEqualTo(0.3d);

        long filterCount = request.query().bool().filter().size();
        assertThat(filterCount).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("성공: 벡터 검색 (KNN) 적용 - Sort 없을 때")
    void search_VectorSearch() throws IOException {
        ReflectionTestUtils.setField(bookSearchQueryClient, "indexName", "test-book-index");
        ReflectionTestUtils.setField(bookSearchQueryClient, "embeddingField", "embedding");
        BookSearchCondition condition = new BookSearchCondition();
        Pageable pageable = PageRequest.of(0, 10);
        List<Float> vector = List.of(0.1f, 0.2f, 0.3f);

        mockElasticsearchResponse();

        bookSearchQueryClient.search(condition, pageable, vector);

        ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> captor = ArgumentCaptor.forClass(Function.class);
        verify(elasticsearchClient).search(captor.capture(), eq(BookSearchDocument.class));

        SearchRequest request = applyBuilder(captor.getValue());

        assertThat(request.knn()).hasSize(1);
        assertThat(request.knn().get(0).field()).isEqualTo("embedding");
        assertThat(request.knn().get(0).numCandidates()).isEqualTo(50);
        assertThat(request.sort()).isEmpty();
    }

    @Test
    @DisplayName("성공: 정렬(Sort) 옵션이 있으면 벡터 검색 무시 및 Sort 적용")
    void search_WithSort_IgnoresVector() throws IOException {
        BookSearchCondition condition = new BookSearchCondition();
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "price"));
        List<Float> vector = List.of(0.1f, 0.2f);

        mockElasticsearchResponse();

        bookSearchQueryClient.search(condition, pageable, vector);

        ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> captor = ArgumentCaptor.forClass(Function.class);
        verify(elasticsearchClient).search(captor.capture(), eq(BookSearchDocument.class));

        SearchRequest request = applyBuilder(captor.getValue());

        assertThat(request.knn()).isEmpty();
        assertThat(request.sort()).isNotEmpty();
        assertThat(request.sort().get(0).field().field()).isEqualTo("price");
        assertThat(request.sort().get(0).field().order()).isEqualTo(SortOrder.Desc);
    }

    @Test
    @DisplayName("실패: Elasticsearch IOException 발생 시 RuntimeException 반환")
    void search_IOException() throws IOException {
        BookSearchCondition condition = new BookSearchCondition();
        Pageable pageable = Pageable.unpaged();

        when(elasticsearchClient.search(any(Function.class), eq(BookSearchDocument.class)))
                .thenThrow(new IOException("Elastic connection failed"));

        assertThatThrownBy(() -> bookSearchQueryClient.search(condition, pageable, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("검색 중 오류 발생")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("성공: 필터 값이 Null이거나 빈 문자열이면 필터 추가 안함 (Condition Coverage)")
    void search_Filter_Null_Or_Empty() throws IOException {
        BookSearchCondition condition = new BookSearchCondition();
        condition.setCategoryName("");

        Pageable pageable = PageRequest.of(0, 10);

        mockElasticsearchResponse();

        bookSearchQueryClient.search(condition, pageable, null);

        ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> captor = ArgumentCaptor.forClass(Function.class);
        verify(elasticsearchClient).search(captor.capture(), eq(BookSearchDocument.class));

        SearchRequest request = applyBuilder(captor.getValue());

        long explicitFilters = request.query().bool().filter().stream()
                .filter(f -> !f.isTerm() || !f.term().field().equals("status.keyword"))
                .count();

        assertThat(explicitFilters).isZero();
    }

    private void mockElasticsearchResponse() throws IOException {
        BookSearchDocument doc = new BookSearchDocument();
        Hit<BookSearchDocument> hit = Hit.of(h -> h
                .index("book2onandon-books")
                .id("1")
                .source(doc)
                .score(1.0)
        );

        TotalHits totalHits = TotalHits.of(t -> t.value(1).relation(TotalHitsRelation.Eq));
        HitsMetadata<BookSearchDocument> hitsMetadata = HitsMetadata.of(h -> h
                .hits(List.of(hit))
                .total(totalHits)
        );

        SearchResponse<BookSearchDocument> response = SearchResponse.of(r -> r
                .took(10)
                .timedOut(false)
                .shards(s -> s.successful(1).failed(0).total(1))
                .hits(hitsMetadata)
        );

        when(elasticsearchClient.search(any(Function.class), eq(BookSearchDocument.class)))
                .thenReturn(response);
    }

    private SearchRequest applyBuilder(Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> fn) {
        SearchRequest.Builder builder = new SearchRequest.Builder();
        return fn.apply(builder).build();
    }
}