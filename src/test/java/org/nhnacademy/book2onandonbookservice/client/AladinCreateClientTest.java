package org.nhnacademy.book2onandonbookservice.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.exception.AladinApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AladinCreateClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AladinCreateClient aladinCreateClient;

    private static final String BASE_URL = "http://test-aladin.com";
    private static final String TTB_KEY = "test-create-ttb-key";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aladinCreateClient, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(aladinCreateClient, "ttbKey", TTB_KEY);
    }

    @Test
    @DisplayName("성공: 유효한 ISBN으로 검색 시 Item 반환")
    void searchByIsbn_Success() throws JsonProcessingException {
        String isbn = "9781234567890";
        String jsonResponse = "{\"version\":\"20131101\", \"item\":[{\"title\":\"Test Book\"}]}";

        AladinApiResponse mockResponse = new AladinApiResponse();
        AladinApiResponse.Item mockItem = new AladinApiResponse.Item();

        ReflectionTestUtils.setField(mockResponse, "item", List.of(mockItem));

        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonResponse));
        when(objectMapper.readValue(jsonResponse, AladinApiResponse.class))
                .thenReturn(mockResponse);

        AladinApiResponse.Item result = aladinCreateClient.searchByIsbn(isbn);

        assertThat(result).isNotNull().isEqualTo(mockItem);
    }

    @Test
    @DisplayName("실패: ISBN이 null인 경우 null 반환")
    void searchByIsbn_NullIsbn() throws JsonProcessingException {
        AladinApiResponse.Item result = aladinCreateClient.searchByIsbn(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("실패: ISBN이 빈 문자열인 경우 null 반환")
    void searchByIsbn_BlankIsbn() throws JsonProcessingException {
        AladinApiResponse.Item result = aladinCreateClient.searchByIsbn("");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("실패: API 응답 Body가 null인 경우 null 반환")
    void searchByIsbn_NullResponseBody() throws JsonProcessingException {
        String isbn = "9781234567890";
        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(null));

        AladinApiResponse.Item result = aladinCreateClient.searchByIsbn(isbn);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("실패: API 응답에 에러 코드(errorCode)가 포함된 경우 예외 발생")
    void searchByIsbn_ApiErrorCode() {
        String isbn = "9781234567890";
        String errorJson = "{\"errorCode\": 8, \"errorMessage\": \"Invalid Key\"}";

        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(errorJson));

        assertThatThrownBy(() -> aladinCreateClient.searchByIsbn(isbn))
                .isInstanceOf(AladinApiException.class)
                .hasMessageContaining("Aladin API Error Response");
    }

    @Test
    @DisplayName("실패: API 응답에 에러 메시지(errorMessage)가 포함된 경우 예외 발생")
    void searchByIsbn_ApiErrorMessage() {
        String isbn = "9781234567890";
        String errorJson = "{\"errorMessage\": \"Something went wrong\"}";

        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(errorJson));

        assertThatThrownBy(() -> aladinCreateClient.searchByIsbn(isbn))
                .isInstanceOf(AladinApiException.class)
                .hasMessageContaining("Aladin API Error Response");
    }

    @Test
    @DisplayName("실패: 검색 결과는 성공했으나 Item 리스트가 비어있는 경우 null 반환")
    void searchByIsbn_EmptyItems() throws JsonProcessingException {
        String isbn = "9781234567890";
        String jsonResponse = "{\"version\":\"...\", \"item\":[]}";

        AladinApiResponse mockResponse = new AladinApiResponse();

        ReflectionTestUtils.setField(mockResponse, "item", Collections.emptyList());

        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonResponse));
        when(objectMapper.readValue(jsonResponse, AladinApiResponse.class))
                .thenReturn(mockResponse);

        AladinApiResponse.Item result = aladinCreateClient.searchByIsbn(isbn);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("실패: 검색 결과는 성공했으나 Item 리스트가 null인 경우 null 반환")
    void searchByIsbn_NullItemsList() throws JsonProcessingException {
        String isbn = "9781234567890";
        String jsonResponse = "{\"version\":\"...\"}";

        AladinApiResponse mockResponse = new AladinApiResponse();

        ReflectionTestUtils.setField(mockResponse, "item", null);

        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonResponse));
        when(objectMapper.readValue(jsonResponse, AladinApiResponse.class))
                .thenReturn(mockResponse);

        AladinApiResponse.Item result = aladinCreateClient.searchByIsbn(isbn);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("실패: RestTemplate 호출 중 예외 발생 시 AladinApiException으로 래핑")
    void searchByIsbn_RestTemplateException() {
        String isbn = "9781234567890";
        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection Refused"));

        assertThatThrownBy(() -> aladinCreateClient.searchByIsbn(isbn))
                .isInstanceOf(AladinApiException.class)
                .hasMessage("Aladin API Fail during processing")
                .hasCauseInstanceOf(RestClientException.class);
    }

    @Test
    @DisplayName("실패: ObjectMapper 파싱 중 예외 발생 시 AladinApiException으로 래핑")
    void searchByIsbn_JsonProcessingException() throws JsonProcessingException {
        String isbn = "9781234567890";
        String invalidJson = "{invalid-json}";

        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(invalidJson));

        when(objectMapper.readValue(invalidJson, AladinApiResponse.class))
                .thenThrow(new JsonProcessingException("Parse Error"){});

        assertThatThrownBy(() -> aladinCreateClient.searchByIsbn(isbn))
                .isInstanceOf(AladinApiException.class)
                .hasMessage("Aladin API Fail during processing");
    }
}