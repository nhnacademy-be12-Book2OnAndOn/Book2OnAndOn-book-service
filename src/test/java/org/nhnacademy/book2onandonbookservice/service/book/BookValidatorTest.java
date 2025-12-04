package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;

class BookValidatorTest {
    private final BookValidator validator = new BookValidator();

    private BookSaveRequest.BookSaveRequestBuilder createValidRequest() {
        return BookSaveRequest.builder()
                .title("정상 제목")
                .isbn("1234567890123")
                .publishDate(LocalDate.now())
                .priceStandard(10000L)
                .priceSales(9000L)
                .categoryIds(List.of(1L));
    }

    @Test
    @DisplayName("등록 검증 성공: 모든 필수 값이 정상")
    void validateForCreate_Success() {
        BookSaveRequest req = createValidRequest().build();

        assertThatCode(() -> validator.validateForCreate(req)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("등록 실패: 제목이 없음")
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
    @DisplayName("등록 실패: ISBN이 없음")
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
    @DisplayName("등록 실패: 출판일이 없음")
    void validateForCreate_NoPublishDate() {
        BookSaveRequest request = createValidRequest().publishDate(null).build();
        assertThatThrownBy(() -> validator.validateForCreate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("출판일");


    }

    @Test
    @DisplayName("등록 실패: 정가가 없음")
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
    @DisplayName("등록 실패: 판매가가 0이하")
    void validateForCreate_minusPriceSale() {
        BookSaveRequest request = createValidRequest().priceSales(-4L).build();
        assertThatThrownBy(() -> validator.validateForCreate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("판매가");

    }

    @Test
    @DisplayName("등록 실패: 카테고리가 없거나 너무 많을때 ")
    void validateForCreate_InvalidCategoryCount() {
        BookSaveRequest request = createValidRequest().categoryIds(Collections.emptyList()).build();
        assertThatThrownBy(() -> validator.validateForCreate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최소 1개");

        BookSaveRequest request1 = createValidRequest().categoryIds(null).build();
        assertThatThrownBy(() -> validator.validateForCreate(request1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최소 1개");

        List<Long> manyCategories = IntStream.range(0, 11).mapToObj(Long::valueOf).toList();
        BookSaveRequest request2 = createValidRequest().categoryIds(manyCategories).build();
        assertThatThrownBy(() -> validator.validateForCreate(request2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 10개");
    }

    @Test
    @DisplayName("수정 성공: 값이 없거나 정상 범위 일때")
    void validateForUpdate_Success() {
        BookSaveRequest request = BookSaveRequest.builder().build();

        assertThatCode(() -> validator.validateForUpdate(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("수정 실패: 정가,판매가가 음수인경우")
    void validateForCreate_InvalidPrice_Modify() {
        BookSaveRequest request = createValidRequest().priceStandard(-1L).build();
        assertThatThrownBy(() -> validator.validateForUpdate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정가는 0원 이상");

        BookSaveRequest request2 = createValidRequest().priceSales(-1L).build();
        assertThatThrownBy(() -> validator.validateForUpdate(request2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("판매가는 0원 이상");
    }

    @Test
    @DisplayName("수정 실패: 카테고리 리스트가 넘어왔는데 개수가 부적절한 경우")
    void validateForUpdate_InvalidCategory() {
        BookSaveRequest request = BookSaveRequest.builder()
                .categoryIds(Collections.emptyList())
                .build();

        assertThatThrownBy(() -> validator.validateForUpdate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최소 1개");

        List<Long> manyCategories = IntStream.range(0, 11).mapToObj(Long::valueOf).toList();

        BookSaveRequest req = BookSaveRequest.builder().categoryIds(manyCategories).build();

        assertThatThrownBy(() -> validator.validateForUpdate(req)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 10개");

    }
}