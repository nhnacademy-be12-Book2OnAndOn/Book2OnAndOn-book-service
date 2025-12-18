package org.nhnacademy.book2onandonbookservice.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiRequest;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiResponse;
import org.nhnacademy.book2onandonbookservice.exception.GeminiQuotaExceededException;
import org.nhnacademy.book2onandonbookservice.exception.GeminiTagGenerationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiBookCreateClient {

    private static final String MODEL_NAME = "gemini-2.0-flash-lite";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.base-url}")
    private String baseUrl;

    @Value("${gemini.api.create-key}")
    private String apiCreateKey;

    @Cacheable(value = "geminiCreateContent", key = "#isbn", unless = "#result == null")
    public String generateChapter (String isbn, String title, String description){
        String safeDescription = (description != null) ? description : "";
        String prompt = """
                다음 책 정보를 분석하여 JSON으로 답해.
                
                [책 정보]
                ISBN: %s
                제목: %s
                설명: %s
                
                [지시사항]
                [Markdown 코드 블록은 사용하지마]
                1. ISBN과 제목, 설명을 활용하여 해당 도서에 맞는 목차 반환 (목차 정보가 없으면 5~10줄 생성)
                2. 제공된 ISBN과 제목을 바탕으로 네가 가진 지식을 활용해 내용을 추론하여 반드시 생성. 절대 빈 값을 반환하지 마라.
                
                형식:
                {
                    "chapter": "..."
                }
                """.formatted(isbn, title, safeDescription);

        try {
            String rawText = callGeminiApi(prompt);
            return parseContentFromJson(rawText);
        } catch (GeminiQuotaExceededException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini 목차 생성 최종 실패 (ISBN: {}): {}", isbn, e.getMessage());
            return "";
        }
    }

    private String callGeminiApi(String prompt){
        String url = String.format("%s/%s:generateContent?key=%s", baseUrl, MODEL_NAME, apiCreateKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        GeminiApiRequest rBody = new GeminiApiRequest(prompt);
        HttpEntity<GeminiApiRequest> entity = new HttpEntity<>(rBody, headers);

        try {
            GeminiApiResponse response = restTemplate.postForObject(url, entity, GeminiApiResponse.class);
            if (response != null) {
                return response.getFirstCandidateText();
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                log.warn("Gemini API Quota/Rate Limit 발생: {}", e.getMessage());
                throw new GeminiQuotaExceededException("Gemini API Rate Limit Exceeded", e);
            }
            log.error("Gemini API HTTP 오류: {}", e.getMessage());
            throw new GeminiTagGenerationException("Gemini API HTTP Error", e);
        } catch (Exception e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage());
            throw new GeminiTagGenerationException("Gemini API Call Failed", e);
        }

        return null;
    }
    //방어로직
    private String parseContentFromJson(String rawText){
        if(rawText == null){
            return "";
        }

        String jsonText = rawText.replace("```json", "").replace("```", "").trim();

        try{
            JsonNode rootNode = objectMapper.readTree(jsonText);

            if (rootNode.has("chapter")) {
                return rootNode.get("chapter").asText();
            }

            return jsonText;
        } catch (JsonProcessingException e) {
            log.warn("Gemini 콘텐츠 응답 파싱 실패: {}", rawText);
            return jsonText;
        }
    }

}
