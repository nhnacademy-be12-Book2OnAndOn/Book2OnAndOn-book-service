package org.nhnacademy.book2onandonbookservice.service.search;

import jakarta.persistence.EntityManager;
import java.util.Objects;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("성공: DB의 모든 책을 조회하여 임베딩 주입 후 ES에 저장")
    void reindexAll_Success() {
        Book book1 = mock(Book.class);

        Book book2 = mock(Book.class);
        when(book2.getId()).thenReturn(2L);

        List<Book> batch = List.of(book1, book2);

        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .thenReturn(batch);
        when(bookRepository.findAllByIdGreaterThan(eq(2L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        BookSearchDocument doc1 = mock(BookSearchDocument.class);
        BookSearchDocument doc2 = mock(BookSearchDocument.class);


        when(bookSearchIndexService.createDocumentWithoutEmbedding(book1)).thenReturn(doc1);
        when(bookSearchIndexService.createDocumentWithoutEmbedding(book2)).thenReturn(doc2);

        bookReindexService.reindexAll();

        verify(bookSearchIndexService).createDocumentWithoutEmbedding(book1);
        verify(bookSearchIndexService).createDocumentWithoutEmbedding(book2);
        verify(bookSearchIndexService).injectEmbedding(doc1);
        verify(bookSearchIndexService).injectEmbedding(doc2);

        ArgumentCaptor<List<BookSearchDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookSearchRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);

        verify(entityManager, times(1)).clear();
    }

    @Test
    @DisplayName("성공: 객체 변환 실패 시 해당 문서는 제외하고 나머지만 저장")
    void reindexAll_PartialConversionFailure() {
        Book book1 = mock(Book.class);
        Book book2 = mock(Book.class);
        when(book2.getId()).thenReturn(2L);

        List<Book> batch = List.of(book1, book2);

        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class))).thenReturn(batch);

        BookSearchDocument doc1 = mock(BookSearchDocument.class);
        when(bookSearchIndexService.createDocumentWithoutEmbedding(book1)).thenReturn(doc1);

        doThrow(new RuntimeException("Conversion Error"))
                .when(bookSearchIndexService).createDocumentWithoutEmbedding(book2);

        bookReindexService.reindexAll();

        verify(bookSearchIndexService).injectEmbedding(doc1);
        verify(bookSearchIndexService, never()).injectEmbedding(argThat(Objects::isNull));
        ArgumentCaptor<List<BookSearchDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookSearchRepository).saveAll(captor.capture());

        List<BookSearchDocument> savedDocs = captor.getValue();
        assertThat(savedDocs).hasSize(1).contains(doc1);

        verify(entityManager).clear();
    }

    @Test
    @DisplayName("성공: 임베딩 주입 실패 시 예외를 잡고 저장은 계속 진행")
    void reindexAll_EmbeddingInjectionFailure() {
        Book book1 = mock(Book.class);
        when(book1.getId()).thenReturn(1L);
        List<Book> batch = List.of(book1);

        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class))).thenReturn(batch);
        when(bookRepository.findAllByIdGreaterThan(eq(1L), any(Pageable.class))).thenReturn(Collections.emptyList());

        BookSearchDocument doc1 = mock(BookSearchDocument.class);
        when(doc1.getId()).thenReturn(1L);
        when(bookSearchIndexService.createDocumentWithoutEmbedding(book1)).thenReturn(doc1);

        doThrow(new RuntimeException("Embedding Error"))
                .when(bookSearchIndexService).injectEmbedding(doc1);

        bookReindexService.reindexAll();

        verify(bookSearchRepository).saveAll(anyList());
        verify(entityManager).clear();
    }

    @Test
    @DisplayName("성공: ES 배치 저장 실패 시 예외를 잡고 다음 배치 진행")
    void reindexAll_BatchSaveFailure() {
        Book book1 = mock(Book.class);
        when(book1.getId()).thenReturn(1L);
        List<Book> batch = List.of(book1);

        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class))).thenReturn(batch);
        when(bookRepository.findAllByIdGreaterThan(eq(1L), any(Pageable.class))).thenReturn(Collections.emptyList());

        BookSearchDocument doc1 = mock(BookSearchDocument.class);
        when(bookSearchIndexService.createDocumentWithoutEmbedding(book1)).thenReturn(doc1);

        doThrow(new RuntimeException("ES Save Error"))
                .when(bookSearchRepository).saveAll(anyList());

        bookReindexService.reindexAll();

        verify(bookSearchIndexService).injectEmbedding(doc1);
        verify(bookSearchRepository).saveAll(anyList());
        verify(entityManager).clear();
    }

    @Test
    @DisplayName("성공: DB에 책이 없는 경우 작업 수행 안함")
    void reindexAll_EmptyDB() {
        when(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        bookReindexService.reindexAll();

        verify(bookSearchIndexService, never()).createDocumentWithoutEmbedding(any());
        verify(bookSearchIndexService, never()).injectEmbedding(any());
        verify(bookSearchRepository, never()).saveAll(any());
        verify(entityManager, never()).clear();
    }
}