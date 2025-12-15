package org.nhnacademy.book2onandonbookservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.nhnacademy.book2onandonbookservice.client.GroqApiClient.GroqChatResponse;
import org.nhnacademy.book2onandonbookservice.client.GroqApiClient.GroqChatResponse.Choice;
import org.nhnacademy.book2onandonbookservice.client.GroqApiClient.GroqChatResponse.Message;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class GroqApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GroqApiClient groqApiClient;

    private static final String BASE_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String API_KEY = "dummy-api-key";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(groqApiClient, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(groqApiClient, "apiKey", API_KEY);
    }

    @Test
    @DisplayName("Groq API 호출 및 파싱 성공")
    void extractContent_Success() throws JsonProcessingException {
        String title = "Test Title";
        String description = "Test Description";
        String isbn = "1234567890";
        String jsonResponse = "{\"tags\": [\"tag1\", \"tag2\"], \"chapter\": \"Chapter 1\"}";

        Message message = new Message();
        message.setContent(jsonResponse);
        Choice choice = new Choice();
        choice.setMessage(message);
        GroqChatResponse response = new GroqChatResponse();
        response.setChoices(List.of(choice));

        BookContentDto expectedDto = new BookContentDto(List.of("tag1", "tag2"), "Chapter 1");

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GroqChatResponse.class)))
                .thenReturn(response);
        when(objectMapper.readValue(jsonResponse, BookContentDto.class)).thenReturn(expectedDto);

        BookContentDto result = groqApiClient.extractContent(title, description, isbn);

        assertThat(result).isNotNull();
        assertThat(result.getTags()).hasSize(2).containsExactly("tag1", "tag2");
        assertThat(result.getChapter()).isEqualTo("Chapter 1");

        verify(restTemplate, times(1)).postForObject(anyString(), any(HttpEntity.class), eq(GroqChatResponse.class));
    }

    @Test
    @DisplayName("설명이 Null이어도 API 호출 수행")
    void extractContent_NullDescription() throws JsonProcessingException {
        String title = "Test Title";
        String isbn = "1234567890";
        String jsonResponse = "{}";

        Message message = new Message();
        message.setContent(jsonResponse);
        Choice choice = new Choice();
        choice.setMessage(message);
        GroqChatResponse response = new GroqChatResponse();
        response.setChoices(List.of(choice));

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GroqChatResponse.class)))
                .thenReturn(response);
        when(objectMapper.readValue(jsonResponse, BookContentDto.class)).thenReturn(new BookContentDto());

        groqApiClient.extractContent(title, null, isbn);

        verify(restTemplate, times(1)).postForObject(anyString(), any(HttpEntity.class), eq(GroqChatResponse.class));
    }

    @Test
    @DisplayName("Groq API 호출 실패 시 RuntimeException 발생")
    void extractContent_ApiFailure() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GroqChatResponse.class)))
                .thenThrow(new RestClientException("Connection Refused"));

        assertThatThrownBy(() -> groqApiClient.extractContent("Title", "Desc", "ISBN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Groq Fail");
    }

    @Test
    @DisplayName("응답이 비어있거나 Choices가 없는 경우 예외 발생")
    void extractContent_EmptyResponse() {
        GroqChatResponse emptyResponse = new GroqChatResponse();
        emptyResponse.setChoices(List.of());

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GroqChatResponse.class)))
                .thenReturn(emptyResponse);

        assertThatThrownBy(() -> groqApiClient.extractContent("Title", "Desc", "ISBN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Groq Fail");
    }

    @Test
    @DisplayName("JSON 파싱 실패 시 RuntimeException 발생")
    void extractContent_JsonParsingError() throws JsonProcessingException {
        String jsonResponse = "{invalid_json}";

        Message message = new Message();
        message.setContent(jsonResponse);
        Choice choice = new Choice();
        choice.setMessage(message);
        GroqChatResponse response = new GroqChatResponse();
        response.setChoices(List.of(choice));

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(GroqChatResponse.class)))
                .thenReturn(response);
        when(objectMapper.readValue(jsonResponse, BookContentDto.class))
                .thenThrow(new JsonProcessingException("Parse Error") {});

        assertThatThrownBy(() -> groqApiClient.extractContent("Title", "Desc", "ISBN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Groq Fail");
    }
}