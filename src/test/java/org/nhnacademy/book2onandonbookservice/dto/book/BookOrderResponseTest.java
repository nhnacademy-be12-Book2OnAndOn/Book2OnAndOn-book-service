package org.nhnacademy.book2onandonbookservice.dto.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.Category;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookOrderResponseTest {

    @Test
    @DisplayName("성공: 썸네일 컬럼이 있고 모든 필드가 정상인 경우 (Happy Path)")
    void from_FullData_WithDirectThumbnail() {
        Book book = mock(Book.class);
        when(book.getId()).thenReturn(1L);
        when(book.getTitle()).thenReturn("Test Book");
        when(book.getPriceSales()).thenReturn(10000L);
        when(book.getThumbnail()).thenReturn("direct-thumb.jpg");
        when(book.getIsWrapped()).thenReturn(true);
        when(book.getStockCount()).thenReturn(50);
        when(book.getStatus()).thenReturn(BookStatus.ON_SALE);

        Category category = mock(Category.class);
        when(category.getId()).thenReturn(10L);
        when(book.getCategory()).thenReturn(category);

        BookOrderResponse response = BookOrderResponse.from(book);

        assertThat(response.getBookId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Test Book");
        assertThat(response.getPriceSales()).isEqualTo(10000L);
        assertThat(response.getImageUrl()).isEqualTo("direct-thumb.jpg");
        assertThat(response.isPackable()).isTrue();
        assertThat(response.getStockCount()).isEqualTo(50);
        assertThat(response.getStockStatus()).isEqualTo(BookStatus.ON_SALE);
        assertThat(response.getCategoryId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("성공: 썸네일 컬럼이 없고 이미지 리스트가 있는 경우 첫 번째 이미지 사용")
    void from_NoThumbnailColumn_WithImageList() {
        Book book = mock(Book.class);
        when(book.getThumbnail()).thenReturn(null);

        BookImage image1 = mock(BookImage.class); when(image1.getImagePath()).thenReturn("image1.jpg");
        BookImage image2 = mock(BookImage.class); when(image2.getImagePath()).thenReturn("image2.jpg");
        when(book.getImages()).thenReturn(Set.of(image1, image2));

        when(book.getId()).thenReturn(1L);
        when(book.getTitle()).thenReturn("Title");
        when(book.getPriceSales()).thenReturn(1000L);
        when(book.getIsWrapped()).thenReturn(false);
        when(book.getStockCount()).thenReturn(10);
        when(book.getStatus()).thenReturn(BookStatus.ON_SALE);

        Category category = mock(Category.class);
        when(category.getId()).thenReturn(10L);
        when(book.getCategory()).thenReturn(category);

        BookOrderResponse response = BookOrderResponse.from(book);

        assertThat(response.getImageUrl()).isIn("image1.jpg", "image2.jpg");
    }

    @Test
    @DisplayName("성공: 썸네일 컬럼도 없고 이미지 리스트도 비어있는 경우 기본 이미지 사용")
    void from_NoThumbnail_NoImages() {
        Book book = mock(Book.class);
        when(book.getThumbnail()).thenReturn("");
        when(book.getImages()).thenReturn(Collections.emptySet());

        when(book.getId()).thenReturn(1L);
        when(book.getTitle()).thenReturn("Title");
        when(book.getPriceSales()).thenReturn(1000L);
        when(book.getIsWrapped()).thenReturn(false);
        when(book.getStockCount()).thenReturn(10);
        when(book.getStatus()).thenReturn(BookStatus.ON_SALE);

        Category category = mock(Category.class);
        when(category.getId()).thenReturn(10L);
        when(book.getCategory()).thenReturn(category);

        BookOrderResponse response = BookOrderResponse.from(book);

        assertThat(response.getImageUrl()).isEqualTo("/images/no-image.png");
    }

    @Test
    @DisplayName("성공: Lombok 생성자, Setter, Builder 테스트")
    void lombok_Methods_Success() {
        BookOrderResponse dto = new BookOrderResponse();
        dto.setBookId(1L);
        dto.setTitle("Title");
        dto.setPriceSales(5000L);
        dto.setImageUrl("img.jpg");
        dto.setPackable(true);
        dto.setStockCount(100);
        dto.setStockStatus(BookStatus.ON_SALE);
        dto.setCategoryId(5L);

        assertThat(dto.getBookId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("Title");
        assertThat(dto.getPriceSales()).isEqualTo(5000L);
        assertThat(dto.getImageUrl()).isEqualTo("img.jpg");
        assertThat(dto.isPackable()).isTrue();
        assertThat(dto.getStockCount()).isEqualTo(100);
        assertThat(dto.getStockStatus()).isEqualTo(BookStatus.ON_SALE);
        assertThat(dto.getCategoryId()).isEqualTo(5L);

        BookOrderResponse allArgs = new BookOrderResponse(
                2L, "Title2", 6000L,6000L, "img2.jpg", false, 10, BookStatus.BOOK_DELETED, 3L
        );
        assertThat(allArgs.getBookId()).isEqualTo(2L);
        assertThat(allArgs.getStockStatus()).isEqualTo(BookStatus.BOOK_DELETED);

        BookOrderResponse builder = BookOrderResponse.builder()
                .bookId(3L)
                .title("Title3")
                .build();
        assertThat(builder.getBookId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("실패: Book 객체가 null인 경우 NPE 발생")
    void from_NullBook_Fail() {
        assertThatThrownBy(() -> BookOrderResponse.from(null))
                .isInstanceOf(NullPointerException.class);
    }
}