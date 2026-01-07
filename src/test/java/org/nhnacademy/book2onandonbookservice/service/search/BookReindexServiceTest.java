package org.nhnacademy.book2onandonbookservice.service.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookSearchRepository;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BookReindexServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookSearchIndexService bookSearchIndexService;

    @Mock
    private BookSearchRepository bookSearchRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private BookReindexService bookReindexService;

    @Test
    @DisplayName("Happy Path: 모든 과정이 정상적으로 수행됨")
    void reindexAll_HappyPath() {
        Book book1 = mock(Book.class);
        Book book2 = mock(Book.class);
        BookSearchDocument doc1 = mock(BookSearchDocument.class);
        BookSearchDocument doc2 = mock(BookSearchDocument.class);

        when(doc2.getId()).thenReturn(2L);
        List<Book> bookList = List.of(book1, book2);

        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .thenReturn(bookList);
        when(bookRepository.findAllByIdGreaterThan(eq(2L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        when(bookSearchIndexService.createDocumentWithoutEmbedding(book1)).thenReturn(doc1);
        when(bookSearchIndexService.createDocumentWithoutEmbedding(book2)).thenReturn(doc2);

        bookReindexService.reindexAll();

        verify(bookSearchIndexService).injectEmbedding(doc1);
        verify(bookSearchIndexService).injectEmbedding(doc2);
        verify(bookSearchRepository).saveAll(anyList());

        verify(entityManager, times(1)).clear();
    }

    @Test
    @DisplayName("Fail Path: 객체 변환 중 예외 발생 시 해당 건너뛰고 진행")
    void reindexAll_ConversionError() {
        Book book1 = mock(Book.class);
        Book book2 = mock(Book.class);
        BookSearchDocument doc1 = mock(BookSearchDocument.class);


        when(doc1.getId()).thenReturn(1L);

        List<Book> bookList = List.of(book1, book2);

        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .thenReturn(bookList);
        when(bookRepository.findAllByIdGreaterThan(eq(1L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        when(bookSearchIndexService.createDocumentWithoutEmbedding(book1)).thenReturn(doc1);
        when(bookSearchIndexService.createDocumentWithoutEmbedding(book2))
                .thenThrow(new RuntimeException("Conversion Failed"));

        bookReindexService.reindexAll();

        verify(bookSearchIndexService, times(1)).injectEmbedding(doc1);
        verify(bookSearchRepository).saveAll(List.of(doc1));
    }

    @Test
    @DisplayName("Fail Path: ES 저장 중 예외 발생 시 catch 후 다음 배치 진행")
    void reindexAll_SaveError() {
        Book book1 = mock(Book.class);
        BookSearchDocument doc1 = mock(BookSearchDocument.class);

        when(book1.getId()).thenReturn(100L);

        List<Book> bookList = List.of(book1);

        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .thenReturn(bookList);
        when(bookRepository.findAllByIdGreaterThan(eq(100L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        when(bookSearchIndexService.createDocumentWithoutEmbedding(book1)).thenReturn(doc1);

        lenient().when(doc1.getId()).thenReturn(100L);

        doThrow(new RuntimeException("ES Save Failed"))
                .when(bookSearchRepository).saveAll(anyList());

        bookReindexService.reindexAll();

        verify(bookSearchIndexService).injectEmbedding(doc1);
        verify(bookSearchRepository).saveAll(anyList());
        verify(entityManager, times(1)).clear();
    }

    @Test
    @DisplayName("Edge Case: 조회된 데이터가 없는 경우 즉시 종료")
    void reindexAll_EmptyData() {
        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        bookReindexService.reindexAll();

        verify(bookSearchIndexService, times(0)).createDocumentWithoutEmbedding(any());
        verify(bookSearchRepository, times(0)).saveAll(any());
        verify(entityManager, times(0)).clear();
    }
}