package org.nhnacademy.book2onandonbookservice.client;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class AladinApiClient {

    private final RestTemplate restTemplate;

    @Value("${aladin.api.base-url}")
    private String baseUrl;

    @Value("${aladin.api-ttb-key}")
    private String ttbKey;


    @Cacheable(value = "aladinBook", key = "#isbn", unless = "#result == null")
    public AladinApiResponse.Item searchByIsbn(String isbn) {
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
            AladinApiResponse response = restTemplate.getForObject(uri, AladinApiResponse.class);
            if (response != null && response.getItem() != null && !response.getItem().isEmpty()) {
                return response.getItem().get(0);
            } else {
                log.warn("알라딘 API: ISBN {}에 대한 검색 결과가 없습니다. ", isbn);
                return null;
            }
        } catch (RestClientException e) {
            log.error("알라딘 API 호출 중 오류 발생 (ISBN: {}): {}", isbn, e.getMessage());
            return null;
        }
    }
}
