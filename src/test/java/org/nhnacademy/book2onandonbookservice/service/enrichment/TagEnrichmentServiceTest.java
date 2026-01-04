package org.nhnacademy.book2onandonbookservice.service.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TagEnrichmentServiceTest {

    @InjectMocks
    private TagEnrichmentService tagEnrichmentService;

    @Mock
    private GroqApiClient groqApiClient;

    @Mock
    private GeminiApiClient geminiApiClient;

    @Mock
    private ApiRateLimiter rateLimiter;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private BookTagRepository bookTagRepository;

    @Mock
    private ObjectProvider<TagEnrichmentService> selfProvider;

    /**
     * Helper: enrich() 호출 시 내부에서 selfProvider.getObject()가 호출되므로
     * 현재 테스트 대상인 service 객체 자신을 반환하도록 설정
     */
    private void setupSelfProvider() {
        when(selfProvider.getObject()).thenReturn(tagEnrichmentService);
    }

    @Test
    @DisplayName("enrich: Groq 성공, 정상적으로 태그와 챕터 저장 (Full Flow)")
    void enrich_GroqSuccess() {
        setupSelfProvider();

        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        String title = "Title";
        String description = "Desc";
        String isbn = "123";

        BookContentDto content = mock(BookContentDto.class);
        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(title, description, isbn)).thenReturn(content);

        when(content.hasNoTags()).thenReturn(false);
        when(content.hasNoChapter()).thenReturn(false);
        when(content.getTags()).thenReturn(List.of("tag1"));
        when(content.getChapter()).thenReturn("New Chapter");

        Tag tag = Tag.builder().tagName("tag1").build();
        ReflectionTestUtils.setField(tag, "id", 100L);

        when(tagRepository.findByTagName("tag1")).thenReturn(Optional.of(tag));
        when(bookTagRepository.existsById(any(BookTagPK.class))).thenReturn(false);

        tagEnrichmentService.enrich(book, title, description, isbn);

        assertThat(book.getChapter()).isEqualTo("New Chapter");
        verify(bookTagRepository).save(any(BookTag.class));
        verify(geminiApiClient, never()).extractContent(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("enrich: Groq 할당량 초과 예외 (DB 진입 전 실패)")
    void enrich_GroqQuotaExceeded() {
        when(rateLimiter.tryAcquireGroq()).thenReturn(false);
        Book book = new Book();

        assertThatThrownBy(() -> tagEnrichmentService.enrich(book, "t", "d", "i"))
                .isInstanceOf(GroqQuotaExceededException.class);

        verify(selfProvider, never()).getObject();
    }

    @Test
    @DisplayName("enrich: Groq 실패 -> Gemini 성공 (Fallback 동작 & DB 저장)")
    void enrich_GroqFail_GeminiSuccess() {
        setupSelfProvider();

        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);

        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Groq Fail"));

        when(rateLimiter.tryAcquireGemini()).thenReturn(true);
        BookContentDto content = mock(BookContentDto.class);
        when(geminiApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(content);

        when(content.hasNoTags()).thenReturn(false);
        when(content.hasNoChapter()).thenReturn(false);
        when(content.getTags()).thenReturn(List.of("tag1"));
        when(content.getChapter()).thenReturn("Gemini Chapter");

        Tag tag = Tag.builder().tagName("tag1").build();
        ReflectionTestUtils.setField(tag, "id", 100L);
        when(tagRepository.findByTagName("tag1")).thenReturn(Optional.of(tag));

        tagEnrichmentService.enrich(book, "t", "d", "i");

        assertThat(book.getChapter()).isEqualTo("Gemini Chapter");
        verify(geminiApiClient).extractContent(anyString(), anyString(), anyString());
        verify(bookTagRepository).save(any(BookTag.class));
    }

    @Test
    @DisplayName("enrich: Groq 실패 후 Gemini 할당량 초과")
    void enrich_GeminiQuotaExceeded() {
        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Groq Fail"));

        when(rateLimiter.tryAcquireGemini()).thenReturn(false);

        Book book = new Book();
        assertThatThrownBy(() -> tagEnrichmentService.enrich(book, "t", "d", "i"))
                .isInstanceOf(GeminiQuotaExceededException.class);
    }

    @Test
    @DisplayName("enrich: Groq, Gemini 모두 실패")
    void enrich_AllFail() {
        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Groq Fail"));

        when(rateLimiter.tryAcquireGemini()).thenReturn(true);
        when(geminiApiClient.extractContent(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Gemini Fail"));

        Book book = new Book();
        assertThatThrownBy(() -> tagEnrichmentService.enrich(book, "t", "d", "i"))
                .isInstanceOf(GeminiTagGenerationException.class);
    }

    @Test
    @DisplayName("enrich: AI 응답이 null인 경우")
    void enrich_ContentNull() {
        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(null);

        Book book = new Book();
        assertThatThrownBy(() -> tagEnrichmentService.enrich(book, "t", "d", "i"))
                .isInstanceOf(TagGenerationFailedException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("saveTags: 태그 저장 시 동시성 이슈 발생 후 재조회 성공 (enrich 호출을 통해 검증)")
    void saveTags_ConcurrencyHandling() {
        setupSelfProvider();

        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        BookContentDto content = mock(BookContentDto.class);

        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(content);

        when(content.hasNoTags()).thenReturn(false);
        when(content.hasNoChapter()).thenReturn(false);
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
        setupSelfProvider();

        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        BookContentDto content = mock(BookContentDto.class);

        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(content);
        when(content.getTags()).thenReturn(List.of("tag1"));

        Tag tag = Tag.builder().tagName("tag1").build();
        ReflectionTestUtils.setField(tag, "id", 100L);
        when(tagRepository.findByTagName("tag1")).thenReturn(Optional.of(tag));

        when(bookTagRepository.existsById(any(BookTagPK.class))).thenReturn(true);

        tagEnrichmentService.enrich(book, "t", "d", "i");

        verify(bookTagRepository, never()).save(any(BookTag.class));
    }

    @Test
    @DisplayName("saveTags: 태그가 존재하지 않으면 새로 생성")
    void saveTags_CreateNewTag() {
        setupSelfProvider();

        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        BookContentDto content = mock(BookContentDto.class);

        when(rateLimiter.tryAcquireGroq()).thenReturn(true);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(content);
        when(content.getTags()).thenReturn(List.of("newtag"));

        when(tagRepository.findByTagName("newtag")).thenReturn(Optional.empty());

        Tag savedTag = Tag.builder().tagName("newtag").build();
        ReflectionTestUtils.setField(savedTag, "id", 200L);
        when(tagRepository.saveAndFlush(any(Tag.class))).thenReturn(savedTag);

        tagEnrichmentService.enrich(book, "t", "d", "i");

        verify(tagRepository).saveAndFlush(any(Tag.class));
        verify(bookTagRepository).save(any(BookTag.class));
    }
}