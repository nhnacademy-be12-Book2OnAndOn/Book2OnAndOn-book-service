package org.nhnacademy.book2onandonbookservice.client;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaApiClient {
    private final RestTemplate restTemplate;
    @Value("${ollama.embaddings.url}")
    private String ollamaUrl;
    @Value("${ollama.model.name}")
    private String modelName;

    /*
    텍스트를 1024차원으로 변환
     */
    public List<Float> getEmbedding(String text){
        if(text==null || text.isBlank()){
            return Collections.emptyList();
        }

        try{
            OllamaRequest request = new OllamaRequest(modelName, text);
            OllamaResponse response = restTemplate.postForObject(ollamaUrl, request, OllamaResponse.class);

            if(response != null && response.getEmbedding() != null){
                return response.getEmbedding();
            }
        }catch (Exception e){
            log.error("[Ollama] 임베딩 생성 실패: text={}, error={}", text,e.getMessage());
            //예외를 삼킴 -> 임베딩 서버가 잠깐 꺼져도 서비스 전체가 멈추지 않도록(Graceful Degradation)
        }
        return Collections.emptyList();
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    static class OllamaRequest {
        private String model;
        private String prompt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class OllamaResponse {
        private List<Float> embedding;
    }
}
