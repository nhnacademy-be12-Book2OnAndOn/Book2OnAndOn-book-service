package org.nhnacademy.book2onandonbookservice.client;


import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.api.GoogleBooksApiResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleBooksApiClient {
    private final RestTemplate restTemplate;

    @Value("${google.api.key}")
    private String apikey;

    private static final String API_URL = "https://www.googleapis.com/books/v1/volumes";


    public GoogleBooksApiResponse.VolumeInfo searchByIsbn(String isbn) {
        if (isbn == null || isbn.isEmpty()) {
            return null;
        }

        URI uri = UriComponentsBuilder.fromUriString(API_URL)
                .queryParam("q", "isbn:" + isbn)
                .queryParam("key", apikey)
                .build(true)
                .toUri();

        try {
            ResponseEntity<GoogleBooksApiResponse> response = restTemplate.exchange(uri, HttpMethod.GET,
                    null, GoogleBooksApiResponse.class);
            if (response.getBody() != null && response.getBody().getItems() != null) {
                return response.getBody().getItems().get(0).getVolumeInfo();
            }
        } catch (Exception e) {
            log.error("Google Books API 호출 중 오류 발생 (ISBN: {}): {}", isbn, e.getMessage());
        }

        return null;
    }
}
