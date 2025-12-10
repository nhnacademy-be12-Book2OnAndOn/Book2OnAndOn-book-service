package org.nhnacademy.book2onandonbookservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiApiClient;
import org.nhnacademy.book2onandonbookservice.client.GroqApiClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.nhnacademy.book2onandonbookservice.entity.*;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.repository.*;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;

@ExtendWith(MockitoExtension.class)
class BookEnrichmentServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BookCategoryRepository bookCategoryRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private BookTagRepository bookTagRepository;
    @Mock
    private BookSearchIndexService bookSearchIndexService;
    @Mock
    private GeminiApiClient geminiApiClient;
    @Mock
    private AladinApiClient aladinApiClient;
    @Mock
    private GroqApiClient groqApiClient;

    @InjectMocks
    private BookEnrichmentService bookEnrichmentService;

    private Book testBook;
    private AladinApiResponse.Item aladinItem;
    private BookContentDto aiContent;

    @BeforeEach
    void setUp() {
        testBook = Book.builder()
                .id(1L)
                .isbn("9788901234567")
                .title("테스트 책")
                .description("테스트 설명")
                .bookCategories(new HashSet<>())
                .bookTags(new HashSet<>())
                .images(new HashSet<>())
                .status(BookStatus.ON_SALE)
                .priceStandard(0L)
                .build();

        aladinItem = mock(AladinApiResponse.Item.class);
        aiContent = new BookContentDto(List.of("소설"), "1. 서론\n2. 본론");
    }

    @Test
    @DisplayName("책이 존재하지 않을 때 예외 발생")
    void enrichBookData_BookNotExists() {
        when(bookRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookEnrichmentService.enrichBookData(1L))
                .isInstanceOf(NotFoundBookException.class);
    }

    @Test
    @DisplayName("이미 삭제된 책이면 로직 종료")
    void enrichBookData_BookDeleted() throws JsonProcessingException {
        testBook.setStatus(BookStatus.BOOK_DELETED);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        bookEnrichmentService.enrichBookData(1L);

        verify(aladinApiClient, never()).searchByIsbn(anyString());
    }

    @Test
    @DisplayName("알라딘 API 통신 오류 발생 시 예외 던짐")
    void enrichBookData_AladinApiError() throws JsonProcessingException {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(anyString())).thenThrow(new RuntimeException("Connection Timeout"));

        assertThatThrownBy(() -> bookEnrichmentService.enrichBookData(1L))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Groq 성공 시 Gemini 호출 안 함")
    void enrichBookData_GroqSuccess() throws JsonProcessingException {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(aladinItem);
        // Groq 성공 설정
        when(groqApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(aiContent);

        Tag tag1 = Tag.builder().id(1L).tagName("소설").build();
        when(tagRepository.findByTagName("소설")).thenReturn(Optional.of(tag1));

        bookEnrichmentService.enrichBookData(1L);

        // 검증: Groq은 호출되고 Gemini는 호출되지 않아야 함
        verify(groqApiClient).extractContent(anyString(), anyString(), anyString());
        verify(geminiApiClient, never()).extractContent(anyString(), anyString(), anyString());

        verify(bookRepository).save(testBook);
        verify(bookSearchIndexService).index(testBook);
    }

    @Test
    @DisplayName("Groq 실패 시 Gemini로 재시도하여 성공")
    void enrichBookData_GroqFail_GeminiSuccess() throws JsonProcessingException {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(any())).thenReturn(aladinItem);

        when(groqApiClient.extractContent(any(), any(), any()))
                .thenThrow(new RuntimeException("Groq Error"));
        when(geminiApiClient.extractContent(any(), any(), any()))
                .thenReturn(aiContent);

        Tag tag1 = Tag.builder().id(1L).tagName("소설").build();
        when(tagRepository.findByTagName("소설")).thenReturn(Optional.of(tag1));

        bookEnrichmentService.enrichBookData(1L);

        // 검증: 둘 다 호출되어야 함
        verify(groqApiClient).extractContent(any(), any(), any());
        verify(geminiApiClient).extractContent(any(), any(), any());

        verify(bookRepository).save(testBook);
    }

    @Test
    @DisplayName("Groq 실패 후 Gemini Limit 에러 발생 시 예외 던짐")
    void enrichBookData_GroqFail_GeminiLimitError() throws JsonProcessingException {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(any())).thenReturn(aladinItem);

        when(groqApiClient.extractContent(any(), any(), any()))
                .thenThrow(new RuntimeException("Groq Error"));
        // Gemini Limit 에러 설정
        when(geminiApiClient.extractContent(any(), any(), any()))
                .thenThrow(new RuntimeException("Gemini API Rate Limit Exceeded"));

        assertThatThrownBy(() -> bookEnrichmentService.enrichBookData(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Limit");
    }

    @Test
    @DisplayName("외부 데이터가 없고 가격도 0원일 때 책 삭제 처리")
    void updateBookInTransaction_NoExternalData_And_NoPrice_DeleteBook() throws JsonProcessingException {
        testBook.setPriceStandard(0L);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(null);
        // Groq 호출해서 null 리턴하는 상황 가정 (혹은 예외 발생 후 Gemini도 null 리턴)
        // 실제 로직에서는 예외가 발생하면 catch해서 Gemini 호출하므로, 여기선 Groq이 null 리턴하는 상황보다는
        // Groq 에러 -> Gemini 에러(일반 에러) -> catch 후 null 상태로 진행되는 흐름을 테스트하는 게 맞으나,
        // Mockito 설정상 Groq이 null 리턴한다고 가정해도 무방함 (BookContentDto.empty()와 유사)
        when(groqApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(null);
        // 실제 코드 흐름상 Groq null이면 catch 안 걸리고 aiContent가 null이 됨

        bookEnrichmentService.enrichBookData(1L);

        assertThat(testBook.getStatus()).isEqualTo(BookStatus.BOOK_DELETED);
        verify(bookRepository).save(testBook);
        verify(bookSearchIndexService).deleteIndex(1L);
    }

    @Test
    @DisplayName("외부 데이터가 없어도 기존 가격 정보가 있으면 삭제하지 않음")
    void updateBookInTransaction_NoExternalData_But_HasPrice_KeepBook() throws JsonProcessingException {
        testBook.setPriceStandard(15000L);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(null);
        when(groqApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(null);

        bookEnrichmentService.enrichBookData(1L);

        assertThat(testBook.getStatus()).isNotEqualTo(BookStatus.BOOK_DELETED);
        verify(bookSearchIndexService, never()).deleteIndex(anyLong());
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    @DisplayName("알라딘 데이터로 가격 정보 업데이트")
    void updateBookInTransaction_UpdatePrice() throws JsonProcessingException {
        testBook.setPriceStandard(0L);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        when(aladinItem.getPriceStandard()).thenReturn(20000L);
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(aladinItem);

        // AI 호출 결과가 null이어도 알라딘 데이터가 있으면 저장됨
        when(groqApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(null);

        bookEnrichmentService.enrichBookData(1L);

        assertThat(testBook.getPriceStandard()).isEqualTo(20000L);
        assertThat(testBook.getPriceSales()).isEqualTo(18000L);
        verify(bookRepository).save(testBook);
    }

    @Test
    @DisplayName("AI 목차가 있고 기존 목차가 없을 때 목차 업데이트 성공")
    void updateBookInTransaction_UpdateChapter() throws JsonProcessingException {
        testBook.setChapter(null);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(aladinItem);

        BookContentDto dtoWithChapter = new BookContentDto(Collections.emptyList(), "새로운 목차");
        when(groqApiClient.extractContent(anyString(), anyString(), anyString())).thenReturn(dtoWithChapter);

        bookEnrichmentService.enrichBookData(1L);

        assertThat(testBook.getChapter()).isEqualTo("새로운 목차");
        verify(bookRepository).save(testBook);
    }
}