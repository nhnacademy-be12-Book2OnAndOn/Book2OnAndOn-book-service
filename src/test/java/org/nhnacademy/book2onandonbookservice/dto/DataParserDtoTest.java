package org.nhnacademy.book2onandonbookservice.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class DataParserDtoTest {

    @Test
    @DisplayName("성공: 모든 필드가 정상적으로 파싱되어 생성됨 (Happy Path)")
    void constructor_HappyPath() {
        String seqNo = "1";
        String isbn = "9781234567890";
        String title = "Java Programming";
        String rawAuthorStr = "홍길동 (지은이), Kim (옮긴이), Unknown Person";
        String publisher = "NHN Pub";
        String priceStr = "25000";
        String publishedAtStr = "2024-01-01";
        String description = "Good Book";
        String imageUrl = "http://image.com/1.jpg";
        String volume = "1";

        DataParserDto dto = new DataParserDto(
                seqNo, isbn, title, rawAuthorStr, publisher,
                priceStr, publishedAtStr, description, imageUrl, volume
        );

        assertAll(
                () -> assertThat(dto.getSeqNo()).isEqualTo(seqNo),
                () -> assertThat(dto.getIsbn()).isEqualTo(isbn),
                () -> assertThat(dto.getTitle()).isEqualTo(title),
                () -> assertThat(dto.getPublisherName()).isEqualTo(publisher),
                () -> assertThat(dto.getDescription()).isEqualTo(description),
                () -> assertThat(dto.getImageUrl()).isEqualTo(imageUrl),
                () -> assertThat(dto.getVolume()).isEqualTo(volume),

                () -> assertThat(dto.getStandardPrice()).isEqualTo(25000L),
                () -> assertThat(dto.getSalePrice()).isEqualTo(25000L),

                () -> assertThat(dto.getPublishedAt()).isEqualTo(LocalDate.of(2024, 1, 1)),

                () -> assertThat(dto.getAuthors()).contains("홍길동", "Unknown Person"),
                () -> assertThat(dto.getAuthors()).doesNotContain("Kim"),
                () -> assertThat(dto.getTranslators()).contains("Kim", "Unknown Person"),
                () -> assertThat(dto.getTranslators()).doesNotContain("홍길동")
        );
    }

    @Test
    @DisplayName("성공: 저자 정보가 null이거나 비어있을 때 빈 리스트 반환")
    void constructor_NullAuthors() {
        DataParserDto dto = new DataParserDto(
                "1", "isbn", "title", null, "pub",
                "1000", "2024-01-01", "desc", "img", "vol"
        );

        assertThat(dto.getAuthors()).isEmpty();
        assertThat(dto.getTranslators()).isEmpty();
    }

    @Test
    @DisplayName("성공: 이미지 URL이 null이거나 비어있으면 null로 저장")
    void constructor_NullImage() {
        DataParserDto dtoNull = new DataParserDto(
                "1", "isbn", "title", "auth", "pub",
                "1000", "2024-01-01", "desc", null, "vol"
        );
        DataParserDto dtoEmpty = new DataParserDto(
                "1", "isbn", "title", "auth", "pub",
                "1000", "2024-01-01", "desc", "", "vol"
        );

        assertThat(dtoNull.getImageUrl()).isNull();
        assertThat(dtoEmpty.getImageUrl()).isNull();
    }


    @ParameterizedTest
    @CsvSource({
            "10000, 10000",
            "10000.5, 10000",
            "0, 0",
            "-500, -500"
    })
    @DisplayName("성공: 다양한 숫자 형식의 가격 파싱")
    void parsePrice_Success(String input, Long expected) {
        DataParserDto dto = new DataParserDto(
                "1", "isbn", "title", "auth", "pub",
                input, "2024-01-01", "desc", "img", "vol"
        );
        assertThat(dto.getStandardPrice()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nan", "NAN", "invalid", "1,000"}) // 1,000 throws NumberFormatException
    @NullAndEmptySource
    @DisplayName("실패: 유효하지 않은 가격 문자열은 0L 반환")
    void parsePrice_Fail(String input) {
        DataParserDto dto = new DataParserDto(
                "1", "isbn", "title", "auth", "pub",
                input, "2024-01-01", "desc", "img", "vol"
        );
        assertThat(dto.getStandardPrice()).isZero();
    }


    @ParameterizedTest
    @CsvSource({
            "2024-05-05, 2024-05-05",  // yyyy-MM-dd
            "20240505, 2024-05-05",    // yyyyMMdd
            "202405, 2024-05-01",      // yyyyMM
            "2024, 2024-01-01",        // yyyy

            "2024-05-00, 2024-05-01",
            "2024-00-15, 2024-01-15",
            "20240500, 2024-05-01",
            "20240015, 2024-01-15",
            "202400, 2024-01-01"
    })
    @DisplayName("성공: 날짜 포맷 파싱 및 00일/00월 자동 보정")
    void parseDate_AllPatterns(String input, String expected) {
        DataParserDto dto = new DataParserDto(
                "1", "isbn", "title", "auth", "pub",
                "1000", input, "desc", "img", "vol"
        );
        assertThat(dto.getPublishedAt()).isEqualTo(LocalDate.parse(expected));
    }

    @Test
    @DisplayName("성공: 형식이 맞지 않지만 연도가 포함된 텍스트 (extractYearFromText)")
    void parseDate_FallbackToYearExtraction() {
        DataParserDto dto = new DataParserDto(
                "1", "isbn", "title", "auth", "pub",
                "1000", "Published in 1999", "desc", "img", "vol"
        );
        assertThat(dto.getPublishedAt()).isEqualTo(LocalDate.of(1999, 1, 1));
    }

    @Test
    @DisplayName("성공: 2000년대 연도 추출")
    void parseDate_FallbackToYear2000s() {
        DataParserDto dto = new DataParserDto(
                "1", "isbn", "title", "auth", "pub",
                "1000", "(2023)", "desc", "img", "vol"
        );
        assertThat(dto.getPublishedAt()).isEqualTo(LocalDate.of(2023, 1, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nan", "NAN", "Unknown", "Est. 1800"})
    @NullAndEmptySource
    @DisplayName("실패: 날짜 파싱 및 연도 추출 모두 실패 시 null 반환")
    void parseDate_Fail(String input) {
        DataParserDto dto = new DataParserDto(
                "1", "isbn", "title", "auth", "pub",
                "1000", input, "desc", "img", "vol"
        );
        assertThat(dto.getPublishedAt()).isNull();
    }
}