package org.nhnacademy.book2onandonbookservice.service;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.repository.BatchInsertRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;

@ExtendWith(MockitoExtension.class)
class BookBatchServiceTest {

    @Mock
    private BatchInsertRepository batchInsertRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookBatchService bookBatchService;

    @Test
    @DisplayName("배치 저장 성공 - 정상 데이터")
    void saveBooksInBatch_Success() {

        Book book1 = Book.builder().isbn("111").title("Book 1").build();
        BookContributor bc1 = BookContributor.builder()
                .contributor(Contributor.builder().id(10L).build())
                .roleType("AUTHOR")
                .build();
        BookPublisher bp1 = BookPublisher.builder()
                .publisher(Publisher.builder().id(20L).build())
                .build();
        BookImage bi1 = BookImage.builder()
                .imagePath("path/to/img1.jpg")
                .build();

        book1.getBookContributors().add(bc1);
        book1.getBookPublishers().add(bp1);
        book1.getImages().add(bi1);

        Book book2 = Book.builder().isbn("222").title("Book 2").build();

        List<Book> books = List.of(book1, book2);

        List<BookRepository.BookIdAndIsbn> idMappings = new ArrayList<>();
        idMappings.add(createBookIdAndIsbn(1L, "111"));
        idMappings.add(createBookIdAndIsbn(2L, "222"));

        given(bookRepository.findByIsbnIn(anyList())).willReturn(idMappings);

        bookBatchService.saveBooksInBatch(books);

        verify(batchInsertRepository, times(1)).saveAllBooks(books);

        verify(bookRepository, times(1)).findByIsbnIn(anyList());

        verify(batchInsertRepository, times(1)).saveBookImages(anyList());
        verify(batchInsertRepository, times(1)).saveBookRelations(anyList(), anyList());
    }

    @Test
    @DisplayName("배치 저장 - 빈 리스트 입력 시 중단")
    void saveBooksInBatch_EmptyList() {
        List<Book> emptyList = Collections.emptyList();

        bookBatchService.saveBooksInBatch(emptyList);

        verify(batchInsertRepository, never()).saveAllBooks(anyList());
        verify(bookRepository, never()).findByIsbnIn(anyList());
    }

    @Test
    @DisplayName("배치 저장 - ID 조회 실패 시 연관관계 저장 스킵")
    void saveBooksInBatch_IdMappingFail() {
        Book book1 = Book.builder().isbn("111").title("Book 1").build();
        BookContributor bc1 = BookContributor.builder()
                .contributor(Contributor.builder().id(10L).build())
                .roleType("AUTHOR")
                .build();
        book1.getBookContributors().add(bc1);

        List<Book> books = List.of(book1);

        given(bookRepository.findByIsbnIn(anyList())).willReturn(Collections.emptyList());

        bookBatchService.saveBooksInBatch(books);

        verify(batchInsertRepository, times(1)).saveAllBooks(books);

        verify(batchInsertRepository).saveBookImages(Collections.emptyList());
        verify(batchInsertRepository).saveBookRelations(Collections.emptyList(), Collections.emptyList());
    }

    private BookRepository.BookIdAndIsbn createBookIdAndIsbn(Long id, String isbn) {
        return new BookRepository.BookIdAndIsbn() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getIsbn() {
                return isbn;
            }
        };
    }
}