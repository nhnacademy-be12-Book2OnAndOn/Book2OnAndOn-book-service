package org.nhnacademy.book2onandonbookservice.client;

import java.util.ArrayList;
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
public class RerankerApiClient {
    private final RestTemplate restTemplate;
    @Value("${reranker.url}")
    private String rerankUrl;


    /**
     * 후보군 재순위화 (Re-ranking)
     * @param query 사용자 검색어
     * @param texts 후보 도서들의 텍스트 리스트 (제목 + 설명 등)
     * @return 재정렬된 인덱스와 점수 리스트
     */
    public List<RerankResult> rerank(String query, List<String> texts){
        if(query==null || texts==null || texts.isEmpty()){
            return Collections.emptyList();
        }
        try{
            RerankRequest rerankRequest = new RerankRequest(query, texts);
            RerankResult[] response = restTemplate.postForObject(rerankUrl, rerankRequest, RerankResult[].class);

            if(response != null){
                return new ArrayList<>(List.of(response));
            }
        }catch (Exception e){
            log.error("[Reranker] 리랭킹 요청 실패: query={}, error={}", query, e.getMessage());
        }

        return Collections.emptyList();
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    static class RerankRequest {
        private String query;
        private List<String> texts;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RerankResult {
        private int index;   // 원본 리스트에서의 인덱스
        private double score; // 관련도 점수
    }

}


