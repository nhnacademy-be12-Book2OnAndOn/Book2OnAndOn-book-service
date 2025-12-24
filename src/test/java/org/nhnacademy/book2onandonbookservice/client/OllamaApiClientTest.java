package org.nhnacademy.book2onandonbookservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private OllamaApiClient ollamaApiClient;

    private final String OLLAMA_URL = "http://test-ollama:11434/api/embeddings";
    private final String MODEL_NAME = "test-model";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ollamaApiClient, "ollamaUrl", OLLAMA_URL);
        ReflectionTestUtils.setField(ollamaApiClient, "modelName", MODEL_NAME);
    }

    @Test
    @DisplayName("성공: 유효한 텍스트 입력 시 임베딩 리스트 반환")
    void getEmbedding_Success() {
        String text = "valid text";
        List<Float> expectedEmbedding = List.of(0.1f, 0.2f, 0.3f);

        OllamaApiClient.OllamaResponse mockResponse = new OllamaApiClient.OllamaResponse();
        mockResponse.setEmbedding(expectedEmbedding);

        when(restTemplate.postForObject(anyString(), any(OllamaApiClient.OllamaRequest.class), eq(OllamaApiClient.OllamaResponse.class)))
                .thenReturn(mockResponse);

        List<Float> result = ollamaApiClient.getEmbedding(text);

        assertThat(result).isEqualTo(expectedEmbedding);
        verify(restTemplate).postForObject(eq(OLLAMA_URL), any(OllamaApiClient.OllamaRequest.class), eq(OllamaApiClient.OllamaResponse.class));
    }

    @Test
    @DisplayName("실패: 입력 텍스트가 null인 경우 빈 리스트 반환")
    void getEmbedding_NullText() {
        List<Float> result = ollamaApiClient.getEmbedding(null);

        assertThat(result).isEmpty();
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    @DisplayName("실패: 입력 텍스트가 빈 문자열인 경우 빈 리스트 반환")
    void getEmbedding_BlankText() {
        List<Float> result = ollamaApiClient.getEmbedding("");

        assertThat(result).isEmpty();
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    @DisplayName("실패: API 응답이 null인 경우 빈 리스트 반환")
    void getEmbedding_NullResponse() {
        String text = "valid text";

        when(restTemplate.postForObject(anyString(), any(OllamaApiClient.OllamaRequest.class), eq(OllamaApiClient.OllamaResponse.class)))
                .thenReturn(null);

        List<Float> result = ollamaApiClient.getEmbedding(text);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("실패: API 응답 객체는 있으나 embedding 필드가 null인 경우 빈 리스트 반환")
    void getEmbedding_NullEmbeddingField() {
        String text = "valid text";
        OllamaApiClient.OllamaResponse mockResponse = new OllamaApiClient.OllamaResponse();
        mockResponse.setEmbedding(null);

        when(restTemplate.postForObject(anyString(), any(OllamaApiClient.OllamaRequest.class), eq(OllamaApiClient.OllamaResponse.class)))
                .thenReturn(mockResponse);

        List<Float> result = ollamaApiClient.getEmbedding(text);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("실패: RestTemplate 호출 중 예외 발생 시 로그 출력 후 빈 리스트 반환")
    void getEmbedding_ApiException() {
        String text = "valid text";

        when(restTemplate.postForObject(anyString(), any(OllamaApiClient.OllamaRequest.class), eq(OllamaApiClient.OllamaResponse.class)))
                .thenThrow(new RuntimeException("Connection Refused"));

        List<Float> result = ollamaApiClient.getEmbedding(text);

        assertThat(result).isEmpty();
    }
}