package org.nhnacademy.book2onandonbookservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class RerankerApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private RerankerApiClient rerankerApiClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rerankerApiClient, "rerankUrl", "http://test-url.com");
    }

    @Test
    void rerank_success() {
        String query = "test query";
        List<String> texts = List.of("text1", "text2");
        RerankerApiClient.RerankResult[] mockResponse = {
                new RerankerApiClient.RerankResult(0, 0.9),
                new RerankerApiClient.RerankResult(1, 0.8)
        };

        given(restTemplate.postForObject(any(String.class), any(RerankerApiClient.RerankRequest.class), eq(RerankerApiClient.RerankResult[].class)))
                .willReturn(mockResponse);

        List<RerankerApiClient.RerankResult> result = rerankerApiClient.rerank(query, texts);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getIndex()).isZero();
        assertThat(result.get(0).getScore()).isEqualTo(0.9);
    }

    @Test
    void rerank_fail_null_query() {
        List<RerankerApiClient.RerankResult> result = rerankerApiClient.rerank(null, List.of("text"));

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void rerank_fail_null_texts() {
        List<RerankerApiClient.RerankResult> result = rerankerApiClient.rerank("query", null);

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void rerank_fail_empty_texts() {
        List<RerankerApiClient.RerankResult> result = rerankerApiClient.rerank("query", Collections.emptyList());

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void rerank_fail_rest_client_exception() {
        String query = "query";
        List<String> texts = List.of("text");

        given(restTemplate.postForObject(any(String.class), any(RerankerApiClient.RerankRequest.class), eq(RerankerApiClient.RerankResult[].class)))
                .willThrow(new RestClientException("Error"));

        List<RerankerApiClient.RerankResult> result = rerankerApiClient.rerank(query, texts);

        assertThat(result).isEmpty();
    }

    @Test
    void rerank_fail_null_response() {
        String query = "query";
        List<String> texts = List.of("text");

        given(restTemplate.postForObject(any(String.class), any(RerankerApiClient.RerankRequest.class), eq(RerankerApiClient.RerankResult[].class)))
                .willReturn(null);

        List<RerankerApiClient.RerankResult> result = rerankerApiClient.rerank(query, texts);

        assertThat(result).isEmpty();
    }

    @Test
    void rerankRequest_dto_test() {
        String query = "q";
        List<String> texts = List.of("t");
        RerankerApiClient.RerankRequest request = new RerankerApiClient.RerankRequest(query, texts);

        assertThat(request.getQuery()).isEqualTo(query);
        assertThat(request.getTexts()).isEqualTo(texts);
    }

    @Test
    void rerankResult_dto_test() {
        RerankerApiClient.RerankResult resultNoArgs = new RerankerApiClient.RerankResult();
        assertThat(resultNoArgs).isNotNull();

        RerankerApiClient.RerankResult resultAllArgs = new RerankerApiClient.RerankResult(1, 0.5);
        assertThat(resultAllArgs.getIndex()).isEqualTo(1);
        assertThat(resultAllArgs.getScore()).isEqualTo(0.5);
    }
}