package org.nhnacademy.book2onandonbookservice.dto.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GeminiApiResponseTest {

    @Test
    @DisplayName("정상 추출: 모든 데이터가 존재할 때 텍스트를 반환하고 마크다운을 제거한다")
    void getFirstCandidateText_success() {
        GeminiApiResponse response = new GeminiApiResponse();

        GeminiApiResponse.Part part = new GeminiApiResponse.Part();
        ReflectionTestUtils.setField(part, "text", "```json\nResult\n```");

        GeminiApiResponse.Content content = new GeminiApiResponse.Content();
        ReflectionTestUtils.setField(content, "parts", List.of(part));

        GeminiApiResponse.Candidate candidate = new GeminiApiResponse.Candidate();
        ReflectionTestUtils.setField(candidate, "content", content);

        ReflectionTestUtils.setField(response, "candidates", List.of(candidate));

        String result = response.getFirstCandidateText();

        assertThat(result).isEqualTo("Result");
    }

    @Test
    @DisplayName("Null 반환: candidates 리스트가 null이거나 비어있으면 null 반환")
    void getFirstCandidateText_nullCandidates() {
        GeminiApiResponse response = new GeminiApiResponse();
        ReflectionTestUtils.setField(response, "candidates", null);

        assertThat(response.getFirstCandidateText()).isNull();

        ReflectionTestUtils.setField(response, "candidates", Collections.emptyList());
        assertThat(response.getFirstCandidateText()).isNull();
    }

    @Test
    @DisplayName("예외 처리 검증: 내부 로직 수행 중 예외 발생 시 catch 블록 실행 후 null 반환")
    void getFirstCandidateText_exceptionHandling() {
        GeminiApiResponse response = new GeminiApiResponse();

        List<GeminiApiResponse.Candidate> mockList = mock(List.class);

        when(mockList.isEmpty()).thenReturn(false);

        when(mockList.get(0)).thenThrow(new RuntimeException("의도된 테스트 예외"));

        ReflectionTestUtils.setField(response, "candidates", mockList);

        String result = response.getFirstCandidateText();
        assertThat(result).isNull();
    }
}