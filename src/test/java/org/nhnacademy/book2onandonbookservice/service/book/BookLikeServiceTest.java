package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookLike;
import org.nhnacademy.book2onandonbookservice.repository.BookLikeRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;

@ExtendWith(MockitoExtension.class)
class BookLikeServiceTest {

    @Mock
    BookRepository bookRepository;

    @Mock
    BookLikeRepository bookLikeRepository;

    @InjectMocks
    BookLikeService bookLikeService;

    @Test
    @DisplayName("좋아요가 없으면 새로 등록하고 likeCount 증가")
    void toggleLike_whenNotExists_registerLike() {
        // given
        Long bookId = 1L;
        Long userId = 10L;

        Book book = mock(Book.class);
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(bookLikeRepository.existsByBookIdAndUserId(bookId, userId)).thenReturn(false);

        // increaseLikeCount() 호출 후 getLikeCount()가 1L을 리턴한다고 가정
        doNothing().when(book).increaseLikeCount();
        when(book.getLikeCount()).thenReturn(1L);

        BookLike savedLike = BookLike.builder()
                .book(book)
                .userId(userId)
                .build();
        when(bookLikeRepository.save(any(BookLike.class))).thenReturn(savedLike);

        // when
        BookLikeService.BookLikeToggleResult result = bookLikeService.toggleLike(bookId, userId);

        // then
        assertThat(result.liked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(1L);
        verify(bookRepository).findById(bookId);
        verify(bookLikeRepository).existsByBookIdAndUserId(bookId, userId);
        verify(bookLikeRepository).save(any(BookLike.class));
        verify(book).increaseLikeCount();
        verify(book, never()).decreaseLikeCount();
    }

    @Test
    @DisplayName("이미 좋아요가 있으면 취소하고 likeCount 감소")
    void toggleLike_whenExists_cancelLike() {
        // given
        Long bookId = 1L;
        Long userId = 10L;

        Book book = mock(Book.class);
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(bookLikeRepository.existsByBookIdAndUserId(bookId, userId)).thenReturn(true);

        doNothing().when(book).decreaseLikeCount();
        when(book.getLikeCount()).thenReturn(0L);

        // when
        BookLikeService.BookLikeToggleResult result = bookLikeService.toggleLike(bookId, userId);

        // then
        assertThat(result.liked()).isFalse();
        assertThat(result.likeCount()).isEqualTo(0L);
        verify(bookRepository).findById(bookId);
        verify(bookLikeRepository).existsByBookIdAndUserId(bookId, userId);
        verify(bookLikeRepository).deleteByBookIdAndUserId(bookId, userId);
        verify(book).decreaseLikeCount();
        verify(book, never()).increaseLikeCount();
    }

    @Test
    @DisplayName("내가 좋아요한 책 ID 리스트 조회")
    void getMyLikedBookIds() {
        // given
        Long userId = 10L;
        List<Long> ids = List.of(1L, 2L, 3L);
        when(bookLikeRepository.findBookIdsByUserId(userId)).thenReturn(ids);

        // when
        List<Long> result = bookLikeService.getMyLikedBookIds(userId);

        // then
        assertThat(result).containsExactly(1L, 2L, 3L);
        verify(bookLikeRepository).findBookIdsByUserId(userId);
    }
}