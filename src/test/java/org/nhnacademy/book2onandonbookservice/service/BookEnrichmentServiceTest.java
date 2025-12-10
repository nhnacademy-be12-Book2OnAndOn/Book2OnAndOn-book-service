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

    @InjectMocks
    private BookEnrichmentService bookEnrichmentService;

    private Book testBook;
    private AladinApiResponse.Item aladinItem;
    private BookContentDto geminiContent;

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
        geminiContent = new BookContentDto(List.of("소설"), "1. 서론\n2. 본론");
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
    @DisplayName("알라딘 및 Gemini 데이터로 책 정보 보강 성공")
    void enrichBookData_WithAllExternalData() throws JsonProcessingException {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(aladinItem);
        when(geminiApiClient.extractContent(anyString(), anyString())).thenReturn(geminiContent);

        Tag tag1 = Tag.builder().id(1L).tagName("소설").build();
        when(tagRepository.findByTagName("소설")).thenReturn(Optional.of(tag1));

        bookEnrichmentService.enrichBookData(1L);

        verify(bookRepository).save(testBook);
        verify(bookSearchIndexService).index(testBook);
        verify(bookTagRepository, times(1)).save(any(BookTag.class));
        assertThat(testBook.getChapter()).isEqualTo("1. 서론\n2. 본론");
    }

    @Test
    @DisplayName("외부 데이터가 없고 가격도 0원일 때 책 삭제 처리")
    void updateBookInTransaction_NoExternalData_And_NoPrice_DeleteBook() throws JsonProcessingException {
        testBook.setPriceStandard(0L);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(null);
        when(geminiApiClient.extractContent(anyString(), anyString())).thenReturn(BookContentDto.empty());

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
        when(geminiApiClient.extractContent(anyString(), anyString())).thenReturn(BookContentDto.empty());

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

        bookEnrichmentService.enrichBookData(1L);

        assertThat(testBook.getPriceStandard()).isEqualTo(20000L);
        assertThat(testBook.getPriceSales()).isEqualTo(18000L);
        verify(bookRepository).save(testBook);
    }

    @Test
    @DisplayName("Gemini 태그 생성 실패 시에도 알라딘 데이터가 있으면 저장")
    void enrichBookData_GeminiFail_But_AladinSuccess() throws JsonProcessingException {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinItem.getPriceStandard()).thenReturn(20000L);
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(aladinItem);

        when(geminiApiClient.extractContent(anyString(), anyString())).thenThrow(new RuntimeException("Gemini Error"));

        bookEnrichmentService.enrichBookData(1L);

        verify(bookRepository).save(testBook);
    }

    @Test
    @DisplayName("Gemini 목차가 있고 기존 목차가 없을 때 목차 업데이트 성공")
    void updateBookInTransaction_UpdateChapter() throws JsonProcessingException {
        testBook.setChapter(null);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(aladinItem);

        BookContentDto dtoWithChapter = new BookContentDto(Collections.emptyList(), "새로운 목차");
        when(geminiApiClient.extractContent(anyString(), anyString())).thenReturn(dtoWithChapter);

        bookEnrichmentService.enrichBookData(1L);

        assertThat(testBook.getChapter()).isEqualTo("새로운 목차");
        verify(bookRepository).save(testBook);
    }
}