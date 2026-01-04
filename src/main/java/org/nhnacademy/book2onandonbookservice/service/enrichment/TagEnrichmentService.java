package org.nhnacademy.book2onandonbookservice.service.enrichment;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.client.GeminiApiClient;
import org.nhnacademy.book2onandonbookservice.client.GroqApiClient;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.BookTagPK;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.exception.*;
import org.nhnacademy.book2onandonbookservice.repository.BookTagRepository;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;
import org.nhnacademy.book2onandonbookservice.service.enrichment.rate.ApiRateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TagEnrichmentService {
    private final GroqApiClient groqApiClient;
    private final GeminiApiClient geminiApiClient;
    private final ApiRateLimiter rateLimiter;
    private final TagRepository tagRepository;
    private final BookTagRepository bookTagRepository;
    private final ObjectProvider<TagEnrichmentService> selfProvider;

    /**
     * 외부 API 호출 단계 (트랜잭션 없음)
     */
    public BookContentDto generateContent(String title, String description, String isbn) {
        BookContentDto content;

        if (!rateLimiter.tryAcquireGroq()) {
            throw new GroqQuotaExceededException("Groq quota exceeded");
        }

        try {
            content = groqApiClient.extractContent(title, description, isbn);
        } catch (Exception groqEx) {
            if (!rateLimiter.tryAcquireGemini()) {
                throw new GeminiQuotaExceededException("Gemini quota exceeded");
            }
            try {
                content = geminiApiClient.extractContent(title, description, isbn);
            } catch (Exception geminiEx) {
                throw new GeminiTagGenerationException("AI fail", geminiEx);
            }
        }

        if (content == null) throw new TagGenerationFailedException("AI response null");
        if (content.hasNoTags()) throw new TagGenerationFailedException("No tags generated");
        if (content.hasNoChapter()) throw new TagGenerationFailedException("No chapter generated");

        return content;
    }

    /**
     * DB 반영 단계 (새 트랜잭션 보장)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyContent(Book book, BookContentDto content) {
        saveTags(book, content.getTags());
        book.setChapter(content.getChapter());
    }

    /**
     * 통합 단계 (트랜잭션 없음 -> API 대기 시간 동안 커넥션 점유 안함)
     */
    public void enrich(Book book, String title, String description, String isbn) {
        BookContentDto content = generateContent(title, description, isbn);
        selfProvider.getObject().applyContent(book, content);
    }

    private void saveTags(Book book, List<String> tagNames) {
        for (String raw : tagNames) {
            if (!StringUtils.hasText(raw)) continue;
            String name = raw.trim().toLowerCase();

            Tag tag = tagRepository.findByTagName(name)
                    .orElseGet(() -> {
                        try {
                            return tagRepository.saveAndFlush(Tag.builder().tagName(name).build());
                        } catch (Exception e) {
                            return tagRepository.findByTagName(name).orElseThrow();
                        }
                    });

            BookTagPK pk = new BookTagPK(book.getId(), tag.getId());
            if (!bookTagRepository.existsById(pk)) {
                bookTagRepository.save(BookTag.builder().pk(pk).book(book).tag(tag).build());
            }
        }
    }
}