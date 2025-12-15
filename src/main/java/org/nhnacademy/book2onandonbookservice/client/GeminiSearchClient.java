package org.nhnacademy.book2onandonbookservice.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiRequest;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiResponse;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.graphql.GraphQlProperties.Http;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiSearchClient {
    private static final String MODEL_NAME = "gemini-2.0-flash-lite";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.base-url}")
    private String baseUrl;

    @Value("${gemini.api.search-key}")
    private String apiSearchKey;

    @Cacheable(value = "ai-search-recommendations", key = "#userQuery",
            unless = "#result == null || #result.isEmpty()", cacheManager = "RedisCacheManager")
    public List<AiRecommendation> selectBestBooks(String userQuery, List<BookSearchDocument> candidate){
        if(candidate.isEmpty()) return Collections.emptyList();

        String booksInfo = candidate.stream()
                .map(doc-> String.format("ID:%d|제목:%s|ISBN:%s|출판일:%s|설명:%s",
                        doc.getId(),
                        doc.getTitle(),
                        doc.getIsbn(),
                        doc.getPublishDate(),
                        truncate(doc.getDescription(), 100)))
                .collect(Collectors.joining("\n"));
        String prompt = """
                    사용자 질문: "%s"
               
                    아래 제공된 도서 후보(최대 15권) 중에서 사용자의 질문 의도에 가장 부합하는 책을 최소 5권에서 최대 10권 선별해줘
                    
                    [수행 지침]
                    1. 질문과 관련성이 낮은 책은 제외할 것.
                    2. 비슷한 책이 너무 많다면, 그중 가장 평가가 좋거나 가장 최근에 출판된 책, 사용자가 접근하기 쉬운 책을 선정할 것.
                    3. 각 책에 대해 사용자에게 추천하는 이유를 1문장(한국어, 존댓말, 친절하게)으로 작성할 것.
                    
                    [도서 후보 목록]
                    %s
                    
                    [응답 형식]
                    반드시 아래 JSON 배열 형식만 반환해. (마크다운 코드블럭 없이 순수 JSON만)
                    [
                        { "id": ..., "reason": "..."},
                        { "id": ..., "reason": "..."},
                    ]
                """.formatted(userQuery, booksInfo);

        String responseText = callApi(prompt);

        return parseResponse(responseText);
    }

    private String callApi(String prompt){
        String url = String.format("%s/%s:generateContent?key=%s", baseUrl, MODEL_NAME, apiSearchKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        GeminiApiRequest body  = new GeminiApiRequest(prompt);
        HttpEntity<GeminiApiRequest> entity = new HttpEntity<>(body, headers);

        try{
            GeminiApiResponse response = restTemplate.postForObject(url, entity, GeminiApiResponse.class);
            if(response != null) return response.getFirstCandidateText();
        }catch (Exception e){
            log.error("[Gemini Search] API 호출 실패: {}", e.getMessage());
        }

        return null;
    }

    private List<AiRecommendation> parseResponse(String text) {
        if (text == null) return Collections.emptyList();

        try {
            // 1. 마크다운 제거
            String cleanJson = text.replaceAll("```json", "").replaceAll("```", "").trim();

            // 2. 대괄호 '[' 부터 ']' 까지만 추출 (앞뒤 잡설 제거)
            int startIndex = cleanJson.indexOf("[");
            int endIndex = cleanJson.lastIndexOf("]");

            if (startIndex != -1 && endIndex != -1) {
                cleanJson = cleanJson.substring(startIndex, endIndex + 1);
            } else {
                // 대괄호가 없다면 실패 처리
                log.warn("[Gemini Search] 유효한 JSON 배열을 찾을 수 없음: {}", text);
                return Collections.emptyList();
            }

            return objectMapper.readValue(cleanJson, new TypeReference<List<AiRecommendation>>() {});
        } catch (Exception e) {
            log.error("[Gemini Search] 파싱 실패. Raw Text: {}", text, e);
            return Collections.emptyList();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiRecommendation{
        private Long id;
        private String reason;
    }

    private String truncate(String txt, int len){
        if(txt==null) return "";
        String cleanText = txt.trim().replaceAll("\\s+", " ");

        return cleanText.length() > len ? cleanText.substring(0, len) + "..." : cleanText;
    }
}
