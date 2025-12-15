package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.OllamaApiClient;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.entity.Review;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.repository.BookSearchRepository;

@ExtendWith(MockitoExtension.class)
class BookSearchIndexServiceTest {

    @InjectMocks
    private BookSearchIndexService bookSearchIndexService;

    @Mock
    private BookSearchRepository bookSearchRepository;

    @Mock
    private OllamaApiClient ollamaApiClient;

    @Test
    @DisplayName("인덱싱 성공: 모든 데이터가 존재하는 Happy Path (임베딩 포함)")
    void index_Success_FullData() {
        Category parentCategory = Category.builder().id(1L).categoryName("국내도서").build();
        Category childCategory = Category.builder().id(10L).categoryName("소설").parent(parentCategory).build();

        Tag tag = Tag.builder().tagName("베스트셀러").build();
        Publisher publisher = Publisher.builder().publisherName("전유진출판").build();
        Contributor contributor = Contributor.builder().contributorName("전유진").build();

        Book book = Book.builder()
                .id(1L)
                .isbn("1234567890123")
                .title("테스트 책")
                .description("<p>이 책은 <b>정말</b> 재미있습니다.</p>")
                .volume("1권")
                .publishDate(LocalDate.now())
                .priceStandard(10000L)
                .priceSales(9000L)
                .thumbnail("thumb.jpg")
                .category(childCategory)
                .rating(4.5)
                .likeCount(100L)
                .reviews(Set.of(mock(Review.class), mock(Review.class)))
                .bookTags(new HashSet<>())
                .bookPublishers(new HashSet<>())
                .bookContributors(new HashSet<>())
                .build();

        book.getBookTags().add(BookTag.builder().book(book).tag(tag).build());
        book.getBookPublishers().add(BookPublisher.builder().book(book).publisher(publisher).build());
        book.getBookContributors().add(BookContributor.builder().book(book).contributor(contributor).build());

        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        given(ollamaApiClient.getEmbedding(anyString())).willReturn(mockEmbedding);

        bookSearchIndexService.index(book);

        ArgumentCaptor<BookSearchDocument> captor = ArgumentCaptor.forClass(BookSearchDocument.class);
        then(bookSearchRepository).should(times(1)).save(captor.capture());

        BookSearchDocument doc = captor.getValue();

        assertThat(doc.getId()).isEqualTo(book.getId());
        assertThat(doc.getTitle()).isEqualTo("테스트 책");
        assertThat(doc.getDescription()).isEqualTo("이 책은 정말 재미있습니다.");

        assertThat(doc.getCategoryNames()).containsExactly("국내도서", "소설");
        assertThat(doc.getCategoryIds()).containsExactly("1", "10");

        assertThat(doc.getTagNames()).containsExactly("베스트셀러");
        assertThat(doc.getPublisherNames()).containsExactly("전유진출판");
        assertThat(doc.getContributorNames()).containsExactly("전유진");

        assertThat(doc.getReviewRating()).isEqualTo(4.5);
        assertThat(doc.getReviewCount()).isEqualTo(2L);
        assertThat(doc.getPopularity()).isEqualTo(100L);
        assertThat(doc.getEmbedding()).isEqualTo(mockEmbedding);
    }

    @Test
    @DisplayName("인덱싱 성공: 썸네일이 없을 때 이미지 리스트의 첫 번째 이미지를 사용")
    void index_Success_ThumbnailFallback() {
        BookImage bookImage = BookImage.builder().imagePath("fallback_image.jpg").build();

        Book book = Book.builder()
                .id(2L)
                .title("이미지 테스트")
                .thumbnail(null)
                .images(Set.of(bookImage))
                .bookTags(new HashSet<>())
                .bookPublishers(new HashSet<>())
                .bookContributors(new HashSet<>())
                .build();

        given(ollamaApiClient.getEmbedding(anyString())).willReturn(List.of(0.0f));

        bookSearchIndexService.index(book);


        ArgumentCaptor<BookSearchDocument> captor = ArgumentCaptor.forClass(BookSearchDocument.class);
        then(bookSearchRepository).should().save(captor.capture());

        assertThat(captor.getValue().getImagePath()).isEqualTo("fallback_image.jpg");
    }

    @Test
    @DisplayName("인덱싱 성공: 설명(Description)이 매우 길 경우 Truncate 처리")
    void index_Success_LongDescription() {
        String longDescription = "A".repeat(4000);
        Book book = Book.builder()
                .id(3L)
                .title("긴 설명 책")
                .description(longDescription)
                .bookTags(new HashSet<>())
                .bookPublishers(new HashSet<>())
                .bookContributors(new HashSet<>())
                .build();

        given(ollamaApiClient.getEmbedding(anyString())).willReturn(List.of(0.0f));

        bookSearchIndexService.index(book);

        ArgumentCaptor<BookSearchDocument> captor = ArgumentCaptor.forClass(BookSearchDocument.class);
        then(bookSearchRepository).should().save(captor.capture());

        String savedDescription = captor.getValue().getDescription();
        assertThat(savedDescription).hasSize(4000);
    }

    @Test
    @DisplayName("인덱싱 실패: 내부 로직(임베딩 등) 예외 발생 시 로그만 남기고 조용히 종료 (Fail Safe)")
    void index_Fail_ExceptionCaught() {
        Book book = Book.builder()
                .id(4L)
                .title("에러 유발 책")
                .bookTags(new HashSet<>())
                .bookPublishers(new HashSet<>())
                .bookContributors(new HashSet<>())
                .build();

        willThrow(new RuntimeException("Ollama API Error")).given(ollamaApiClient).getEmbedding(anyString());

        bookSearchIndexService.index(book);

        then(bookSearchRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("인덱싱 성공: 연관관계 및 옵셔널 값들이 없는(Null/Empty) 경우 안전하게 처리")
    void index_Success_NullSafe() {
        Book book = Book.builder()
                .id(5L)
                .title("빈 껍데기 책")
                .rating(null)
                .likeCount(null)
                .reviews(null)
                .bookTags(new HashSet<>())
                .bookPublishers(new HashSet<>())
                .bookContributors(new HashSet<>())
                .build();

        given(ollamaApiClient.getEmbedding(anyString())).willReturn(List.of());

        bookSearchIndexService.index(book);

        ArgumentCaptor<BookSearchDocument> captor = ArgumentCaptor.forClass(BookSearchDocument.class);
        then(bookSearchRepository).should().save(captor.capture());

        BookSearchDocument doc = captor.getValue();

        assertThat(doc.getReviewRating()).isEqualTo(0.0);
        assertThat(doc.getPopularity()).isEqualTo(0L);
        assertThat(doc.getReviewCount()).isEqualTo(0L);
        assertThat(doc.getCategoryNames()).isEmpty();
    }

    @Test
    @DisplayName("인덱스 삭제 성공")
    void deleteIndex_Success() {
        Long bookId = 1L;

        bookSearchIndexService.deleteIndex(bookId);


        then(bookSearchRepository).should(times(1)).deleteById(bookId);
    }
}