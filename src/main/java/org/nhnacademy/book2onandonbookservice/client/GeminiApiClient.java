package org.nhnacademy.book2onandonbookservice.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiRequest;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiApiClient {

    private static final String MODEL_NAME = "gemini-2.0-flash-lite";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.base-url}")
    private String baseUrl;
    @Value("${gemini.api-key}")
    private String rawApikey;

    private String[] apiKeys;
    private final AtomicInteger keyIndex = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        if (rawApikey != null) {
            this.apiKeys = rawApikey.replace(" ", "").split(",");
            log.info("Gemini API 키 {}개  로드 함", apiKeys.length);
        }
    }

    private String getNextKey() {
        if (apiKeys == null || apiKeys.length == 0) {
            return "";
        }

        int index = keyIndex.getAndIncrement() % apiKeys.length;

        if (index < 0) {
            index = Math.abs(index);
        }
        return apiKeys[index];
    }

    @Cacheable(value = "geminiTags", key = "#title", unless = "#result == null || #result.isEmpty()", cacheManager = "RedisCacheManager")
    public List<String> extractTags(String title, String description) {
        if (description == null || description.isEmpty()) {
            return Collections.emptyList();
        }

        String prompt = String.format(
                "다음 책의 제목과 설명을 확인하고 핵심 태그(키워드) 3개를 추출해줘 또한 " +
                        "무조건 JSON 문자열 배열 형식([\"태그1\", \"태그2\",...)으로만 대답해."
                        + "부가적인 말은 생략하고 형식에 맞춰서만 대답해 \n\n"
                        + "제목: %s\n설명: %s", title, description
        );

        String currentKey = getNextKey();
        String url = String.format("%s/%s:generateContent?key=%s", baseUrl, MODEL_NAME, currentKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        GeminiApiRequest rBody = new GeminiApiRequest(prompt);
        HttpEntity<GeminiApiRequest> entity = new HttpEntity<>(rBody, headers);

        try {
            GeminiApiResponse response = restTemplate.postForObject(url, entity, GeminiApiResponse.class);
            if (response != null) {
                String rawText = response.getFirstCandidateText();
                return parseTagsFromJson(rawText);
            }
        } catch (Exception e) {
            log.error("Gemini API 호출 실패 (Key: {}): {}", currentKey.substring(currentKey.length() - 4), e.getMessage());
        }

        return Collections.emptyList();
    }

    private List<String> parseTagsFromJson(String rawText) {
        if (rawText == null) {
            return Collections.emptyList();
        }

        String jsonText = rawText.replaceAll("```json", "").replaceAll("```", "")
                .trim();

        try {
            return objectMapper.readValue(jsonText, List.class);
        } catch (JsonProcessingException e) {
            log.warn("Gemini 응답 파싱 실패: {}", rawText);
            return Collections.emptyList();
        }
    }
}
