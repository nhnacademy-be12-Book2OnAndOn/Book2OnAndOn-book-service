package org.nhnacademy.book2onandonbookservice.service.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.BookTagPK;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.repository.BookTagRepository;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TagEnrichmentServiceTest {

    @InjectMocks
    private TagEnrichmentService tagEnrichmentService;

    @Mock
    private TagGenerationService tagGenerationService;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private BookTagRepository bookTagRepository;

    @Test
    @DisplayName("enrich: 정상적으로 태그와 챕터 저장")
    void enrich_Success() {
        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        String title = "Title";
        String description = "Desc";
        String isbn = "123";

        BookContentDto content = mock(BookContentDto.class);
        when(tagGenerationService.generateContent(title, description, isbn)).thenReturn(content);
        
        when(content.getTags()).thenReturn(List.of("tag1"));
        when(content.getChapter()).thenReturn("New Chapter");

        Tag tag = Tag.builder().tagName("tag1").build();
        ReflectionTestUtils.setField(tag, "id", 100L);
        when(tagRepository.findByTagName("tag1")).thenReturn(Optional.of(tag));
        when(bookTagRepository.existsById(any(BookTagPK.class))).thenReturn(false);

        tagEnrichmentService.enrich(book, title, description, isbn);

        assertThat(book.getChapter()).isEqualTo("New Chapter");
        verify(bookTagRepository).save(any(BookTag.class));
    }

    @Test
    @DisplayName("saveTags: 태그 저장 시 동시성 이슈 발생 후 재조회 성공")
    void saveTags_ConcurrencyHandling() {
        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        BookContentDto content = mock(BookContentDto.class);
        
        when(tagGenerationService.generateContent(anyString(), anyString(), anyString())).thenReturn(content);
        
        when(content.getTags()).thenReturn(List.of("tag1"));
        when(content.getChapter()).thenReturn("C");

        Tag savedTag = Tag.builder().tagName("tag1").build();
        ReflectionTestUtils.setField(savedTag, "id", 100L);

        when(tagRepository.findByTagName("tag1"))
                .thenReturn(Optional.empty()) 
                .thenReturn(Optional.of(savedTag)); 
        
        when(tagRepository.saveAndFlush(any(Tag.class)))
                .thenThrow(new RuntimeException("Constraint Violation"));

        tagEnrichmentService.enrich(book, "t", "d", "i");

        verify(tagRepository, times(2)).findByTagName("tag1");
        verify(bookTagRepository).save(any(BookTag.class));
    }

    @Test
    @DisplayName("saveTags: 이미 존재하는 BookTag는 저장하지 않음")
    void saveTags_ExistingBookTag() {
        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        BookContentDto content = mock(BookContentDto.class);

        when(tagGenerationService.generateContent(anyString(), anyString(), anyString())).thenReturn(content);

        when(content.getTags()).thenReturn(List.of("tag1"));
        when(content.getChapter()).thenReturn("C");

        Tag tag = Tag.builder().tagName("tag1").build();
        ReflectionTestUtils.setField(tag, "id", 100L);
        when(tagRepository.findByTagName("tag1")).thenReturn(Optional.of(tag));

        when(bookTagRepository.existsById(any(BookTagPK.class))).thenReturn(true);

        tagEnrichmentService.enrich(book, "t", "d", "i");

        verify(bookTagRepository, never()).save(any(BookTag.class));
    }

    @Test
    @DisplayName("saveTags: 태그 이름이 비어있으면 건너뜀")
    void saveTags_EmptyTagName() {
        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        BookContentDto content = mock(BookContentDto.class);

        when(tagGenerationService.generateContent(anyString(), anyString(), anyString())).thenReturn(content);

        when(content.getTags()).thenReturn(List.of(""));
        when(content.getChapter()).thenReturn("C");

        tagEnrichmentService.enrich(book, "t", "d", "i");

        verify(tagRepository, never()).findByTagName(anyString());
        verify(bookTagRepository, never()).save(any(BookTag.class));
    }

    @Test
    @DisplayName("saveTags: 태그가 존재하지 않으면 새로 생성")
    void saveTags_CreateNewTag() {
        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        BookContentDto content = mock(BookContentDto.class);

        when(tagGenerationService.generateContent(anyString(), anyString(), anyString())).thenReturn(content);

        when(content.getTags()).thenReturn(List.of("newtag"));
        when(content.getChapter()).thenReturn("C");

        when(tagRepository.findByTagName("newtag")).thenReturn(Optional.empty());
        
        Tag savedTag = Tag.builder().tagName("newtag").build();
        ReflectionTestUtils.setField(savedTag, "id", 200L);
        when(tagRepository.saveAndFlush(any(Tag.class))).thenReturn(savedTag);

        tagEnrichmentService.enrich(book, "t", "d", "i");

        verify(tagRepository).saveAndFlush(any(Tag.class));
        verify(bookTagRepository).save(any(BookTag.class));
    }
}
