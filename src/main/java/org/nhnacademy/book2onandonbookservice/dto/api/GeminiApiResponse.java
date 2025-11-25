package org.nhnacademy.book2onandonbookservice.dto.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
@Slf4j
public class GeminiApiResponse {

    private List<Candidate> candidates;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @NoArgsConstructor
    public static class Candidate {
        private Content content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @NoArgsConstructor
    public static class Content {
        private List<Part> parts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @NoArgsConstructor
    public static class Part {
        private String text;
    }

    public String getFirstCandidateText() {
        try {
            if (candidates != null && !candidates.isEmpty() &&
                    candidates.get(0).getContent() != null &&
                    candidates.get(0).getContent().getParts() != null &&
                    !candidates.get(0).getContent().getParts().isEmpty()) {
                String rawText = candidates.get(0).getContent().getParts().get(0).getText();
                return rawText.replace("```json", "").replace("```", "").trim();
            }
        } catch (Exception e) {
            log.error("Gemini 응답 텍스트 추출 실패", e);
        }
        return null;
    }
}
