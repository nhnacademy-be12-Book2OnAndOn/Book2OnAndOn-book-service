package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.HashSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookCategory;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.repository.BookSearchRepository;

@ExtendWith(MockitoExtension.class)
class BookSearchIndexServiceTest {
    @InjectMocks
    private BookSearchIndexService bookSearchIndexService;

    @Mock
    private BookSearchRepository bookSearchRepository;

    @Test
    @DisplayName("인덱싱 성공")
    void index_success() {
        Category category = Category.builder().id(10L).categoryName("국내도서").build();

        Tag tag = Tag.builder().tagName("베스트셀러").build();
        Publisher publisher = Publisher.builder().publisherName("전유진출판").build();
        Contributor contributor = Contributor.builder().contributorName("전유진").build();

        Book book = Book.builder()
                .id(1L)
                .isbn("1234567890123")
                .title("테스트 책")
                .volume("1권")
                .publishDate(LocalDate.now())
                .priceStandard(10000L)
                .priceSales(9000L)
                .bookCategories(new HashSet<>())
                .bookTags(new HashSet<>())
                .bookPublishers(new HashSet<>())
                .bookContributors(new HashSet<>())
                .build();

        book.getBookCategories().add(BookCategory.builder().book(book).category(category).build());
        book.getBookTags().add(BookTag.builder().book(book).tag(tag).build());
        book.getBookPublishers().add(BookPublisher.builder().book(book).publisher(publisher).build());
        book.getBookContributors().add(BookContributor.builder().book(book).contributor(contributor).build());

        bookSearchIndexService.index(book);

        ArgumentCaptor<BookSearchDocument> captor = ArgumentCaptor.forClass(BookSearchDocument.class);

        verify(bookSearchRepository).save(captor.capture());

        BookSearchDocument doc = captor.getValue();

        assertThat(doc.getId()).isEqualTo(book.getId());
        assertThat(doc.getIsbn()).isEqualTo(book.getIsbn());
        assertThat(doc.getTitle()).isEqualTo(book.getTitle());
        assertThat(doc.getVolume()).isEqualTo(book.getVolume());
        assertThat(doc.getPriceStandard()).isEqualTo(book.getPriceStandard());
        assertThat(doc.getPriceSales()).isEqualTo(book.getPriceSales());

        assertThat(doc.getCategoryNames()).containsExactly("국내도서");
        assertThat(doc.getCategoryIds()).containsExactly("10");
        assertThat(doc.getTagNames()).containsExactly("베스트셀러");
        assertThat(doc.getPublisherNames()).containsExactly("전유진출판");
        assertThat(doc.getContributorNames()).containsExactly("전유진");
    }

    @Test
    @DisplayName("인덱싱 성공: 연관관계가 없는 경우 빈 리스트로 변환됨")
    void index_Success_NoRelations() {
        Book book = Book.builder()
                .id(1L)
                .title("테스트 책")
                .bookCategories(new HashSet<>())
                .bookTags(new HashSet<>())
                .bookPublishers(new HashSet<>())
                .bookContributors(new HashSet<>())
                .build();

        bookSearchIndexService.index(book);
        ArgumentCaptor<BookSearchDocument> captor = ArgumentCaptor.forClass(BookSearchDocument.class);

        verify(bookSearchRepository).save(captor.capture());

        BookSearchDocument doc = captor.getValue();

        assertThat(doc.getId()).isEqualTo(1L);
        assertThat(doc.getCategoryNames()).isEmpty();
        assertThat(doc.getTagNames()).isEmpty();
        assertThat(doc.getPublisherNames()).isEmpty();
        assertThat(doc.getContributorNames()).isEmpty();
    }

    @Test
    @DisplayName("인덱싱 삭제 성공")
    void deleteIndex_Success() {
        Long bookId = 1L;
        bookSearchIndexService.deleteIndex(bookId);

        verify(bookSearchRepository).deleteById(bookId);

    }
}