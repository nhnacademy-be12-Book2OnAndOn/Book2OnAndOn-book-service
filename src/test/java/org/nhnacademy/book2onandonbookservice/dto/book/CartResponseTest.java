package org.nhnacademy.book2onandonbookservice.dto.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CartResponseTest {

    @Test
    @DisplayName("성공: 썸네일 컬럼 존재, 일반 판매 상태 (SELLING)")
    void from_ThumbnailColumn_SellingStatus() {
        Book book = mock(Book.class);
        when(book.getId()).thenReturn(1L);
        when(book.getTitle()).thenReturn("Test Book");
        when(book.getPriceSales()).thenReturn(9000L);
        when(book.getPriceStandard()).thenReturn(10000L);
        when(book.getStockCount()).thenReturn(50);
        when(book.getStatus()).thenReturn(BookStatus.ON_SALE);
        when(book.getThumbnail()).thenReturn("direct-thumb.jpg");

        CartResponse response = CartResponse.from(book);

        assertThat(response.getBookId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Test Book");
        assertThat(response.getSalePrice()).isEqualTo(9000);
        assertThat(response.getOriginalPrice()).isEqualTo(10000);
        assertThat(response.getStockCount()).isEqualTo(50);
        assertThat(response.getThumbnailUrl()).isEqualTo("direct-thumb.jpg");
        assertThat(response.isSaleEnded()).isFalse();
        assertThat(response.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("성공: 썸네일 컬럼 없음, 이미지 리스트 존재, 품절 상태 (SOLD_OUT)")
    void from_ImageList_SoldOutStatus() {
        Book book = mock(Book.class);
        when(book.getId()).thenReturn(2L);
        when(book.getTitle()).thenReturn("Sold Out Book");
        when(book.getPriceSales()).thenReturn(5000L);
        when(book.getPriceStandard()).thenReturn(6000L);
        when(book.getStockCount()).thenReturn(0);
        when(book.getStatus()).thenReturn(BookStatus.SOLD_OUT);

        when(book.getThumbnail()).thenReturn(null);

        BookImage image = mock(BookImage.class);
        when(image.getImagePath()).thenReturn("image-from-list.jpg");
        when(book.getImages()).thenReturn(Set.of(image));

        CartResponse response = CartResponse.from(book);

        assertThat(response.getThumbnailUrl()).isEqualTo("image-from-list.jpg");
        assertThat(response.isSaleEnded()).isTrue();
        assertThat(response.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("성공: 썸네일 없음, 이미지 리스트 없음(기본값), 재고 없음 상태 (OUT_OF_STOCK)")
    void from_NoImage_OutOfStockStatus() {
        Book book = mock(Book.class);
        when(book.getId()).thenReturn(3L);
        when(book.getTitle()).thenReturn("No Stock Book");
        when(book.getPriceSales()).thenReturn(5000L);
        when(book.getPriceStandard()).thenReturn(6000L);
        when(book.getStockCount()).thenReturn(0);
        when(book.getStatus()).thenReturn(BookStatus.OUT_OF_STOCK);

        when(book.getThumbnail()).thenReturn("");
        when(book.getImages()).thenReturn(Collections.emptySet());

        CartResponse response = CartResponse.from(book);

        assertThat(response.getThumbnailUrl()).isEqualTo("/images/no-image.png");
        assertThat(response.isSaleEnded()).isTrue();
        assertThat(response.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("성공: 삭제된 도서 상태 (BOOK_DELETED)")
    void from_DeletedStatus() {
        Book book = mock(Book.class);
        when(book.getId()).thenReturn(4L);
        when(book.getTitle()).thenReturn("Deleted Book");
        when(book.getPriceSales()).thenReturn(1000L);
        when(book.getPriceStandard()).thenReturn(1000L);
        when(book.getStockCount()).thenReturn(0);
        when(book.getStatus()).thenReturn(BookStatus.BOOK_DELETED);
        when(book.getThumbnail()).thenReturn("thumb.jpg");

        CartResponse response = CartResponse.from(book);

        assertThat(response.isSaleEnded()).isFalse();
        assertThat(response.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("성공: Lombok 메서드 (Setter, Constructor, Builder) 테스트")
    void lombokMethods_Success() {
        CartResponse dto = new CartResponse();
        dto.setBookId(10L);
        dto.setTitle("Lombok Title");
        dto.setThumbnailUrl("url");
        dto.setOriginalPrice(2000);
        dto.setSalePrice(1000);
        dto.setStockCount(5);
        dto.setSaleEnded(true);
        dto.setDeleted(true);

        assertThat(dto.getBookId()).isEqualTo(10L);
        assertThat(dto.getTitle()).isEqualTo("Lombok Title");

        CartResponse allArgs = new CartResponse(
                11L, "AllArgs", "url2", 3000, 2000, 10, false, false
        );
        assertThat(allArgs.getBookId()).isEqualTo(11L);

        CartResponse builder = CartResponse.builder()
                .bookId(12L)
                .title("Builder")
                .build();
        assertThat(builder.getBookId()).isEqualTo(12L);
    }

    @Test
    @DisplayName("실패: Book 객체가 null인 경우 NPE 발생")
    void from_NullBook_Fail() {
        assertThatThrownBy(() -> CartResponse.from(null))
                .isInstanceOf(NullPointerException.class);
    }
}