package org.nhnacademy.book2onandonbookservice.service.enrichment;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.BookTagPK;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.repository.BookTagRepository;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TagEnrichmentService {
    private final TagRepository tagRepository;
    private final BookTagRepository bookTagRepository;
    private final TagGenerationService tagGenerationService;

    /**
     * DB 반영만 수행 (트랜잭션 안에서 호출됨)
     */
    public void applyContent(Book book, BookContentDto content) {
        saveTags(book, content.getTags());
        book.setChapter(content.getChapter());
    }

    @Transactional
    public void enrich(Book book, String title, String description, String isbn) {
        BookContentDto content = tagGenerationService.generateContent(title, description, isbn);
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
