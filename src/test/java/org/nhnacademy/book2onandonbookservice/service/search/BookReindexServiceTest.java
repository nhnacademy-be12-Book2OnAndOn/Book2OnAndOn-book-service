package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
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

@ExtendWith(MockitoExtension.class)
class BookReindexServiceTest {

    @InjectMocks
    private BookReindexService bookReindexService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookSearchIndexService bookSearchIndexService;

    @Mock
    private BookSearchRepository bookSearchRepository;

    @Mock
    private EntityManager entityManager;

    @Test
    @DisplayName("전체 재색인 성공: 정상적인 배치 저장 흐름")
    void reindexAll_Success() {
        Book book1 = createBook(1L);
        Book book2 = createBook(2L);
        List<Book> books = List.of(book1, book2);

        BookSearchDocument doc1 = mock(BookSearchDocument.class);
        BookSearchDocument doc2 = mock(BookSearchDocument.class);

        given(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .willReturn(books);
        given(bookRepository.findAllByIdGreaterThan(eq(2L), any(Pageable.class)))
                .willReturn(Collections.emptyList());

        given(bookSearchIndexService.createDocument(book1)).willReturn(doc1);
        given(bookSearchIndexService.createDocument(book2)).willReturn(doc2);

        bookReindexService.reindexAll();

        ArgumentCaptor<List<BookSearchDocument>> captor = ArgumentCaptor.forClass(List.class);
        then(bookSearchRepository).should(times(1)).saveAll(captor.capture());

        List<BookSearchDocument> savedDocs = captor.getValue();
        assertThat(savedDocs).hasSize(2);
        assertThat(savedDocs).contains(doc1, doc2);

        then(entityManager).should(times(1)).clear();
    }

    @Test
    @DisplayName("전체 재색인: 데이터가 없는 경우 바로 종료")
    void reindexAll_NoData() {
        given(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .willReturn(Collections.emptyList());

        bookReindexService.reindexAll();

        then(bookSearchIndexService).should(never()).createDocument(any());
        then(bookSearchRepository).should(never()).saveAll(anyList());
        then(entityManager).should(never()).clear();
    }

    @Test
    @DisplayName("전체 재색인: 페이징 처리가 올바르게 동작하는지 확인")
    void reindexAll_Pagination_Success() {
        Book bookPage1 = createBook(10L);
        Book bookPage2 = createBook(20L);

        given(bookRepository.findAllByIdGreaterThan(anyLong(), any(Pageable.class)))
                .willAnswer(invocation -> {
                    Long lastId = invocation.getArgument(0);
                    if (lastId == 0L) return List.of(bookPage1);
                    if (lastId == 10L) return List.of(bookPage2);
                    return Collections.emptyList();
                });

        bookReindexService.reindexAll();

        then(bookSearchRepository).should(times(2)).saveAll(anyList());
        then(entityManager).should(times(2)).clear();
    }

    @Test
    @DisplayName("부분 실패: 특정 책 변환(createDocument) 실패 시 해당 건만 스킵하고 나머지는 저장")
    void reindexAll_ConversionError_SkipOne() {
        Book successBook = createBook(1L);
        Book failBook = createBook(2L);
        List<Book> books = List.of(successBook, failBook);

        given(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .willReturn(books);
        given(bookRepository.findAllByIdGreaterThan(eq(2L), any(Pageable.class)))
                .willReturn(Collections.emptyList());

        BookSearchDocument doc = mock(BookSearchDocument.class);
        given(bookSearchIndexService.createDocument(successBook)).willReturn(doc);
        willThrow(new RuntimeException("Parsing Error")).given(bookSearchIndexService).createDocument(failBook);

        bookReindexService.reindexAll();

        ArgumentCaptor<List<BookSearchDocument>> captor = ArgumentCaptor.forClass(List.class);
        then(bookSearchRepository).should(times(1)).saveAll(captor.capture());

        List<BookSearchDocument> savedDocs = captor.getValue();
        assertThat(savedDocs).hasSize(1);
        assertThat(savedDocs).contains(doc);
    }

    @Test
    @DisplayName("배치 저장 실패: saveAll 실행 중 예외 발생 시 로그 남기고 계속 진행 (중단되지 않음)")
    void reindexAll_SaveError_Continue() {
        Book book = createBook(1L);
        List<Book> books = List.of(book);

        given(bookRepository.findAllByIdGreaterThan(eq(0L), any(Pageable.class)))
                .willReturn(books);
        given(bookRepository.findAllByIdGreaterThan(eq(1L), any(Pageable.class)))
                .willReturn(Collections.emptyList());

        given(bookSearchIndexService.createDocument(book)).willReturn(mock(BookSearchDocument.class));

        willThrow(new RuntimeException("Elasticsearch Down")).given(bookSearchRepository).saveAll(anyList());

        assertThatCode(() -> bookReindexService.reindexAll()).doesNotThrowAnyException();

        then(entityManager).should(times(1)).clear();
    }

    // 핵심 수정 부분: lenient() 사용
    private Book createBook(Long id) {
        Book book = mock(Book.class);
        // 호출되지 않아도 에러를 내지 않도록 lenient 설정
        lenient().when(book.getId()).thenReturn(id);
        return book;
    }
}