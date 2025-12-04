package org.nhnacademy.book2onandonbookservice.parser.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.dto.DataParserDto;
import org.nhnacademy.book2onandonbookservice.exception.DataParserException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class BookCsvParserTest {

    private final BookCsvParser parser = new BookCsvParser();

    private static final String FULL_HEADERS = "ISBN_THIRTEEN_NO,ISBN_NO,TITLE_NM,AUTHR_NM,SEQ_NO,PUBLISHER_NM,PRC_VALUE,TWO_PBLICTE_DE,BOOK_INTRCN_CN,IMAGE_URL,VLM_NM";

    @Test
    void getFileType() {
        assertThat(parser.getFileType()).isEqualTo("csv");
    }

    @Test
    @DisplayName("정상 파싱: 모든 데이터가 유효할 때 DTO 리스트를 반환")
    void parsing_success() throws IOException {
        String csvContent = FULL_HEADERS + "\n" +
                "9781234567890,1234567890,테스트 제목,테스트 작가,1,테스트 출판사,15000,2023-01-01,설명,http://image.url,1권";
        Resource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8));

        List<DataParserDto> result = parser.parsing(resource);

        assertThat(result).hasSize(1);
        DataParserDto dto = result.get(0);
        assertThat(dto.getIsbn()).isEqualTo("9781234567890");
        assertThat(dto.getTitle()).isEqualTo("테스트 제목");
        assertThat(dto.getPublisherName()).isEqualTo("테스트 출판사");
        assertThat(dto.getDescription()).isEqualTo("설명");
        assertThat(dto.getImageUrl()).isEqualTo("http://image.url");
        assertThat(dto.getStandardPrice()).isEqualTo(15000L);
        assertThat(dto.getPublishedAt()).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(dto.getAuthors()).containsExactly("테스트 작가");
    }

    @Test
    @DisplayName("작가/역자 파싱: 구분자나 역할분리로직 확인")
    void parsing_authors_and_translators() throws IOException {
        String csvContent = FULL_HEADERS + "\n" +
                "978111,111,제목,\"김작가 (지은이), 박역자 (옮긴이), 최감수 (감수)\",1,출판사,1000,2023-01-01,설명,,";
        Resource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8));

        List<DataParserDto> result = parser.parsing(resource);
        DataParserDto dto = result.get(0);

        assertThat(dto.getAuthors()).containsExactly("김작가");
        assertThat(dto.getTranslators()).containsExactly("박역자");
    }

    @Test
    @DisplayName("날짜 파싱: 다양한 날짜 형식 및 오류 처리 확인")
    void parsing_data_formats() throws IOException {
        String csvContent = FULL_HEADERS + "\n" +
                "9781,1,제목1,작가,1,출판사,1000,2023-05-05,설명,,\n" +
                "9782,2,제목2,작가,2,출판사,1000,2023-00-00,설명,,\n" +
                "9783,3,제목3,작가,3,출판사,1000,2020년 발행,설명,,\n" +
                "9784,4,제목4,작가,4,출판사,1000,nan,설명,,";

        Resource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8));

        List<DataParserDto> result = parser.parsing(resource);

        assertThat(result).hasSize(4);
        assertThat(result.get(0).getPublishedAt()).isEqualTo(LocalDate.of(2023, 5, 5));
        assertThat(result.get(1).getPublishedAt()).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(result.get(2).getPublishedAt()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(result.get(3).getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("가격 파싱: 실수형 문자열이나 nan 처리 확인")
    void parsing_price_formats() throws IOException {
        String csvContent = FULL_HEADERS + "\n" +
                "9781,1,제목1,작가,1,출판사,15000.0,20230101,설명,,\n" +
                "9782,2,제목2,작가,2,출판사,nan,20230101,설명,,\n" +
                "9783,3,제목3,작가,3,출판사,,20230101,설명,,";

        Resource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8));

        List<DataParserDto> result = parser.parsing(resource);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getStandardPrice()).isEqualTo(15000L);
        assertThat(result.get(1).getStandardPrice()).isZero();
        assertThat(result.get(2).getStandardPrice()).isZero();

    }

    @Test
    @DisplayName("ISBN 로직: ISBN13이 없고 ISBN10만 있을 경우 ISBN10을 사용함")
    void parsing_isbn_fallback() throws IOException {
        String csvContent = FULL_HEADERS + "\n" +
                ",1234567890,제목,작가,1,출판사,1000,2023-01-01,설명,url,";
        Resource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8));

        List<DataParserDto> result = parser.parsing(resource);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsbn()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("Row 스킵: ISBN이 둘 다 없으면 해당 라인은 스킵된다")
    void parsing_skip_row_no_isbn() throws IOException {
        String csvContent = FULL_HEADERS + "\n" +
                "978111,111,정상책,작가,1,출판사,1000,2023-01-01,설명,url,\n" +
                ",,ISBN없는책,작가,2,출판사,1000,2023-01-01,설명,url,";
        Resource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8));

        List<DataParserDto> result = parser.parsing(resource);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("정상책");
    }

    @Test
    @DisplayName("Row 스킵: Title이 없으면 해당 라인은 스킵된다")
    void parsing_skip_row_no_title() throws IOException {
        String csvContent = FULL_HEADERS + "\n" +
                "978111,111,,작가,1,출판사,1000,2023-01-01,설명,url,";
        Resource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8));

        List<DataParserDto> result = parser.parsing(resource);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("헤더 검증 실패: 필수 헤더가 없으면 전체 파싱이 실패한다")
    void parsing_fail_missing_header() {
        String brokenHeader = "ISBN_THIRTEEN_NO,ISBN_NO,AUTHR_NM,SEQ_NO";
        Resource resource = new ByteArrayResource(brokenHeader.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parsing(resource))
                .isInstanceOf(DataParserException.class)
                .hasMessageContaining("필수 컬럼이 없습니다");
    }

    @Test
    @DisplayName("파일 검증: 빈 파일이거나 내용이 없으면 실패한다")
    void parsing_fail_empty_file() {
        Resource resource = new ByteArrayResource("".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parsing(resource))
                .isInstanceOf(DataParserException.class);
    }
}