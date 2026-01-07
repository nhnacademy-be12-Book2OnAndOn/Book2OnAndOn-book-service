package org.nhnacademy.book2onandonbookservice.service.search;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookSearchRepository;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    @DisplayName("성공: DB의 모든 책을 조회하여 Elasticsearch에 저장 (reindexAll)")
    void reindexAll_Success() {
        Book book1 = mock(Book.class);

        Book book2 = mock(Book.class);
        when(book2.getId()).thenReturn(2L);

        List<Book> batch1 = List.of(book1, book2);

        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .thenReturn(batch1);

        when(bookRepository.findAllByIdGreaterThan(eq(2L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        BookSearchDocument doc1 = mock(BookSearchDocument.class);
        BookSearchDocument doc2 = mock(BookSearchDocument.class);

        when(bookSearchIndexService.createDocument(book1)).thenReturn(doc1);
        when(bookSearchIndexService.createDocument(book2)).thenReturn(doc2);

//        bookReindexService.reindexAll();

        verify(bookSearchIndexService).createDocument(book1);
        verify(bookSearchIndexService).createDocument(book2);
        verify(bookSearchRepository, times(1)).saveAll(anyList());
        verify(entityManager, times(1)).clear();
    }

    @Test
    @DisplayName("성공: 일부 책 변환 실패 시 로그를 남기고 나머지는 저장 (Partial Conversion Failure)")
    void reindexAll_PartialConversionFailure() {
        Book book1 = mock(Book.class);

        Book book2 = mock(Book.class);
        when(book2.getId()).thenReturn(2L);

        List<Book> batch = List.of(book1, book2);

        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class))).thenReturn(batch);
        when(bookRepository.findAllByIdGreaterThan(eq(2L), any(Pageable.class))).thenReturn(Collections.emptyList());

        BookSearchDocument doc1 = mock(BookSearchDocument.class);
        when(bookSearchIndexService.createDocument(book1)).thenReturn(doc1);

        doThrow(new RuntimeException("Conversion Error"))
                .when(bookSearchIndexService).createDocument(book2);

//        bookReindexService.reindexAll();

        ArgumentCaptor<Iterable<BookSearchDocument>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(bookSearchRepository).saveAll(captor.capture());

        List<BookSearchDocument> savedDocs = (List<BookSearchDocument>) captor.getValue();
        assertThat(savedDocs).hasSize(1);

        verify(entityManager).clear();
    }

    @Test
    @DisplayName("성공: ES 배치 저장 실패 시 로그를 남기고 계속 진행 (Batch Save Failure)")
    void reindexAll_BatchSaveFailure() {

        Book book1 = mock(Book.class);
        when(book1.getId()).thenReturn(1L);
        List<Book> batch = List.of(book1);

        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class))).thenReturn(batch);
        when(bookRepository.findAllByIdGreaterThan(eq(1L), any(Pageable.class))).thenReturn(Collections.emptyList());

        BookSearchDocument doc1 = mock(BookSearchDocument.class);
        when(bookSearchIndexService.createDocument(book1)).thenReturn(doc1);

        doThrow(new RuntimeException("ES Save Error"))
                .when(bookSearchRepository).saveAll(anyList());

        bookReindexService.reindexAll();


        verify(entityManager).clear();
    }

    @Test
    @DisplayName("성공: DB에 책이 없는 경우 아무 작업도 안함 (Empty DB)")
    void reindexAll_EmptyDB() {
        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

//        bookReindexService.reindexAll();

        verify(bookSearchIndexService, never()).createDocument(any());
        verify(bookSearchRepository, never()).saveAll(any());
        verify(entityManager, never()).clear();
    }
}