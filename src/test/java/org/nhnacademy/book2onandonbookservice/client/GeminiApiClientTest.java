package org.nhnacademy.book2onandonbookservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiResponse;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class GeminiApiClientTest {
    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GeminiApiClient geminiApiClient;

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final String RAW_API_KEY = "key1,key2,key3";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(geminiApiClient, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(geminiApiClient, "rawApikey", RAW_API_KEY);
    }

    @Test
    @DisplayName("초기화 init테스트")
    void init_Success() {
        geminiApiClient.init();

        String[] keys = (String[]) ReflectionTestUtils.getField(geminiApiClient, "apiKeys");
        assertThat(keys).isNotNull().hasSize(3).containsExactly("key1", "key2", "key3");
    }

    @Test
    @DisplayName("초기화 테스트 : rawApikey가 null 이라면")
    void init_nullApiKey() {
        ReflectionTestUtils.setField(geminiApiClient, "rawApikey", null);
        geminiApiClient.init();
        String[] keys = (String[]) ReflectionTestUtils.getField(geminiApiClient, "apiKeys");
        assertThat(keys).isNull();

    }

    @Test
    @DisplayName("태그 추출 성공: 정상적인 Json응답 반환 -> 태그리스트반환")
    void tags_success() throws JsonProcessingException {
        geminiApiClient.init();
        String title = "테스트 책";
        String description = "설명설명";
        String mockRawJson = "[\"tag1\", \"tag2\", \"tag3\"]";
        List<String> tags = List.of("tag1", "tag2", "tag3");

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(mockRawJson);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        when(objectMapper.readValue(anyString(), eq(List.class))).thenReturn(tags);

        List<String> result = geminiApiClient.extractTags(title, description);
        assertThat(result).hasSize(3).containsExactly("tag1", "tag2", "tag3");

        verify(restTemplate, times(1)).postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class));

    }

    @Test
    @DisplayName("마크다운 제거 로직: 응답에 코드 블록(```json)이 있어도 파싱 성공해야 함")
    void extractTags_markdownCleaning() throws JsonProcessingException {
        geminiApiClient.init();
        String rawTextWithMarkdown = "```json\n [\"tagA\", \"tagB\"] \n```";
        List<String> expectedTags = List.of("tagA", "tagB");

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(rawTextWithMarkdown);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        when(objectMapper.readValue("[\"tagA\", \"tagB\"]", List.class)).thenReturn(expectedTags);

        List<String> result = geminiApiClient.extractTags("Title", "Desc");

        assertThat(result).isEqualTo(expectedTags);
    }

    @Test
    @DisplayName("API 키 로테이션 및 오버플로우 처리: 인덱스가 음수가 되어도 정상 동작해야 함")
    void getNextKey_rotationAndOverflow() {
        geminiApiClient.init();

        AtomicInteger keyIndex = (AtomicInteger) ReflectionTestUtils.getField(geminiApiClient, "keyIndex");
        keyIndex.set(Integer.MAX_VALUE);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(null);

        geminiApiClient.extractTags("T", "D");

        verify(restTemplate).postForObject(contains("key="), any(), any());
    }

    @Test
    @DisplayName("API 키 없음: 키가 로드되지 않았을 때 빈 문자열 키 사용 (예외 발생 안 함)")
    void getNextKey_noKeys() {
        ReflectionTestUtils.setField(geminiApiClient, "apiKeys", null);

        List<String> result = geminiApiClient.extractTags("T", "D");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("입력값 검증: 설명(Description)이 없으면 API 호출 없이 빈 리스트 반환")
    void extractTags_emptyDescription() {
        String title = "제목";
        String description = "";

        List<String> result = geminiApiClient.extractTags(title, description);

        assertThat(result).isEmpty();
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    @DisplayName("API 호출 실패: RestTemplate 예외 발생 시 빈 리스트 반환 및 로그 처리")
    void extractTags_apiCallError() {
        geminiApiClient.init();

        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenThrow(new RestClientException("Connection refused"));

        List<String> result = geminiApiClient.extractTags("T", "D");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("JSON 파싱 실패: ObjectMapper 예외 발생 시 빈 리스트 반환")
    void extractTags_jsonParsingError() throws JsonProcessingException {
        geminiApiClient.init();
        String invalidJson = "{ invalid }";

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(invalidJson);

        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenReturn(mockResponse);

        when(objectMapper.readValue(anyString(), eq(List.class)))
                .thenThrow(new JsonProcessingException("Parse Error") {
                });

        List<String> result = geminiApiClient.extractTags("T", "D");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("응답 처리: Response가 null이거나 텍스트가 null일 때 빈 리스트 반환")
    void parseTagsFromJson_nullInputs() {
        geminiApiClient.init();

        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn(null);
        assertThat(geminiApiClient.extractTags("T", "D")).isEmpty();

        GeminiApiResponse emptyResponse = mock(GeminiApiResponse.class);
        when(emptyResponse.getFirstCandidateText()).thenReturn(null);
        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn(emptyResponse);

        assertThat(geminiApiClient.extractTags("T", "D")).isEmpty();
    }

}