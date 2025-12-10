package org.nhnacademy.book2onandonbookservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse.Item;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class AladinApiClientTest {
    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AladinApiClient aladinApiClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aladinApiClient, "baseUrl", "http://www.aladin.co.kr/ttb/api");
        ReflectionTestUtils.setField(aladinApiClient, "ttbKey", "test-ttb-key");
    }

    @Test
    @DisplayName("ISBN 검색 성공")
    void searchByIsbn() throws JsonProcessingException {
        String isbn = "1234567890123";
        String mockJson = "{\"item\": [{\"title\": \"테스트 책\"}]}";

        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockJson, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(responseEntity);

        AladinApiResponse mockResponse = new AladinApiResponse();
        Item mockItem = new Item();
        ReflectionTestUtils.setField(mockItem, "title", "테스트 책");
        ReflectionTestUtils.setField(mockResponse, "item", List.of(mockItem));

        when(objectMapper.readValue(mockJson, AladinApiResponse.class)).thenReturn(mockResponse);

        Item result = aladinApiClient.searchByIsbn(isbn);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("테스트 책");

        verify(restTemplate, times(1)).getForEntity(any(URI.class), eq(String.class));
    }

    @Test
    @DisplayName("ISBN 검색 결과 없음")
    void searchByIsbn_emptyList() throws JsonProcessingException {
        String isbn = "1234567890123";
        String mockJson = "{\"item\": []}";

        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockJson, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(responseEntity);

        AladinApiResponse mockResponse = new AladinApiResponse();
        ReflectionTestUtils.setField(mockResponse, "item", Collections.emptyList());

        when(objectMapper.readValue(mockJson, AladinApiResponse.class)).thenReturn(mockResponse);

        Item result = aladinApiClient.searchByIsbn(isbn);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("API 호출 예외 발생")
    void searchByIsbn_apiError() {
        String isbn = "1234567890123";

        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection Error"));

        assertThatThrownBy(() -> aladinApiClient.searchByIsbn(isbn))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Aladin API Fail");
    }

    @Test
    @DisplayName("입력값 검증 실패")
    void searchByIsbn_invalidInput() throws JsonProcessingException {
        String emptyIsbn = "";

        Item result = aladinApiClient.searchByIsbn(emptyIsbn);

        assertThat(result).isNull();
        verify(restTemplate, never()).getForEntity(any(URI.class), eq(String.class));
    }
}