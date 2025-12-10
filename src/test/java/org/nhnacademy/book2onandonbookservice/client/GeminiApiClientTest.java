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
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
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
    @DisplayName("초기화 init 테스트")
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
    @DisplayName("컨텐츠 추출 성공: 정상적인 Json응답 반환 -> BookContentDto 반환")
    void extractContent_success() throws JsonProcessingException {
        geminiApiClient.init();
        String title = "테스트 책";
        String description = "설명설명";

        String mockRawJson = "{\"tags\": [\"tag1\", \"tag2\"], \"chapter\": \"1. 서론\"}";
        BookContentDto expectedDto = new BookContentDto(List.of("tag1", "tag2"), "1. 서론");

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(mockRawJson);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        when(objectMapper.readValue(anyString(), eq(BookContentDto.class))).thenReturn(expectedDto);

        BookContentDto result = geminiApiClient.extractContent(title, description);

        assertThat(result).isNotNull();
        assertThat(result.getTags()).hasSize(2).containsExactly("tag1", "tag2");
        assertThat(result.getChapter()).isEqualTo("1. 서론");

        verify(restTemplate, times(1)).postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class));
    }

    @Test
    @DisplayName("마크다운 제거 로직: 응답에 코드 블록(```json)이 있어도 파싱 성공해야 함")
    void extractContent_markdownCleaning() throws JsonProcessingException {
        geminiApiClient.init();
        String rawTextWithMarkdown = "```json\n {\"tags\": [\"A\"], \"chapter\": \"Ch1\"} \n```";
        BookContentDto expectedDto = new BookContentDto(List.of("A"), "Ch1");

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(rawTextWithMarkdown);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        when(objectMapper.readValue(anyString(), eq(BookContentDto.class))).thenReturn(expectedDto);

        BookContentDto result = geminiApiClient.extractContent("Title", "Desc");

        assertThat(result).isEqualTo(expectedDto);
    }

    @Test
    @DisplayName("API 키 로테이션 및 오버플로우 처리: 인덱스가 음수가 되어도 정상 동작해야 함")
    void getNextKey_rotationAndOverflow() {
        geminiApiClient.init();

        AtomicInteger keyIndex = (AtomicInteger) ReflectionTestUtils.getField(geminiApiClient, "keyIndex");
        keyIndex.set(Integer.MAX_VALUE);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(null);

        geminiApiClient.extractContent("T", "D");

        verify(restTemplate).postForObject(contains("key="), any(), any());
    }

    @Test
    @DisplayName("API 키 없음: 키가 로드되지 않았을 때 null 반환")
    void getNextKey_noKeys() {
        ReflectionTestUtils.setField(geminiApiClient, "apiKeys", null);

        BookContentDto result = geminiApiClient.extractContent("T", "D");

        assertThat(result.hasNoTags()).isTrue();
        assertThat(result.hasNoChapter()).isTrue();
    }

    @Test
    @DisplayName("입력값 검증: 설명(Description)이 없으면 API 호출 없이 빈 DTO 반환")
    void extractContent_emptyDescription() {
        String title = "제목";
        String description = "";

        BookContentDto result = geminiApiClient.extractContent(title, description);

        assertThat(result.hasNoTags()).isTrue();
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    @DisplayName("API 호출 실패: RestTemplate 예외 발생 시 빈 DTO 반환 및 로그 처리")
    void extractContent_apiCallError() {
        geminiApiClient.init();

        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenThrow(new RestClientException("Connection refused"));

        BookContentDto result = geminiApiClient.extractContent("T", "D");

        assertThat(result.hasNoTags()).isTrue();
        assertThat(result.hasNoChapter()).isTrue();
    }

    @Test
    @DisplayName("JSON 파싱 실패: ObjectMapper 예외 발생 시 빈 DTO 반환")
    void extractContent_jsonParsingError() throws JsonProcessingException {
        geminiApiClient.init();
        String invalidJson = "{ invalid }";

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(invalidJson);

        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenReturn(mockResponse);

        when(objectMapper.readValue(anyString(), eq(BookContentDto.class)))
                .thenThrow(new JsonProcessingException("Parse Error") {});

        BookContentDto result = geminiApiClient.extractContent("T", "D");

        assertThat(result.hasNoTags()).isTrue();
        assertThat(result.hasNoChapter()).isTrue();
    }

    @Test
    @DisplayName("응답 처리: Response가 null이거나 텍스트가 null일 때 빈 DTO 반환")
    void parseContentFromJson_nullInputs() {
        geminiApiClient.init();

        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn(null);
        BookContentDto result1 = geminiApiClient.extractContent("T", "D");
        assertThat(result1.hasNoTags()).isTrue();

        GeminiApiResponse emptyResponse = mock(GeminiApiResponse.class);
        when(emptyResponse.getFirstCandidateText()).thenReturn(null);
        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn(emptyResponse);

        BookContentDto result2 = geminiApiClient.extractContent("T", "D");
        assertThat(result2.hasNoTags()).isTrue();
    }
}