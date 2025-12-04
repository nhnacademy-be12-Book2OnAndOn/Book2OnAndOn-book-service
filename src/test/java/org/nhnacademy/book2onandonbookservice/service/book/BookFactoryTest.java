package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.entity.Book;

class BookFactoryTest {
    private final BookFactory bookFactory = new BookFactory();

    @Test
    @DisplayName("createForm 모든 필드가 있는 요청으로 도서 생성")
    void createForm_AllFields() {
        BookSaveRequest req = BookSaveRequest.builder()
                .title("제목임")
                .isbn("1234567890123")
                .volume("1권")
                .chapter("목차")
                .descriptionHtml("설명임")
                .priceStandard(10000L)
                .priceSales(9000L)
                .stockCount(100)
                .isWrapped(true)
                .publishDate(LocalDate.of(2025, 1, 1))
                .status(BookStatus.ON_SALE)
                .build();

        Book book = bookFactory.createFrom(req);
        assertThat(book.getTitle()).isEqualTo("제목임");
        assertThat(book.getIsbn()).isEqualTo("1234567890123");
        assertThat(book.getVolume()).isEqualTo("1권");
        assertThat(book.getChapter()).isEqualTo("목차");
        assertThat(book.getDescription()).isEqualTo("설명임");
        assertThat(book.getPriceStandard()).isEqualTo(10000L);
        assertThat(book.getPriceSales()).isEqualTo(9000L);
        assertThat(book.getStockCount()).isEqualTo(100);
        assertThat(book.getIsWrapped()).isTrue();
        assertThat(book.getPublishDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(book.getStatus()).isEqualTo(BookStatus.ON_SALE);
    }

    @Test
    @DisplayName("createFrom 선택적 필드가 없을때 기본값이 적용되나")
    void createFrom_Default() {
        BookSaveRequest req = BookSaveRequest.builder()
                .title("제목임")
                .isbn("1234567890123")
                .volume("1권")
                .chapter("목차")
                .descriptionHtml("설명임")
                .priceStandard(10000L)
                .priceSales(null)
                .stockCount(null)
                .isWrapped(null)
                .publishDate(LocalDate.of(2025, 1, 1))
                .status(BookStatus.ON_SALE)
                .build();

        Book book = bookFactory.createFrom(req);
        assertThat(book.getPriceSales()).isEqualTo(10000L);
        assertThat(book.getStockCount()).isZero();
        assertThat(book.getIsWrapped()).isFalse();
        assertThat(book.getStatus()).isEqualTo(BookStatus.ON_SALE);
    }

    @Test
    @DisplayName("updateFields: 요청에 값이 있는 필드만 업데이트 된다")
    void updateFields_UpdatePresent() {
        Book book = Book.builder()
                .title("구제목")
                .priceStandard(1000L)
                .status(BookStatus.ON_SALE)
                .build();

        BookSaveRequest newBook = BookSaveRequest.builder()
                .title("신제목")
                .priceStandard(2000L)
                .status(BookStatus.ON_SALE)
                .build();

        bookFactory.updateFields(book, newBook);

        assertThat(book.getTitle()).isEqualTo("신제목");
        assertThat(book.getPriceStandard()).isEqualTo(2000L);
        assertThat(book.getStatus()).isEqualTo(BookStatus.ON_SALE);
    }

    @Test
    @DisplayName("updateFields: 요청 필드가 null이면 기존 값 유지")
    void updateFields_IgnoreNull() {
        Book book = Book.builder()
                .title("유지되나요?")
                .isbn("1111")
                .priceStandard(1000L)
                .stockCount(30)
                .isWrapped(true)
                .build();

        BookSaveRequest emptyRequest = BookSaveRequest.builder().build();

        bookFactory.updateFields(book, emptyRequest);

        assertThat(book.getTitle()).isEqualTo("유지되나요?");
        assertThat(book.getIsbn()).isEqualTo("1111");
        assertThat(book.getPriceStandard()).isEqualTo(1000L);
        assertThat(book.getStockCount()).isEqualTo(30);
        assertThat(book.getIsWrapped()).isTrue();
    }

    @Test
    @DisplayName("updateFields: 모든 필드 업데이트 테스트")
    void updateFields_AllFields() {
        Book book = Book.builder().build();
        BookSaveRequest request = BookSaveRequest.builder()
                .title("타타이이틀틀")
                .volume("볼륨볼륨")
                .chapter("채채텁텁텁!")
                .descriptionHtml("설명설명")
                .isbn("1234123412345")
                .publishDate(LocalDate.now())
                .priceStandard(1000000L)
                .priceSales(900000L)
                .stockCount(23)
                .status(BookStatus.BOOK_DELETED)
                .isWrapped(false)
                .build();

        bookFactory.updateFields(book, request);

        assertThat(book.getTitle()).isEqualTo("타타이이틀틀");
        assertThat(book.getIsbn()).isEqualTo("1234123412345");
        assertThat(book.getVolume()).isEqualTo("볼륨볼륨");
        assertThat(book.getChapter()).isEqualTo("채채텁텁텁!");
        assertThat(book.getDescription()).isEqualTo("설명설명");
        assertThat(book.getPriceStandard()).isEqualTo(1000000L);
        assertThat(book.getPriceSales()).isEqualTo(900000L);
        assertThat(book.getStockCount()).isEqualTo(23);
        assertThat(book.getIsWrapped()).isFalse();
        assertThat(book.getStatus()).isEqualTo(BookStatus.BOOK_DELETED);
    }
}