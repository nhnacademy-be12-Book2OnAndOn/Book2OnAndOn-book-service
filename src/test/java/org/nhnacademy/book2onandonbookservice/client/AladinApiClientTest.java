package org.nhnacademy.book2onandonbookservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class AladinApiClientTest {
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AladinApiClient aladinApiClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aladinApiClient, "baseUrl", "http://www.aladin.co.kr/ttb/api");
        ReflectionTestUtils.setField(aladinApiClient, "ttbKey", "test-ttb-key");
    }

    @Test
    @DisplayName("ISBN 검색 성공: 결과가 있을 때 정상적으로 Item을 반환한다.")
    void searchByIsbn() {
        String isbn = "1234567890123";
        String title = "테스트 책 제목";
        String author = "테스트 작가";
        Long price = 15000L;

        AladinApiResponse.Item mockItem = new Item();

        ReflectionTestUtils.setField(mockItem, "title", title);
        ReflectionTestUtils.setField(mockItem, "author", author);
        ReflectionTestUtils.setField(mockItem, "priceStandard", price);

        AladinApiResponse mockResponse = new AladinApiResponse();
        ReflectionTestUtils.setField(mockResponse, "item", List.of(mockItem));

        when(restTemplate.getForObject(any(URI.class), eq(AladinApiResponse.class))).thenReturn(mockResponse);

        AladinApiResponse.Item result = aladinApiClient.searchByIsbn(isbn);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo(title);
        assertThat(result.getAuthor()).isEqualTo(author);
        assertThat(result.getPriceStandard()).isEqualTo(price);

        verify(restTemplate, times(1)).getForObject(any(URI.class), eq(AladinApiResponse.class));
    }

    @Test
    @DisplayName("ISBN 검색 결과 X: API는 호출 성공했지만 응답이 비어있을때")
    void searchByIsbn_emptyList() {
        String isbn = "1234567890123";
        AladinApiResponse mockResponse = new AladinApiResponse();

        ReflectionTestUtils.setField(mockResponse, "item", Collections.emptyList());

        when(restTemplate.getForObject(any(URI.class), eq(AladinApiResponse.class))).thenReturn(mockResponse);

        AladinApiResponse.Item result = aladinApiClient.searchByIsbn(isbn);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("API 호출 예외 발생")
    void searchByIsbn_apiError() {
        String isbn = "1234567890123";
        when(restTemplate.getForObject(any(URI.class), eq(AladinApiResponse.class))).thenThrow(
                new RestClientException("Connection Error"));
        AladinApiResponse.Item result = aladinApiClient.searchByIsbn(isbn);
        assertThat(result).isNull();

    }

    @Test
    @DisplayName("입력값 검증: ISBN이 null이거나 공백이면 API 호출 없이 null 반환")
    void searchByIsbn_invalidInput() {
        String emptyIsbn = "";

        AladinApiResponse.Item result = aladinApiClient.searchByIsbn(emptyIsbn);
        assertThat(result).isNull();
        verify(restTemplate, never()).getForObject(any(URI.class), eq(AladinApiResponse.class));
    }
}