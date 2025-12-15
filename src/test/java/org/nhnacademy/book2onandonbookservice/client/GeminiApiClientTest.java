package org.nhnacademy.book2onandonbookservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
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
    private static final String API_KEY = "dummy-api-key";

    @BeforeEach
    void setUp() {
        // 단일 키 설정으로 변경됨
        ReflectionTestUtils.setField(geminiApiClient, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(geminiApiClient, "apiKey", API_KEY);
    }

    @Test
    @DisplayName("컨텐츠 추출 성공: 정상적인 Json응답 반환 -> BookContentDto 반환")
    void extractContent_success() throws JsonProcessingException {
        String title = "테스트 책";
        String description = "설명설명";
        String isbn = "9781234567890";

        String mockRawJson = "{\"tags\": [\"tag1\", \"tag2\"], \"chapter\": \"1. 서론\"}";
        BookContentDto expectedDto = new BookContentDto(List.of("tag1", "tag2"), "1. 서론");

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(mockRawJson);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        when(objectMapper.readValue(anyString(), eq(BookContentDto.class))).thenReturn(expectedDto);

        // 파라미터에 isbn 추가됨
        BookContentDto result = geminiApiClient.extractContent(title, description, isbn);

        assertThat(result).isNotNull();
        assertThat(result.getTags()).hasSize(2).containsExactly("tag1", "tag2");
        assertThat(result.getChapter()).isEqualTo("1. 서론");

        verify(restTemplate, times(1)).postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class));
    }

    @Test
    @DisplayName("마크다운 제거 로직: 응답에 코드 블록(```json)이 있어도 파싱 성공해야 함")
    void extractContent_markdownCleaning() throws JsonProcessingException {
        String rawTextWithMarkdown = "```json\n {\"tags\": [\"A\"], \"chapter\": \"Ch1\"} \n```";
        BookContentDto expectedDto = new BookContentDto(List.of("A"), "Ch1");

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(rawTextWithMarkdown);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        when(objectMapper.readValue(anyString(), eq(BookContentDto.class))).thenReturn(expectedDto);

        BookContentDto result = geminiApiClient.extractContent("Title", "Desc", "ISBN123");

        assertThat(result).isEqualTo(expectedDto);
    }

    @Test
    @DisplayName("설명이 없어도(Empty) API 호출을 수행해야 함 (ISBN 기반 추론)")
    void extractContent_emptyDescription_ShouldCallApi() throws JsonProcessingException {
        String title = "제목";
        String description = ""; // 빈 설명
        String isbn = "12345";

        String mockRawJson = "{\"tags\": [], \"chapter\": \"\"}";
        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(mockRawJson);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class)))
                .thenReturn(mockResponse);

        when(objectMapper.readValue(anyString(), eq(BookContentDto.class))).thenReturn(BookContentDto.empty());

        geminiApiClient.extractContent(title, description, isbn);

        // 설명이 없어도 호출이 한 번 일어나야 함
        verify(restTemplate, times(1)).postForObject(anyString(), any(HttpEntity.class), eq(GeminiApiResponse.class));
    }

    @Test
    @DisplayName("API 호출 실패 (429 Too Many Requests): Limit 예외를 던져야 함")
    void extractContent_LimitError() {
        // 429 에러 시뮬레이션
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Quota exceeded"));

        // RuntimeException이 발생하고 메시지에 Limit이 포함되어야 함
        assertThatThrownBy(() -> geminiApiClient.extractContent("T", "D", "I"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Limit");
    }

    @Test
    @DisplayName("API 호출 실패 (일반 오류): 예외 발생")
    void extractContent_GeneralError() {
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenThrow(new RestClientException("Internal Server Error"));

        assertThatThrownBy(() -> geminiApiClient.extractContent("T", "D", "I"))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("Internal Server Error");
    }

    @Test
    @DisplayName("JSON 파싱 실패: ObjectMapper 예외 발생 시 빈 DTO 반환")
    void extractContent_jsonParsingError() throws JsonProcessingException {
        String invalidJson = "{ invalid }";

        GeminiApiResponse mockResponse = mock(GeminiApiResponse.class);
        when(mockResponse.getFirstCandidateText()).thenReturn(invalidJson);

        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenReturn(mockResponse);

        when(objectMapper.readValue(anyString(), eq(BookContentDto.class)))
                .thenThrow(new JsonProcessingException("Parse Error") {});

        BookContentDto result = geminiApiClient.extractContent("T", "D", "I");

        assertThat(result.hasNoTags()).isTrue();
        assertThat(result.hasNoChapter()).isTrue();
    }

    @Test
    @DisplayName("응답 처리: Response가 null이거나 텍스트가 null일 때 빈 DTO 반환")
    void parseContentFromJson_nullInputs() {
        // 1. Response 자체가 null
        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn(null);
        BookContentDto result1 = geminiApiClient.extractContent("T", "D", "I");
        assertThat(result1.hasNoTags()).isTrue();

        // 2. Response는 왔는데 텍스트(Candidate)가 null
        GeminiApiResponse emptyResponse = mock(GeminiApiResponse.class);
        when(emptyResponse.getFirstCandidateText()).thenReturn(null);
        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn(emptyResponse);

        BookContentDto result2 = geminiApiClient.extractContent("T", "D", "I");
        assertThat(result2.hasNoTags()).isTrue();
    }
}