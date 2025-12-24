package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.AladinCreateClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiBookCreateClient;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.exception.AladinApiException;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AladinServiceTest {

    @InjectMocks
    private AladinService aladinService;

    @Mock
    private AladinCreateClient aladinClient;

    @Mock
    private GeminiBookCreateClient geminiBookCreateClient;

    @Test
    @DisplayName("도서 검색 성공 - 정상적인 날짜 포맷 (yyyy-MM-dd) 및 제미나이 성공")
    void searchBookInfo_Success() throws JsonProcessingException {

        AladinApiResponse.Item item = new AladinApiResponse.Item();

        ReflectionTestUtils.setField(item, "title", "Test Book");
        ReflectionTestUtils.setField(item, "pubDate", "2023-10-10");
        ReflectionTestUtils.setField(item, "cover", "http://image.com/1.jpg");
        ReflectionTestUtils.setField(item, "author", "Test Author");
        ReflectionTestUtils.setField(item, "publisher", "Test Publisher");
        ReflectionTestUtils.setField(item, "description", "Test Description");
        ReflectionTestUtils.setField(item, "priceStandard", 10000L);
        ReflectionTestUtils.setField(item, "priceSales", 9000L);

        when(aladinClient.searchByIsbn("12345")).thenReturn(item);
        when(geminiBookCreateClient.generateChapter(anyString(), anyString(), anyString())).thenReturn("Chapter 1");

        BookSaveRequest result = aladinService.searchBookInfo("12345");

        assertThat(result.getTitle()).isEqualTo("Test Book");
        assertThat(result.getPublishDate()).isEqualTo(LocalDate.of(2023, 10, 10));
        assertThat(result.getImageUrl()).startsWith("https://");
        assertThat(result.getChapter()).isEqualTo("Chapter 1");
    }

    @Test
    @DisplayName("도서 검색 - 날짜 포맷이 연도(yyyy)만 있을 때")
    void searchBookInfo_YearOnly() throws JsonProcessingException {
        AladinApiResponse.Item item = new AladinApiResponse.Item();
        ReflectionTestUtils.setField(item, "pubDate", "2023");
        ReflectionTestUtils.setField(item, "title", "Year Only Book");

        when(aladinClient.searchByIsbn("123")).thenReturn(item);

        BookSaveRequest result = aladinService.searchBookInfo("123");
        assertThat(result.getPublishDate()).isEqualTo(LocalDate.of(2023, 1, 1));
    }

    @Test
    @DisplayName("도서 검색 - 날짜 포맷 엉망일 때 (현재 날짜 반환)")
    void searchBookInfo_InvalidDate() throws JsonProcessingException {
        AladinApiResponse.Item item = new AladinApiResponse.Item();
        ReflectionTestUtils.setField(item, "pubDate", "invalid-date");
        ReflectionTestUtils.setField(item, "title", "Invalid Date Book");

        when(aladinClient.searchByIsbn("123")).thenReturn(item);

        BookSaveRequest result = aladinService.searchBookInfo("123");
        assertThat(result.getPublishDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("도서 검색 - 날짜 빈 값")
    void searchBookInfo_EmptyDate() throws JsonProcessingException {
        AladinApiResponse.Item item = new AladinApiResponse.Item();
        ReflectionTestUtils.setField(item, "pubDate", null);
        ReflectionTestUtils.setField(item, "title", "Empty Date Book");

        when(aladinClient.searchByIsbn("123")).thenReturn(item);

        BookSaveRequest result = aladinService.searchBookInfo("123");
        assertThat(result.getPublishDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("도서 검색 - 제미나이 호출 실패해도 도서 정보는 반환해야 함")
    void searchBookInfo_GeminiFail() throws JsonProcessingException {
        AladinApiResponse.Item item = new AladinApiResponse.Item();
        ReflectionTestUtils.setField(item, "title", "Gemini Fail Book");
        ReflectionTestUtils.setField(item, "pubDate", "2023-01-01");

        when(aladinClient.searchByIsbn("123")).thenReturn(item);
        when(geminiBookCreateClient.generateChapter(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Gemini Error"));

        BookSaveRequest result = aladinService.searchBookInfo("123");
        assertThat(result.getChapter()).isEmpty();
    }

    @Test
    @DisplayName("도서 검색 - 알라딘 API 오류 발생")
    void searchBookInfo_AladinApiError() throws JsonProcessingException {
        when(aladinClient.searchByIsbn("123")).thenThrow(new RuntimeException("API Fail"));
        assertThatThrownBy(() -> aladinService.searchBookInfo("123"))
                .isInstanceOf(AladinApiException.class);
    }

    @Test
    @DisplayName("도서 검색 - 검색 결과 없음")
    void searchBookInfo_NotFound() throws JsonProcessingException {
        when(aladinClient.searchByIsbn("123")).thenReturn(null);
        assertThatThrownBy(() -> aladinService.searchBookInfo("123"))
                .isInstanceOf(NotFoundBookException.class);
    }
}