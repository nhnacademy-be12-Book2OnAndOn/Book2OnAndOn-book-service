package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BookServicePopularUnitTest {

    @Mock
    BookRepository bookRepository;

    @InjectMocks
    BookServiceImpl bookService;

    @Test
    @DisplayName("좋아요 순 인기 도서 조회 - Repository mock 기반 단위 테스트")
    void getPopularBooks_mockOnly() {
        // given
        Book b1 = Book.builder()
                .id(1L)
                .title("A")
                .isbn("111")
                .status(BookStatus.ON_SALE)
                .likeCount(35L)
                .build();

        Book b2 = Book.builder()
                .id(2L)
                .title("B")
                .isbn("222")
                .status(BookStatus.ON_SALE)
                .likeCount(10L)
                .build();

        Book b3 = Book.builder()
                .id(3L)
                .title("C")
                .isbn("333")
                .status(BookStatus.ON_SALE)
                .likeCount(50L)
                .build();

        List<Book> books = Arrays.asList(b3, b1, b2); // C(50) → A(35) → B(10)
        Pageable pageable = PageRequest.of(0, 10);
        Page<Book> bookPage = new PageImpl<>(books, pageable, books.size());

        // stubbing: 실제로 호출되는 메서드만!
        given(bookRepository.findByStatusOrderByLikeCountDesc(eq(BookStatus.ON_SALE), any(Pageable.class)))
                .willReturn(bookPage);

        Page<BookListResponse> result = bookService.getPopularBooks(pageable);

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("C");
        assertThat(result.getContent().get(1).getTitle()).isEqualTo("A");
        assertThat(result.getContent().get(2).getTitle()).isEqualTo("B");
    }
}