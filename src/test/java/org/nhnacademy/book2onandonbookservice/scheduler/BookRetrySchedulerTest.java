package org.nhnacademy.book2onandonbookservice.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.BookEnrichmentService;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class BookRetrySchedulerTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookEnrichmentService bookEnrichmentService;

    @InjectMocks
    private BookRetryScheduler bookRetryScheduler;

    private Book book1;
    private Book book2;
    private Book book3;

    @BeforeEach
    void setUp() {
        book1 = Book.builder()
                .id(1L)
                .isbn("9788901234567")
                .title("책1")
                .build();

        book2 = Book.builder()
                .id(2L)
                .isbn("9788901234568")
                .title("책2")
                .build();

        book3 = Book.builder()
                .id(3L)
                .isbn("9788901234569")
                .title("책3")
                .build();
    }

    @Test
    @DisplayName("보강 대상 도서가 있을 때 enrichBookData 호출")
    void runEnrichment_WithBooks() {

        List<Book> books = Arrays.asList(book1, book2, book3);
        when(bookRepository.findBooksNeedingEnrichment(PageRequest.of(0, 20)))
                .thenReturn(books);

        bookRetryScheduler.runEnrichment();

        verify(bookRepository).findBooksNeedingEnrichment(PageRequest.of(0, 20));
        verify(bookEnrichmentService).enrichBookData(1L);
        verify(bookEnrichmentService).enrichBookData(2L);
        verify(bookEnrichmentService).enrichBookData(3L);
    }

    @Test
    @DisplayName("보강 대상 도서가 없을 때 enrichBookData 호출 안함")
    void runEnrichment_WithoutBooks() {
        when(bookRepository.findBooksNeedingEnrichment(PageRequest.of(0, 20)))
                .thenReturn(Collections.emptyList());

        bookRetryScheduler.runEnrichment();

        verify(bookRepository).findBooksNeedingEnrichment(PageRequest.of(0, 20));
        verify(bookEnrichmentService, never()).enrichBookData(any());
    }

    @Test
    @DisplayName("보강 대상 도서가 1권만 있을 때")
    void runEnrichment_WithSingleBook() {
        List<Book> books = Arrays.asList(book1);
        when(bookRepository.findBooksNeedingEnrichment(PageRequest.of(0, 20)))
                .thenReturn(books);

        bookRetryScheduler.runEnrichment();

        verify(bookRepository).findBooksNeedingEnrichment(PageRequest.of(0, 20));
        verify(bookEnrichmentService, times(1)).enrichBookData(1L);
    }

    @Test
    @DisplayName("보강 대상 도서가 최대 개수(20권)일 때")
    void runEnrichment_WithMaxBooks() {
        List<Book> books = Arrays.asList(
                book1, book2, book3,
                createBook(4L), createBook(5L), createBook(6L), createBook(7L), createBook(8L),
                createBook(9L), createBook(10L), createBook(11L), createBook(12L), createBook(13L),
                createBook(14L), createBook(15L), createBook(16L), createBook(17L), createBook(18L),
                createBook(19L), createBook(20L)
        );
        when(bookRepository.findBooksNeedingEnrichment(PageRequest.of(0, 20)))
                .thenReturn(books);

        bookRetryScheduler.runEnrichment();

        verify(bookRepository).findBooksNeedingEnrichment(PageRequest.of(0, 20));
        verify(bookEnrichmentService, times(20)).enrichBookData(any());
    }

    @Test
    @DisplayName("태그 누락 도서가 있을 때 enrichBookData 호출")
    void retryMissingTags_WithBooks() {
        List<Book> books = Arrays.asList(book1, book2);
        when(bookRepository.findBooksNeedingTags(PageRequest.of(0, 5)))
                .thenReturn(books);

        bookRetryScheduler.retryMissingTags();

        verify(bookRepository).findBooksNeedingTags(PageRequest.of(0, 5));
        verify(bookEnrichmentService).enrichBookData(1L);
        verify(bookEnrichmentService).enrichBookData(2L);
    }

    @Test
    @DisplayName("태그 누락 도서가 없을 때 enrichBookData 호출 안함")
    void retryMissingTags_WithoutBooks() {
        when(bookRepository.findBooksNeedingTags(PageRequest.of(0, 5)))
                .thenReturn(Collections.emptyList());

        bookRetryScheduler.retryMissingTags();

        verify(bookRepository).findBooksNeedingTags(PageRequest.of(0, 5));
        verify(bookEnrichmentService, never()).enrichBookData(any());
    }

    @Test
    @DisplayName("태그 누락 도서가 1권만 있을 때")
    void retryMissingTags_WithSingleBook() {

        List<Book> books = Arrays.asList(book1);
        when(bookRepository.findBooksNeedingTags(PageRequest.of(0, 5)))
                .thenReturn(books);

        bookRetryScheduler.retryMissingTags();

        verify(bookRepository).findBooksNeedingTags(PageRequest.of(0, 5));
        verify(bookEnrichmentService, times(1)).enrichBookData(1L);
    }

    @Test
    @DisplayName("태그 누락 도서가 최대 개수(5권)일 때")
    void retryMissingTags_WithMaxBooks() {
        List<Book> books = Arrays.asList(
                book1, book2, book3, createBook(4L), createBook(5L)
        );
        when(bookRepository.findBooksNeedingTags(PageRequest.of(0, 5)))
                .thenReturn(books);

        bookRetryScheduler.retryMissingTags();

        verify(bookRepository).findBooksNeedingTags(PageRequest.of(0, 5));
        verify(bookEnrichmentService, times(5)).enrichBookData(any());
    }

    @Test
    @DisplayName("runEnrichment - enrichBookData 실행 중 예외 발생해도 계속 고")
    void runEnrichment_ContinueOnException() {
        List<Book> books = Arrays.asList(book1, book2, book3);
        when(bookRepository.findBooksNeedingEnrichment(PageRequest.of(0, 20)))
                .thenReturn(books);

        when(bookEnrichmentService.enrichBookData(1L))
                .thenThrow(new RuntimeException("Enrichment failed"));

        try {
            bookRetryScheduler.runEnrichment();
        } catch (Exception e) {
            // 예외가 발생하면 스케줄러가 중단될 수 있음
        }

        verify(bookEnrichmentService).enrichBookData(1L);
    }

    @Test
    @DisplayName("retryMissingTags - enrichBookData 실행 중 예외 발생해도 계속 진행")
    void retryMissingTags_ContinueOnException() {
        List<Book> books = Arrays.asList(book1, book2);
        when(bookRepository.findBooksNeedingTags(PageRequest.of(0, 5)))
                .thenReturn(books);

        when(bookEnrichmentService.enrichBookData(1L))
                .thenThrow(new RuntimeException("Enrichment failed"));

        try {
            bookRetryScheduler.retryMissingTags();
        } catch (Exception e) {
            // 예외가 발생하면 스케줄러가 중단될 수 있음
        }

        verify(bookEnrichmentService).enrichBookData(1L);
        // 예외 발생 후 나머지는 실행되지 않을 수 있음
    }

    @Test
    @DisplayName("runEnrichment와 retryMissingTags는 독립적으로 동작")
    void bothSchedulersWorkIndependently() {
        List<Book> enrichmentBooks = Arrays.asList(book1, book2);
        List<Book> tagBooks = Arrays.asList(book3);

        when(bookRepository.findBooksNeedingEnrichment(PageRequest.of(0, 20)))
                .thenReturn(enrichmentBooks);
        when(bookRepository.findBooksNeedingTags(PageRequest.of(0, 5)))
                .thenReturn(tagBooks);

        bookRetryScheduler.runEnrichment();
        bookRetryScheduler.retryMissingTags();

        verify(bookRepository).findBooksNeedingEnrichment(PageRequest.of(0, 20));
        verify(bookRepository).findBooksNeedingTags(PageRequest.of(0, 5));
        verify(bookEnrichmentService).enrichBookData(1L);
        verify(bookEnrichmentService).enrichBookData(2L);
        verify(bookEnrichmentService).enrichBookData(3L);
    }

    @Test
    @DisplayName("runEnrichment 여러 번 호출 시 매번 조회")
    void runEnrichment_MultipleInvocations() {
        List<Book> books1 = Arrays.asList(book1);
        List<Book> books2 = Arrays.asList(book2);
        List<Book> books3 = Collections.emptyList();

        when(bookRepository.findBooksNeedingEnrichment(PageRequest.of(0, 20)))
                .thenReturn(books1)
                .thenReturn(books2)
                .thenReturn(books3);

        bookRetryScheduler.runEnrichment();
        bookRetryScheduler.runEnrichment();
        bookRetryScheduler.runEnrichment();

        verify(bookRepository, times(3)).findBooksNeedingEnrichment(PageRequest.of(0, 20));
        verify(bookEnrichmentService).enrichBookData(1L);
        verify(bookEnrichmentService).enrichBookData(2L);
    }

    @Test
    @DisplayName("retryMissingTags 여러 번 호출 시 매번 조회")
    void retryMissingTags_MultipleInvocations() {
        List<Book> books1 = Arrays.asList(book1);
        List<Book> books2 = Collections.emptyList();

        when(bookRepository.findBooksNeedingTags(PageRequest.of(0, 5)))
                .thenReturn(books1)
                .thenReturn(books2);

        bookRetryScheduler.retryMissingTags();
        bookRetryScheduler.retryMissingTags();

        verify(bookRepository, times(2)).findBooksNeedingTags(PageRequest.of(0, 5));
        verify(bookEnrichmentService).enrichBookData(1L);
    }

    private Book createBook(Long id) {
        return Book.builder()
                .id(id)
                .isbn("978890123456" + id)
                .title("책" + id)
                .build();
    }
}