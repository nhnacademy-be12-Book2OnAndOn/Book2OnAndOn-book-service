package org.nhnacademy.book2onandonbookservice.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiResponse;
import org.nhnacademy.book2onandonbookservice.exception.GeminiQuotaExceededException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiBookCreateClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GeminiBookCreateClient client;

    private final String BASE_URL = "http://test-gemini.com";
    private final String API_KEY = "test-api-key";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(client, "apiCreateKey", API_KEY);
    }

    @Test
    @DisplayName("성공: API 호출 성공 및 JSON 파싱 후 Chapter 반환")
    void generateChapter_Success() throws JsonProcessingException {
        String isbn = "1234567890123";
        String title = "Test Book";
        String description = "Test Description";
        String rawText = "```json\n{\"chapter\": \"Chapter 1...\"}\n```";
        String cleanJson = "{\"chapter\": \"Chapter 1...\"}";

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(rawText);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        JsonNode mockRoot = mock(JsonNode.class);
        JsonNode mockChapter = mock(JsonNode.class);
        when(objectMapper.readTree(cleanJson)).thenReturn(mockRoot);
        when(mockRoot.has("chapter")).thenReturn(true);
        when(mockRoot.get("chapter")).thenReturn(mockChapter);
        when(mockChapter.asText()).thenReturn("Chapter 1...");

        String result = client.generateChapter(isbn, title, description);

        assertThat(result).isEqualTo("Chapter 1...");
    }

    @Test
    @DisplayName("성공: Description이 Null일 때 빈 문자열로 처리하여 호출")
    void generateChapter_NullDescription() throws JsonProcessingException {
        String isbn = "1234567890123";
        String title = "Test Book";
        String rawText = "{\"chapter\": \"Content\"}";

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(rawText);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        JsonNode mockRoot = mock(JsonNode.class);
        JsonNode mockChapter = mock(JsonNode.class);
        when(objectMapper.readTree(rawText)).thenReturn(mockRoot);
        when(mockRoot.has("chapter")).thenReturn(true);
        when(mockRoot.get("chapter")).thenReturn(mockChapter);
        when(mockChapter.asText()).thenReturn("Content");

        String result = client.generateChapter(isbn, title, null);

        assertThat(result).isEqualTo("Content");
    }

    @Test
    @DisplayName("성공: JSON 응답에 'chapter' 필드가 없는 경우 원본 텍스트 반환")
    void generateChapter_NoChapterField() throws JsonProcessingException {
        String isbn = "1234567890123";
        String rawText = "{\"other\": \"value\"}";

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(rawText);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        JsonNode mockRoot = mock(JsonNode.class);
        when(objectMapper.readTree(rawText)).thenReturn(mockRoot);
        when(mockRoot.has("chapter")).thenReturn(false);

        String result = client.generateChapter(isbn, "Title", "Desc");

        assertThat(result).isEqualTo(rawText);
    }

    @Test
    @DisplayName("실패: API 호출 중 429 Quota Exceeded 발생 시 예외 Rethrow")
    void generateChapter_QuotaExceeded() {
        String isbn = "1234567890123";

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.generateChapter(isbn, "Title", "Desc"))
                .isInstanceOf(GeminiQuotaExceededException.class)
                .hasMessageContaining("Rate Limit Exceeded");
    }

    @Test
    @DisplayName("실패: API 호출 중 400 등 기타 HTTP 오류 발생 시 빈 문자열 반환 (로그 출력)")
    void generateChapter_OtherHttpError() {
        String isbn = "1234567890123";

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        String result = client.generateChapter(isbn, "Title", "Desc");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("실패: API 호출 중 일반 예외 발생 시 빈 문자열 반환 (로그 출력)")
    void generateChapter_GeneralException() {
        String isbn = "1234567890123";

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenThrow(new RuntimeException("Connection Error"));

        String result = client.generateChapter(isbn, "Title", "Desc");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("실패: API 응답이 Null인 경우 빈 문자열 반환")
    void generateChapter_NullResponse() {
        String isbn = "1234567890123";

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(null);

        String result = client.generateChapter(isbn, "Title", "Desc");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("실패: JSON 파싱 중 JsonProcessingException 발생 시 원본 텍스트 반환")
    void generateChapter_JsonProcessingException() throws JsonProcessingException {
        String isbn = "1234567890123";
        String invalidJson = "{invalid-json";

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(invalidJson);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        when(objectMapper.readTree(invalidJson)).thenThrow(new JsonProcessingException("Parse Error"){});

        String result = client.generateChapter(isbn, "Title", "Desc");

        assertThat(result).isEqualTo(invalidJson);
    }
}