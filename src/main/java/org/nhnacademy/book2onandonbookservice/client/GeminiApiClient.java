package org.nhnacademy.book2onandonbookservice.client;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiRequest;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiApiClient {

    private final RestTemplate restTemplate;

    @Value("${gemini.api.base-url}")
    private String baseUrl;

    @Value("${gemini.api-key}")
    private String apikey;

    private static final String MODEL_NAME = "gemini-2.5-flash";

    public String generateContent(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }

        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl)
                .pathSegment(MODEL_NAME + ":generateContent")
                .queryParam("key", apikey)
                .build()
                .toUri();

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);

        GeminiApiRequest requestBody = new GeminiApiRequest(prompt);
        HttpEntity<GeminiApiRequest> requestEntity = new HttpEntity<>(requestBody, httpHeaders);

        try {
            ResponseEntity<GeminiApiResponse> response = restTemplate.postForEntity(uri, requestEntity,
                    GeminiApiResponse.class);

            if (response.getBody() != null) {
                return response.getBody().getFirstCandidateText();
            } else {
                log.warn("Gemini API 응답이 null 입니다. (Prompt:{})", prompt.substring(0, 50));
                return null;
            }
        } catch (RestClientException e) {
            log.error("Gemini API 호출 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }
}
