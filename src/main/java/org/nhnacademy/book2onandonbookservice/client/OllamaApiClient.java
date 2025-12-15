package org.nhnacademy.book2onandonbookservice.client;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
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
    private String OLLAMA_URL;
    @Value("${ollama.model.name}")
    private String MODEL_NAME;

    /*
    텍스트를 1024차원으로 변환
     */
    public List<Float> getEmbedding(String text){
        if(text==null || text.isBlank()){
            return Collections.emptyList();
        }

        try{
            OllamaRequest request = new OllamaRequest(MODEL_NAME, text);
            OllamaResponse response = restTemplate.postForObject(OLLAMA_URL, request, OllamaResponse.class);

            if(response != null && response.getEmbedding() != null){
                return response.getEmbedding();
            }
        }catch (Exception e){
            log.error("[Ollama] 임베딩 생성 실패: text={}, error={}", text,e.getMessage());
            //TODO: 실패 시 빈 리스트를 반환 할지, 아니면 throw Exception을 할지 결정해야됨
        }
        return Collections.emptyList();
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    static class OllamaRequest {
        private String model;
        private String prompt;
    }

    @Getter
    @NoArgsConstructor
    static class OllamaResponse {
        private List<Float> embedding;
    }
}
