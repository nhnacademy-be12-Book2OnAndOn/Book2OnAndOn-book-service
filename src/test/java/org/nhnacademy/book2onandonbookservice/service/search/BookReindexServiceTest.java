package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BookReindexServiceTest {

    @InjectMocks
    private BookReindexService bookReindexService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookSearchIndexService bookSearchIndexService;

    @Mock
    private EntityManager entityManager;

    @Test
    @DisplayName("전체 재색인 성공 - 단일 페이지 데이터")
    void reindexAll_Success_SinglePage() {
        Book book1 = createBook(1L);
        Book book2 = createBook(2L);
        List<Book> books = List.of(book1, book2);

        given(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .willReturn(books);

        given(bookRepository.findAllByIdGreaterThan(eq(2L), any(Pageable.class)))
                .willReturn(Collections.emptyList());
        bookReindexService.reindexAll();

        verify(bookSearchIndexService).index(book1);
        verify(bookSearchIndexService).index(book2);
        verify(bookRepository, times(2)).findAllByIdGreaterThan(anyLong(), any(Pageable.class));
        verify(entityManager, times(1)).clear();
    }

    @Test
    @DisplayName("전체 재색인 성공 - 멀티 페이지 (Cursor 동작 확인)")
    void reindexAll_Success_MultiPage() {
        Book book1 = createBook(10L);
        Book book2 = createBook(20L);

        given(bookRepository.findAllByIdGreaterThan(anyLong(), any(Pageable.class)))
                .willAnswer(invocation -> {
                    Long lastId = invocation.getArgument(0);
                    if (lastId == 0L) {
                        return List.of(book1);
                    }
                    if (lastId == 10L) {
                        return List.of(book2);
                    }
                    return Collections.emptyList();
                });

        bookReindexService.reindexAll();

        verify(bookSearchIndexService).index(book1);
        verify(bookSearchIndexService).index(book2);

        verify(bookRepository, times(3)).findAllByIdGreaterThan(anyLong(), any(Pageable.class));
        verify(entityManager, times(2)).clear();
    }

    @Test
    @DisplayName("전체 재색인 - 데이터 없음")
    void reindexAll_NoData() {
        given(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .willReturn(Collections.emptyList());

        bookReindexService.reindexAll();

        verify(bookSearchIndexService, times(0)).index(any());
        verify(entityManager, times(0)).clear();
    }

    @Test
    @DisplayName("재색인 중 예외 발생 시 - 중단하지 않고 다음 책 진행 (Resilience)")
    void reindexAll_ContinueOnException() {
        Book successBook1 = createBook(1L);
        Book failBook = createBook(2L);
        Book successBook2 = createBook(3L);
        List<Book> books = List.of(successBook1, failBook, successBook2);

        given(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .willReturn(books);
        given(bookRepository.findAllByIdGreaterThan(eq(3L), any(Pageable.class)))
                .willReturn(Collections.emptyList());

        willThrow(new RuntimeException("ES Connection Error"))
                .given(bookSearchIndexService).index(failBook);

        assertThatCode(() -> bookReindexService.reindexAll()).doesNotThrowAnyException();

        verify(bookSearchIndexService).index(successBook1);
        verify(bookSearchIndexService).index(failBook); // 호출은 되었음
        verify(bookSearchIndexService).index(successBook2);
    }

    private Book createBook(Long id) {
        Book book = mock(Book.class);
        lenient().when(book.getId()).thenReturn(id);
        return book;
    }
}