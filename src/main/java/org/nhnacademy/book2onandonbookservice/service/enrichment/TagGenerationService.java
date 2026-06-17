package org.nhnacademy.book2onandonbookservice.service.enrichment;

import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.client.GeminiApiClient;
import org.nhnacademy.book2onandonbookservice.client.GroqApiClient;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.nhnacademy.book2onandonbookservice.exception.GeminiQuotaExceededException;
import org.nhnacademy.book2onandonbookservice.exception.GeminiTagGenerationException;
import org.nhnacademy.book2onandonbookservice.exception.GroqQuotaExceededException;
import org.nhnacademy.book2onandonbookservice.exception.TagGenerationFailedException;
import org.nhnacademy.book2onandonbookservice.service.enrichment.rate.ApiRateLimiter;
import org.springframework.stereotype.Service;

/**
 * 외부 API(groq, gemini)를 호출하여 태그와 챕터를 생성하는 로직을 전담하는 클래스
 * 트랜잭션이 필요없는 외부 I/O 작업 수행
 */
@Service
@RequiredArgsConstructor
public class TagGenerationService {

    private final GroqApiClient groqApiClient;
    private final GeminiApiClient geminiApiClient;
    private final ApiRateLimiter rateLimiter;

    public BookContentDto generateContent(String title, String description, String isbn) {
        BookContentDto content;

        if (!rateLimiter.tryAcquireGroq()) {
            throw new GroqQuotaExceededException("Groq quota exceeded (429/limit)");
        }

        try {
            content = groqApiClient.extractContent(title, description, isbn);
        } catch (Exception groqEx) {
            if (!rateLimiter.tryAcquireGemini()) {
                throw new GeminiQuotaExceededException("Gemini quota exceeded (429/limit)");
            }
            try {
                content = geminiApiClient.extractContent(title, description, isbn);
            } catch (Exception geminiEx) {
                throw new GeminiTagGenerationException("태그/챕터 생성실패 (Groq->Gemini)", geminiEx);
            }
        }

        if (content == null) throw new TagGenerationFailedException("AI 응답이 null");
        if (content.hasNoTags()) throw new TagGenerationFailedException("태그 생성 실패 (빈 결과)");
        if (content.hasNoChapter()) throw new TagGenerationFailedException("챕터 생성 실패 (빈 결과)");

        return content;
    }
}
