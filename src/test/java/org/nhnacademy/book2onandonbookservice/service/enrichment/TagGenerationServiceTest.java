package org.nhnacademy.book2onandonbookservice.service.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.GeminiApiClient;
import org.nhnacademy.book2onandonbookservice.client.GroqApiClient;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.nhnacademy.book2onandonbookservice.exception.GeminiQuotaExceededException;
import org.nhnacademy.book2onandonbookservice.exception.GeminiTagGenerationException;
import org.nhnacademy.book2onandonbookservice.exception.GroqQuotaExceededException;
import org.nhnacademy.book2onandonbookservice.exception.TagGenerationFailedException;
import org.nhnacademy.book2onandonbookservice.service.enrichment.rate.ApiRateLimiter;

@ExtendWith(MockitoExtension.class)
class TagGenerationServiceTest {

    @InjectMocks
    private TagGenerationService tagGenerationService;

    @Mock
    private GroqApiClient groqApiClient;

    @Mock
    private GeminiApiClient geminiApiClient;

    @Mock
    private ApiRateLimiter rateLimiter;

    @Test
    @DisplayName("generateContent: Groq 성공")
    void generateContent_GroqSuccess() {
        String title = "Title";
        String description = "Desc";
        String isbn = "123";

        BookContentDto content = mock(BookContentDto.class);
        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(title, description, isbn)).thenReturn(content);
        
        when(content.hasNoTags()).thenReturn(false);
        when(content.hasNoChapter()).thenReturn(false);

        BookContentDto result = tagGenerationService.generateContent(title, description, isbn);

        assertThat(result).isEqualTo(content);
        verify(geminiApiClient, never()).extractContent(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("generateContent: Groq 할당량 초과 예외")
    void generateContent_GroqQuotaExceeded() {
        when(rateLimiter.tryAcquireGroq()).thenReturn(false);

        assertThatThrownBy(() -> tagGenerationService.generateContent("t", "d", "i"))
                .isInstanceOf(GroqQuotaExceededException.class);
    }

    @Test
    @DisplayName("generateContent: Groq 실패 -> Gemini 성공 (Fallback 동작)")
    void generateContent_GroqFail_GeminiSuccess() {
        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Groq Fail"));

        when(rateLimiter.tryAcquireGemini()).thenReturn(true);
        BookContentDto content = mock(BookContentDto.class);
        when(geminiApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(content);
        
        when(content.hasNoTags()).thenReturn(false);
        when(content.hasNoChapter()).thenReturn(false);

        BookContentDto result = tagGenerationService.generateContent("t", "d", "i");

        assertThat(result).isEqualTo(content);
        verify(geminiApiClient).extractContent(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("generateContent: Groq 실패 후 Gemini 할당량 초과")
    void generateContent_GeminiQuotaExceeded() {
        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Groq Fail"));

        when(rateLimiter.tryAcquireGemini()).thenReturn(false);

        assertThatThrownBy(() -> tagGenerationService.generateContent("t", "d", "i"))
                .isInstanceOf(GeminiQuotaExceededException.class);
    }

    @Test
    @DisplayName("generateContent: Groq, Gemini 모두 실패")
    void generateContent_AllFail() {
        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Groq Fail"));

        when(rateLimiter.tryAcquireGemini()).thenReturn(true);
        when(geminiApiClient.extractContent(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Gemini Fail"));

        assertThatThrownBy(() -> tagGenerationService.generateContent("t", "d", "i"))
                .isInstanceOf(GeminiTagGenerationException.class);
    }

    @Test
    @DisplayName("generateContent: AI 응답이 null인 경우")
    void generateContent_ContentNull() {
        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(null);

        assertThatThrownBy(() -> tagGenerationService.generateContent("t", "d", "i"))
                .isInstanceOf(TagGenerationFailedException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("generateContent: 태그가 없는 경우")
    void generateContent_NoTags() {
        BookContentDto content = mock(BookContentDto.class);
        when(content.hasNoTags()).thenReturn(true);

        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(content);
        
        assertThatThrownBy(() -> tagGenerationService.generateContent("t", "d", "i"))
                .isInstanceOf(TagGenerationFailedException.class)
                .hasMessageContaining("태그");
    }

    @Test
    @DisplayName("generateContent: 챕터가 없는 경우")
    void generateContent_NoChapter() {
        BookContentDto content = mock(BookContentDto.class);
        when(content.hasNoTags()).thenReturn(false);
        when(content.hasNoChapter()).thenReturn(true);

        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(content);
        
        assertThatThrownBy(() -> tagGenerationService.generateContent("t", "d", "i"))
                .isInstanceOf(TagGenerationFailedException.class)
                .hasMessageContaining("챕터");
    }
}
