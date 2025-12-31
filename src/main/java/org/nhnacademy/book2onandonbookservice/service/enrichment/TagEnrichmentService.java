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
import org.nhnacademy.book2onandonbookservice.exception.GeminiQuotaExceededException;
import org.nhnacademy.book2onandonbookservice.exception.GeminiTagGenerationException;
import org.nhnacademy.book2onandonbookservice.exception.GroqQuotaExceededException;
import org.nhnacademy.book2onandonbookservice.exception.TagGenerationFailedException;
import org.nhnacademy.book2onandonbookservice.repository.BookTagRepository;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;
import org.nhnacademy.book2onandonbookservice.service.enrichment.rate.ApiRateLimiter;
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

    /**
     * 2) DB 반영만 수행 (트랜잭션 안에서)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyContent(Book book, BookContentDto content) {
        saveTags(book, content.getTags());
        book.setChapter(content.getChapter());
    }

    @Transactional
    public void enrich(Book book, String title, String description, String isbn) {
        BookContentDto content = generateContent(title, description, isbn);
        applyContent(book, content);
    }

    private void saveTags(Book book, List<String> tagNames){
        for(String raw : tagNames){
            if(!StringUtils.hasText(raw)) continue;

            String name = raw.trim().toLowerCase();

            Tag tag = tagRepository.findByTagName(name)
                    .orElseGet(()->{
            try{
                return tagRepository.saveAndFlush(Tag.builder().tagName(name).build());
            } catch (Exception e) {
                return tagRepository.findByTagName(name).orElseThrow();
            }});



            BookTagPK pk = new BookTagPK(book.getId(), tag.getId());

            if(!bookTagRepository.existsById(pk)){
                BookTag bookTag = BookTag.builder()
                        .pk(pk)
                        .book(book)
                        .tag(tag)
                        .build();

                bookTagRepository.save(bookTag);
            }
        }
    }
}
