package org.nhnacademy.book2onandonbookservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroqApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.base-url}")
    private String baseUrl;

    @Value("${groq.api.api-key}")
    private String apiKey;

    private static final String MODEL_NAME = "llama-3.1-8b-instant";
    private static final String STANDARD_TAGS = """
            출근길, 퇴근길, 잠들기전, 주말순삭, 새벽감성, 여행갈때, 카페에서, 비오는날, 겨울감성, 여름휴가,
            위로, 힐링, 스트레스해소, 기분전환, 눈물버튼, 동기부여, 자존감, 용기, 행복, 설렘, 자기계발, 습관형성,
            갓생살기, 인사이트, 지식확장, 실무역량, 일잘러, 부자되는법, 트렌드, 인간관계, 몰입감, 순삭, 흥미진진, 잔잔한,
            깊은여운, 가볍게읽기좋은, 생각할거리, 선물하기좋은, 방송에나온, 만화, 웹툰
            """;

    public BookContentDto extractContent(String title, String description, String isbn) {
        String safeDescription = (description != null) ? description : "";

        String prompt = """
                다음 책 정보를 분석하여 JSON으로 답해.
                
                [표준 태그 목록]
                %s
                
                [책 정보]
                ISBN: %s
                제목: %s
                설명: %s
                
                [지시사항]
                1. tags: 총 3개의 태그를 배열로 반환.
                    - 2개는 반드시 위 [표준 태그 목록]에서 가장 적절한 것을 골라라.
                    - 나머지 1개는 책의 특성을 잘 나타내는 구체적인 키워드를 생성해라.
                2. chapter: 책의 목차 (없으면 5~10줄 생성)
                3. [중요] 만약 '설명'란이 비어있다면, 제공된 ISBN과 제목을 바탕으로 네가 가진 지식을 활용해 내용을 추론하여 태그와 목차를 반드시 생성해라.
                4. [매우 중요] 결과는 반드시 한국어로 작성해야 하며, 오직 JSON 형식만 반환해라. (마크다운, 설명 금지)
                
                형식:
                {
                    "tags": ["표준태그1", "표준태그2", "생성된태그"],
                    "chapter": "..."
                }
                """.formatted(STANDARD_TAGS, isbn, title, safeDescription);

        try {
            return callGroqApi(prompt);
        } catch (Exception e) {
            log.warn("Groq API 호출 실패 (ISBN: {}): {}", isbn, e.getMessage());
            throw new RuntimeException("Groq Fail", e); // 서비스로 예외를 던져서 Gemini가 받게 함
        }
    }

    private BookContentDto callGroqApi(String prompt) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        // OpenAI 호환 포맷
        Map<String, Object> requestBody = Map.of(
                "model", MODEL_NAME,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "response_format", Map.of("type", "json_object") // JSON 강제 모드
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        GroqChatResponse response = restTemplate.postForObject(baseUrl, entity, GroqChatResponse.class);

        if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
            String content = response.getChoices().get(0).getMessage().getContent();
            return objectMapper.readValue(content, BookContentDto.class);
        }

        throw new RuntimeException("Groq response was empty");
    }

    // --- Response DTO (내부 클래스) ---
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true) // 모르는 필드는 무시 (안전장치)
    public static class GroqChatResponse {
        private List<Choice> choices;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Choice {
            private Message message;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Message {
            private String content;
        }
    }
}