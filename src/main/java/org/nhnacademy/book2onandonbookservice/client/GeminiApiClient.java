package org.nhnacademy.book2onandonbookservice.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiRequest;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiApiResponse;
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
    private static final String STANDARD_TAGS = """
            출근길, 퇴근길, 잠들기전, 주말순삭, 새벽감성, 여행갈때, 카페에서, 비오는날, 겨울감성, 여름휴가,
            위로, 힐링, 스트레스해소, 기분전환, 눈물버튼, 동기부여, 자존감, 용기, 행복, 설렘, 자기계발, 습관형성,
            갓생살기, 인사이트, 지식확장, 실무역량, 일잘러, 부자되는법, 트렌드, 인간관계, 몰입감, 순삭, 흥미진진, 잔잔한,
            깊은여운, 가볍게읽기좋은, 생각할거리, 선물하기좋은, 방송에나온, 만화, 웹툰
            """;

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


    @Cacheable(value = "geminiContent", key = "#isbn", unless = "#result == null", cacheManager = "RedisCacheManager")
    public BookContentDto extractContent(String title, String description, String isbn){
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
                    - 나머지 1개는 책의, 특성을 잘 나타내는 구체적인 키워드를 생성해라.
                2. chapter: 책의 목차 (없으면 5~10줄 생성)
                3. [중요] 만약 '설명'란이 비어있다면, 제공된 ISBN과 제목을 바탕으로 네가 가진 지식을 활용해 내용을 추론하여 태그와 목차를 반드시 생성해라. 절대 빈 값을 반환하지 마라.
                4. 무조건 한국어로 반환할 것
                
                형식:
                {
                    "tags": ["표준태그1", "표준태그2", "생성된태그"],
                    "chapter": "..."
                }
          
                """.formatted(STANDARD_TAGS, isbn ,title, description);

        String rawText = callGeminiApi(prompt);
        return parseContentFromJson(rawText);
    }

    private String callGeminiApi(String prompt){
        String currentKey = getNextKey();
        String url = String.format("%s/%s:generateContent?key=%s", baseUrl,MODEL_NAME,currentKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        GeminiApiRequest rBody = new GeminiApiRequest(prompt);
        HttpEntity<GeminiApiRequest> entity = new HttpEntity<>(rBody, headers);

        try{
            GeminiApiResponse response =  restTemplate.postForObject(url, entity, GeminiApiResponse.class);
            if(response != null){
                return response.getFirstCandidateText();
            }
        }catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                log.warn("Gemini API Quota/Rate Limit 발생 (Key: {}): {}",
                        currentKey.length()>=4 ? currentKey.substring(currentKey.length()-4): "****", e.getMessage());

                throw new RuntimeException("Gemini API Rate Limit Exceeded", e);
            }
            log.error("Gemini API HTTP 오류 (Key: {}): {}", currentKey, e.getMessage());
        }catch (Exception e){
            log.error("Gemini API 호출 실패 (Key: {}): {}",
                    currentKey.length()>=4 ? currentKey.substring(currentKey.length()-4): "****", e.getMessage());

        }

        return null;
    }

    private BookContentDto parseContentFromJson(String rawText){
        if(rawText == null){
            return BookContentDto.empty();
        }

        String jsonText = rawText.replace("```json", "").replace("```", "").trim();

        try{
            return objectMapper.readValue(jsonText, BookContentDto.class);
        } catch (JsonProcessingException e) {
            log.warn("Gemini 콘텐츠 응답 파싱 실패: {}", rawText);
            return BookContentDto.empty();
        }
    }
}
