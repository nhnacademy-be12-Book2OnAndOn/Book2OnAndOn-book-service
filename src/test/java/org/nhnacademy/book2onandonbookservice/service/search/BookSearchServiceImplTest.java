package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class BookSearchServiceImplTest {

    @InjectMocks
    private BookSearchServiceImpl bookSearchService;

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private OllamaApiClient ollamaApiClient;
    @Mock
    private RerankerApiClient rerankerApiClient;
    @Mock
    private ElasticsearchClient elasticsearchClient;
    @Mock
    private GeminiSearchClient geminiSearchClient;

    @Test
    @DisplayName("검색 결과 없음: ES 결과가 비어있으면 빈 페이지 반환")
    void search_Empty() throws IOException {
        BookSearchCondition condition = new BookSearchCondition();
        Pageable pageable = PageRequest.of(0, 10);

        mockElasticsearchResponse(Collections.emptyList(), 0L);

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result.getContent()).isEmpty();
        then(geminiSearchClient).should(never()).selectBestBooks(any(), any());
    }

    @Test
    @DisplayName("일반 검색: 키워드 없음 -> AI/Rerank 없이 단순 매핑 반환")
    void search_Simple_NoKeyword() throws IOException {
        BookSearchCondition condition = new BookSearchCondition(); // keyword null
        Pageable pageable = PageRequest.of(0, 10);

        BookSearchDocument doc = createDoc(1L, "Test Book");
        mockElasticsearchResponse(List.of(doc), 1L);

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Test Book");

        then(ollamaApiClient).should(never()).getEmbedding(anyString());
        then(rerankerApiClient).should(never()).rerank(anyString(), anyList());
        then(geminiSearchClient).should(never()).selectBestBooks(anyString(), anyList());
    }

    @Test
    @DisplayName("일반 검색: 키워드 있음 + 정확도 정렬 -> 임베딩 & Rerank 실행, Gemini 미실행")
    void search_Keyword_WithRerank_NoGemini() throws IOException {
        String keyword = "Java Programming";
        BookSearchCondition condition = new BookSearchCondition(keyword, null, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10);

        BookSearchDocument doc1 = createDoc(1L, "Java Basic");
        BookSearchDocument doc2 = createDoc(2L, "Advanced Java");
        BookSearchDocument doc3 = createDoc(3L, "Spring Boot");
        List<BookSearchDocument> docs = List.of(doc1, doc2, doc3);

        given(ollamaApiClient.getEmbedding(keyword)).willReturn(List.of(0.1f, 0.2f));

        mockElasticsearchResponse(docs, 3L);

        RerankResult r1 = new RerankResult(0, 0.5f);
        RerankResult r2 = new RerankResult(1, 0.9f);
        RerankResult r3 = new RerankResult(2, 0.4f);

        given(rerankerApiClient.rerank(eq(keyword), anyList()))
                .willReturn(new ArrayList<>(List.of(r1, r2, r3)));

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Advanced Java");
        assertThat(result.getContent().get(1).getTitle()).isEqualTo("Java Basic");

        then(geminiSearchClient).should(never()).selectBestBooks(anyString(), anyList());
    }

    @Test
    @DisplayName("AI 검색: 트리거 단어('추천') 포함 -> Gemini 실행 및 AI 코멘트 매핑")
    void search_Gemini_Triggered_Success() throws IOException {
        String keyword = "Java 책 추천해줘";
        BookSearchCondition condition = new BookSearchCondition(keyword, null, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10);

        BookSearchDocument doc1 = createDoc(1L, "Java 101");
        List<BookSearchDocument> docs = List.of(doc1);

        given(ollamaApiClient.getEmbedding(keyword)).willReturn(List.of(0.1f));
        mockElasticsearchResponse(docs, 1L);

        // [수정됨] 여기도 Reranking이 먼저 실행되므로 ArrayList로 감싸야 합니다.
        given(rerankerApiClient.rerank(anyString(), anyList()))
                .willReturn(new ArrayList<>(List.of(new RerankResult(0, 0.9f))));

        // Gemini 결과는 서비스에서 수정하지 않으므로 List.of() 써도 괜찮음
        AiRecommendation rec = new AiRecommendation(1L, "초보자에게 좋습니다.");
        given(geminiSearchClient.selectBestBooks(anyString(), anyList())).willReturn(List.of(rec));

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAiRecommendation()).isEqualTo("초보자에게 좋습니다.");
        then(geminiSearchClient).should(times(1)).selectBestBooks(anyString(), anyList());
    }

    @Test
    @DisplayName("AI 검색: Gemini 호출했으나 결과 없음 -> Rerank 결과로 Fallback")
    void search_Gemini_Fallback() throws IOException {
        String keyword = "엄청 긴 문장으로 검색을 시도해서 AI를 동작시키려는 의도입니다.";
        BookSearchCondition condition = new BookSearchCondition(keyword, null, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10);

        BookSearchDocument doc1 = createDoc(1L, "Book A");
        List<BookSearchDocument> docs = List.of(doc1);

        given(ollamaApiClient.getEmbedding(keyword)).willReturn(List.of(0.1f));
        mockElasticsearchResponse(docs, 1L);

        // [수정됨] 여기서도 Reranker 리스트 수정이 일어나므로 ArrayList 사용 필수
        given(rerankerApiClient.rerank(anyString(), anyList()))
                .willReturn(new ArrayList<>(List.of(new RerankResult(0, 0.8f))));

        given(geminiSearchClient.selectBestBooks(anyString(), anyList())).willReturn(Collections.emptyList());

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Book A");
        assertThat(result.getContent().get(0).getAiRecommendation()).isNull(); // 코멘트 없음
    }

    @Test
    @DisplayName("스마트 판단: 검색 결과가 3개 미만이면 트리거 단어 없어도 Gemini 실행")
    void search_Gemini_Triggered_By_LowResults() throws IOException {
        String keyword = "희귀한책";
        BookSearchCondition condition = new BookSearchCondition(keyword, null, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10);

        BookSearchDocument doc = createDoc(1L, "Rare Book");
        mockElasticsearchResponse(List.of(doc), 1L);
        given(ollamaApiClient.getEmbedding(keyword)).willReturn(List.of(0.1f));

        // ★ 여기 수정됨: new ArrayList<>(List.of(...)) 사용
        given(rerankerApiClient.rerank(anyString(), anyList()))
                .willReturn(new ArrayList<>(List.of(new RerankResult(0, 0.9f))));

        given(geminiSearchClient.selectBestBooks(anyString(), anyList())).willReturn(Collections.emptyList());

        bookSearchService.search(condition, pageable);

        then(geminiSearchClient).should(times(1)).selectBestBooks(anyString(), anyList());
    }
    @Test
    @DisplayName("정렬 조건: 가격순 정렬 시 Reranker/Gemini 모두 스킵 (성능 최적화)")
    void search_Sort_Price_Skip_AI() throws IOException {
        String keyword = "Java";
        BookSearchCondition condition = new BookSearchCondition(keyword, null, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10, Sort.by("priceStandard").ascending());

        BookSearchDocument doc = createDoc(1L, "Cheap Java");
        mockElasticsearchResponse(List.of(doc), 1L);
        given(ollamaApiClient.getEmbedding(keyword)).willReturn(List.of(0.1f));

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result.getContent()).hasSize(1);

        then(rerankerApiClient).should(never()).rerank(anyString(), anyList());
        then(geminiSearchClient).should(never()).selectBestBooks(anyString(), anyList());
    }

    @Test
    @DisplayName("필터 검색: 카테고리/출판사 등 필터 적용 확인")
    void search_With_Filters() throws IOException {
        Long categoryId = 100L;
        BookSearchCondition condition = new BookSearchCondition(null, categoryId, "IT", "Writer", "Pub", null, null, null);
        Pageable pageable = PageRequest.of(0, 10);

        mockElasticsearchResponse(List.of(createDoc(1L, "Filtered Book")), 1L);

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Filtered Book");
        verify(categoryRepository, never()).findById(categoryId);

    }

    @Test
    @DisplayName("ES 예외 발생 시 RuntimeException으로 래핑")
    void search_Exception_Handling() throws IOException {
        BookSearchCondition condition = new BookSearchCondition();
        Pageable pageable = PageRequest.of(0, 10);

        given(elasticsearchClient.search(any(Function.class), eq(BookSearchDocument.class)))
                .willThrow(new IOException("ES Connection Fail"));

        assertThatThrownBy(() -> bookSearchService.search(condition, pageable))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Search failed");
    }

    @Test
    @DisplayName("페이징 처리: 결과 리스트가 PageSize보다 클 때 서브리스트 반환")
    void search_Pagination_Logic() throws IOException {


        List<BookSearchDocument> docs = new ArrayList<>();
        for(int i=0; i<15; i++) {
            docs.add(createDoc((long)i, "Book " + i));
        }
        mockElasticsearchResponse(docs, 15L);

        Pageable pageable = PageRequest.of(0, 5);
        BookSearchCondition condition = new BookSearchCondition();

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);


        assertThat(result.getContent()).hasSize(5);
        assertThat(result.getTotalElements()).isEqualTo(15);
    }

    @Test
    @DisplayName("페이징 처리: Offset이 전체 결과보다 클 때 빈 페이지 반환")
    void search_Pagination_Out_Of_Bound() throws IOException {
        mockElasticsearchResponse(List.of(createDoc(1L, "Book")), 1L);

        Pageable pageable = PageRequest.of(1, 10);
        BookSearchCondition condition = new BookSearchCondition();

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);


        assertThat(result.getContent()).isEmpty();
    }

    // --- Helpers ---

    private void mockElasticsearchResponse(List<BookSearchDocument> docs, long totalHitsVal) throws IOException {
        List<Hit<BookSearchDocument>> hitList = new ArrayList<>();
        for (BookSearchDocument doc : docs) {
            Hit<BookSearchDocument> hit = mock(Hit.class);
            given(hit.source()).willReturn(doc);
            hitList.add(hit);
        }

        HitsMetadata<BookSearchDocument> hitsMetadata = mock(HitsMetadata.class);
        given(hitsMetadata.hits()).willReturn(hitList);

        TotalHits totalHits = mock(TotalHits.class);
        given(totalHits.value()).willReturn(totalHitsVal);
        given(hitsMetadata.total()).willReturn(totalHits);

        SearchResponse<BookSearchDocument> response = mock(SearchResponse.class);
        given(response.hits()).willReturn(hitsMetadata);

        given(elasticsearchClient.search(any(Function.class), eq(BookSearchDocument.class)))
                .willReturn(response);
    }

    private BookSearchDocument createDoc(Long id, String title) {
        return BookSearchDocument.builder()
                .id(id)
                .title(title)
                .description("Description for " + title)
                .isbn("12345")
                .volume("1")
                .priceStandard(10000L)
                .priceSales(9000L)
                .contributorNames(List.of("Author"))
                .publisherNames(List.of("Publisher"))
                .categoryNames(List.of("Category"))
                .tagNames(List.of("Tag"))
                .imagePath("img.jpg")
                .build();
    }
}