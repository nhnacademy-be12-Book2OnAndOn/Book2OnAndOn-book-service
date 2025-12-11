package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookUpdateRequest;

class BookValidatorTest {
    private final BookValidator validator = new BookValidator();

    private BookSaveRequest.BookSaveRequestBuilder createValidRequest() {
        return BookSaveRequest.builder()
                .title("정상 제목")
                .isbn("1234567890123")
                .publishDate(LocalDate.now())
                .priceStandard(10000L)
                .priceSales(9000L)
                .categoryId(1L);
    }

    private BookUpdateRequest.BookUpdateRequestBuilder updateValidRequest() {
        return BookUpdateRequest.builder()
                .title("정상 제목")
                .isbn("1234567890123")
                .publishDate(LocalDate.now())
                .priceStandard(10000L)
                .priceSales(9000L)
                .categoryId(1L);
    }

    @Test
    @DisplayName("등록 검증 성공")
    void validateForCreate_Success() {
        BookSaveRequest req = createValidRequest().build();

        assertThatCode(() -> validator.validateForCreate(req)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("등록 실패: 제목 없음")
    void validateForCreate_NoTitle() {
        BookSaveRequest request = createValidRequest().title("").build();
        assertThatThrownBy(() -> validator.validateForCreate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("도서 제목");

        BookSaveRequest nullTitle = createValidRequest().title(null).build();
        assertThatThrownBy(() -> validator.validateForCreate(nullTitle))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("도서 제목");
    }

    @Test
    @DisplayName("등록 실패: ISBN 없음")
    void validateForCreate_NoISBN() {
        BookSaveRequest request = createValidRequest().isbn("").build();
        assertThatThrownBy(() -> validator.validateForCreate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISBN");

        BookSaveRequest nullIsbn = createValidRequest().isbn(null).build();
        assertThatThrownBy(() -> validator.validateForCreate(nullIsbn))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISBN");
    }

    @Test
    @DisplayName("등록 실패: 출판일 없음")
    void validateForCreate_NoPublishDate() {
        BookSaveRequest request = createValidRequest().publishDate(null).build();
        assertThatThrownBy(() -> validator.validateForCreate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("출판일");
    }

    @Test
    @DisplayName("등록 실패: 정가 없음")
    void validateForCreate_NoPriceStandard() {
        BookSaveRequest request = createValidRequest().priceStandard(null).build();
        assertThatThrownBy(() -> validator.validateForCreate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정가는");

        BookSaveRequest minusPrice = createValidRequest().priceStandard(-3L).build();
        assertThatThrownBy(() -> validator.validateForCreate(minusPrice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정가는");
    }

    @Test
    @DisplayName("등록 실패: 판매가 음수")
    void validateForCreate_minusPriceSale() {
        BookSaveRequest request = createValidRequest().priceSales(-4L).build();
        assertThatThrownBy(() -> validator.validateForCreate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("판매가");
    }

    @Test
    @DisplayName("등록 실패: 카테고리 없음")
    void validateForCreate_InvalidCategoryCount() {
        BookSaveRequest request = createValidRequest().categoryId(null).build();
        assertThatThrownBy(() -> validator.validateForCreate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("카테고리는 필수");
    }

    @Test
    @DisplayName("수정 성공: 카테고리 포함")
    void validateForUpdate_Success() {
        BookUpdateRequest request = BookUpdateRequest.builder()
                .categoryId(1L)
                .build();

        assertThatCode(() -> validator.validateForUpdate(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("수정 실패: 정가 판매가 음수")
    void validateForCreate_InvalidPrice_Modify() {
        BookUpdateRequest request = updateValidRequest().priceStandard(-1L).build();
        assertThatThrownBy(() -> validator.validateForUpdate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정가는 0원 이상");

        BookUpdateRequest request2 = updateValidRequest().priceSales(-1L).build();
        assertThatThrownBy(() -> validator.validateForUpdate(request2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("판매가는 0원 이상");
    }

    @Test
    @DisplayName("수정 실패: 카테고리 null")
    void validateForUpdate_InvalidCategory() {
        BookUpdateRequest request = BookUpdateRequest.builder()
                .categoryId(null)
                .build();

        assertThatThrownBy(() -> validator.validateForUpdate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("카테고리는 필수");
    }
}