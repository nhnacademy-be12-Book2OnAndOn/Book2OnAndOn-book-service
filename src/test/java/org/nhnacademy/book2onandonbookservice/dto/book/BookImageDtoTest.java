package org.nhnacademy.book2onandonbookservice.dto.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookImageDtoTest {

    @Test
    @DisplayName("성공: Entity를 DTO로 변환")
    void from_Success() {
        BookImage bookImage = mock(BookImage.class);
        when(bookImage.getId()).thenReturn(100L);
        when(bookImage.getImagePath()).thenReturn("http://test-image.com/1.jpg");

        BookImageDto result = BookImageDto.from(bookImage);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getUrl()).isEqualTo("http://test-image.com/1.jpg");
    }

    @Test
    @DisplayName("성공: Builder 패턴 동작 확인")
    void builder_Success() {
        BookImageDto dto = BookImageDto.builder()
                .id(1L)
                .url("test-url")
                .build();

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getUrl()).isEqualTo("test-url");
    }

    @Test
    @DisplayName("성공: AllArgsConstructor 동작 확인")
    void allArgsConstructor_Success() {
        BookImageDto dto = new BookImageDto(2L, "url2");

        assertThat(dto.getId()).isEqualTo(2L);
        assertThat(dto.getUrl()).isEqualTo("url2");
    }

    @Test
    @DisplayName("성공: NoArgsConstructor 동작 확인")
    void noArgsConstructor_Success() {
        BookImageDto dto = new BookImageDto();

        assertThat(dto.getId()).isNull();
        assertThat(dto.getUrl()).isNull();
    }

    @Test
    @DisplayName("실패: 입력 Entity가 null일 경우 NPE 발생")
    void from_NullEntity_Fail() {
        assertThatThrownBy(() -> BookImageDto.from(null))
                .isInstanceOf(NullPointerException.class);
    }
}