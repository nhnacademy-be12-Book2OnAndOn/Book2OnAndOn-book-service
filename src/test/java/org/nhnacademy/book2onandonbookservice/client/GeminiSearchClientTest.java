package org.nhnacademy.book2onandonbookservice.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiRequest;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiResponse;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchDocument;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiSearchClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GeminiSearchClient client;

    private final String BASE_URL = "http://test-gemini.com";
    private final String API_KEY = "test-key";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(client, "apiSearchKey", API_KEY);
    }

    @Test
    @DisplayName("성공: 정상적인 도서 목록과 API 응답 처리")
    void selectBestBooks_Success() throws JsonProcessingException {
        BookSearchDocument doc1 = BookSearchDocument.builder()
                .id(1L)
                .title("Book1")
                .isbn("ISBN1")
                .publishDate(LocalDate.now())
                .description("Desc1")
                .build();

        List<BookSearchDocument> candidates = List.of(doc1);
        String userQuery = "recommend me";

        String rawJson = "```json\n[{\"id\":1, \"reason\":\"Good\"}]\n```";
        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(rawJson);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        List<GeminiSearchClient.AiRecommendation> expectedList = List.of(new GeminiSearchClient.AiRecommendation(1L, "Good"));
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(expectedList);

        List<GeminiSearchClient.AiRecommendation> result = client.selectBestBooks(userQuery, candidates);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getReason()).isEqualTo("Good");
    }

    @Test
    @DisplayName("성공: Truncate 로직 확인 (긴 설명 및 null 설명)")
    void selectBestBooks_TruncateLogic() {
        String longDesc = "A".repeat(150);

        BookSearchDocument docLong = BookSearchDocument.builder()
                .id(1L)
                .title("Long")
                .isbn("ISBN1")
                .publishDate(LocalDate.now())
                .description(longDesc)
                .build();

        BookSearchDocument docNull = BookSearchDocument.builder()
                .id(2L)
                .title("Null")
                .isbn("ISBN2")
                .publishDate(LocalDate.now())
                .description(null)
                .build();

        List<BookSearchDocument> candidates = List.of(docLong, docNull);

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn("[]");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        client.selectBestBooks("query", candidates);

        ArgumentCaptor<HttpEntity<GeminiApiRequest>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(GeminiApiResponse.class));

        GeminiApiRequest requestBody = captor.getValue().getBody();
        assertThat(requestBody).isNotNull();
        String prompt = requestBody.getContents().get(0).getParts().get(0).getText();

        assertThat(prompt).contains("ID:1");
        assertThat(prompt).contains("...");
        assertThat(prompt).contains("ID:2");
    }

    @Test
    @DisplayName("실패: 후보군이 비어있으면 API 호출 없이 빈 리스트 반환")
    void selectBestBooks_EmptyCandidates() {
        List<GeminiSearchClient.AiRecommendation> result = client.selectBestBooks("query", Collections.emptyList());

        assertThat(result).isEmpty();
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    @DisplayName("실패: API 호출 중 예외 발생 시 빈 리스트 반환")
    void selectBestBooks_ApiException() {
        BookSearchDocument doc = BookSearchDocument.builder()
                .id(1L)
                .description("desc")
                .build();
        List<BookSearchDocument> candidates = List.of(doc);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenThrow(new RuntimeException("Network Error"));

        List<GeminiSearchClient.AiRecommendation> result = client.selectBestBooks("query", candidates);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("실패: API 응답이 null인 경우 빈 리스트 반환")
    void selectBestBooks_NullApiResponse() {
        BookSearchDocument doc = BookSearchDocument.builder()
                .id(1L)
                .description("desc")
                .build();
        List<BookSearchDocument> candidates = List.of(doc);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(null);

        List<GeminiSearchClient.AiRecommendation> result = client.selectBestBooks("query", candidates);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("실패: 파싱 - 응답 텍스트가 null인 경우")
    void selectBestBooks_Parse_NullText() {
        BookSearchDocument doc = BookSearchDocument.builder()
                .id(1L)
                .description("desc")
                .build();
        List<BookSearchDocument> candidates = List.of(doc);

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(null);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        List<GeminiSearchClient.AiRecommendation> result = client.selectBestBooks("query", candidates);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("실패: 파싱 - 유효한 JSON 배열 대괄호가 없는 경우")
    void selectBestBooks_Parse_NoBrackets() {
        BookSearchDocument doc = BookSearchDocument.builder()
                .id(1L)
                .description("desc")
                .build();
        List<BookSearchDocument> candidates = List.of(doc);

        String invalidResponse = "I cannot recommend any books.";
        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(invalidResponse);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        List<GeminiSearchClient.AiRecommendation> result = client.selectBestBooks("query", candidates);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("실패: 파싱 - JSON 매핑 중 예외 발생")
    void selectBestBooks_Parse_JsonException() throws JsonProcessingException {
        BookSearchDocument doc = BookSearchDocument.builder()
                .id(1L)
                .description("desc")
                .build();
        List<BookSearchDocument> candidates = List.of(doc);

        String rawJson = "[{invalid json}]";
        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(rawJson);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new JsonProcessingException("Parse Error"){});

        List<GeminiSearchClient.AiRecommendation> result = client.selectBestBooks("query", candidates);

        assertThat(result).isEmpty();
    }
}