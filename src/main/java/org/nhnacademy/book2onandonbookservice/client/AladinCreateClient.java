package org.nhnacademy.book2onandonbookservice.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class AladinCreateClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${aladin.api.base-url}")
    private String baseUrl;

    @Value("${aladin.api.create-ttb-key}")
    private String ttbKey;

    @Cacheable(value = "aladinCreateBook", key = "#isbn", unless = "#result == null", cacheManager = "RedisCacheManager")
    public AladinApiResponse.Item searchByIsbn(String isbn) throws JsonProcessingException {
        if (isbn == null || isbn.isBlank()) {
            return null;
        }

        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path("/ItemLookUp.aspx") // 알라딘 API의 ItemLookUp.aspx 엔드포인트 사용
                .queryParam("ttbkey", ttbKey)
                .queryParam("ItemId", isbn)
                .queryParam("ItemIdType", "ISBN13")
                .queryParam("output", "js")
                .queryParam("Version", "20131101")
                .queryParam("Cover", "toc")
                .queryParam("OptResult", "description,categoryName,author,publisher,cover")
                .build(true)
                .toUri();

        try {
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(uri,String.class);
            String rawJson = responseEntity.getBody();
            if(rawJson==null){
                return null;
            }
            if (rawJson.contains("errorCode") || rawJson.contains("errorMessage")) {
                log.error("알라딘 API 에러 응답 감지 (ISBN: {}): {}", isbn, rawJson);
                throw new RuntimeException("Aladin API Error Response: " + rawJson);
            }

            AladinApiResponse response = objectMapper.readValue(rawJson, AladinApiResponse.class);
            if (response != null && response.getItem() != null && !response.getItem().isEmpty()) {
                return response.getItem().get(0);
            } else {
                // 진짜 검색 결과가 없는 경우 (items가 비어있음)
                log.warn("알라딘 API: ISBN {}에 대한 검색 결과가 없습니다.", isbn);
                return null;
            }
        }catch (Exception e) { // RuntimeException 포함 모든 예외 잡기
            // 예외를 로그만 찍고 끝내면 안됨! 반드시 던져야 함!
            if (e.getMessage() != null && e.getMessage().contains("Aladin API Error Response")) {
                throw e;
            }
            log.error("알라딘 API 호출 중 치명적 오류 (ISBN: {}): {}", isbn, e.getMessage());
            throw new RuntimeException("Aladin API Fail", e);
        }
    }
}
