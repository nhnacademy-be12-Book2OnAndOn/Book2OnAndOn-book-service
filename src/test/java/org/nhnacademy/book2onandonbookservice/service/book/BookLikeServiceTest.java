package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.api.RestPage;
import org.nhnacademy.book2onandonbookservice.dto.book.MyLikedBookResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookLike;
import org.nhnacademy.book2onandonbookservice.repository.BookLikeRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class BookLikeServiceTest {

    @InjectMocks
    BookLikeService bookLikeService;

    @Mock
    BookRepository bookRepository;

    @Mock
    BookLikeRepository bookLikeRepository;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        // RedisTemplate이 opsForValue()를 호출할 때 Mock 객체(valueOperations)를 반환하도록 설정
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    }

    @Test
    @DisplayName("좋아요 등록 성공 - 락 획득 -> DB 반영 -> 락 해제")
    void toggleLike_whenNotExists_registerLike() {
        Long bookId = 1L;
        Long userId = 10L;
        String lockKey = "book-service:lock:like:" + userId + ":" + bookId;

        given(valueOperations.setIfAbsent(eq(lockKey), anyString(), any(Duration.class))).willReturn(true);

        Book book = mock(Book.class);
        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(bookLikeRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(false);

        doNothing().when(book).increaseLikeCount();
        given(book.getLikeCount()).willReturn(1L);

        BookLike savedLike = BookLike.builder().book(book).userId(userId).build();
        given(bookLikeRepository.save(any(BookLike.class))).willReturn(savedLike);

        BookLikeService.BookLikeToggleResult result = bookLikeService.toggleLike(bookId, userId);

        assertThat(result.liked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(1L);

        verify(valueOperations).setIfAbsent(eq(lockKey), eq("LOCKED"), any(Duration.class)); // 락 시도 확인
        verify(bookRepository).findById(bookId);
        verify(bookLikeRepository).save(any(BookLike.class));
        verify(redisTemplate).delete(lockKey); // finally 블록에서 락 해제 확인
    }

    @Test
    @DisplayName("좋아요 취소 성공 - 락 획득 -> DB 삭제 -> 락 해제")
    void toggleLike_whenExists_cancelLike() {
        Long bookId = 1L;
        Long userId = 10L;
        String lockKey = "book-service:lock:like:" + userId + ":" + bookId;

        given(valueOperations.setIfAbsent(eq(lockKey), anyString(), any(Duration.class))).willReturn(true);

        Book book = mock(Book.class);
        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(bookLikeRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(true);

        doNothing().when(book).decreaseLikeCount();
        given(book.getLikeCount()).willReturn(0L);

        BookLikeService.BookLikeToggleResult result = bookLikeService.toggleLike(bookId, userId);

        assertThat(result.liked()).isFalse();
        assertThat(result.likeCount()).isZero();

        verify(bookLikeRepository).deleteByBookIdAndUserId(bookId, userId);
        verify(redisTemplate).delete(lockKey); // 락 해제 확인
    }

    @Test
    @DisplayName("좋아요 실패 - 이미 처리 중인 요청 (Redis 락 획득 실패)")
    void toggleLike_Fail_AlreadyLocked() {
        Long bookId = 1L;
        Long userId = 10L;
        String lockKey = "book-service:lock:like:" + userId + ":" + bookId;

        given(valueOperations.setIfAbsent(eq(lockKey), anyString(), any(Duration.class))).willReturn(false);

        assertThatThrownBy(() -> bookLikeService.toggleLike(bookId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 처리 중인 요청입니다");

        verify(bookRepository, never()).findById(anyLong());
        verify(redisTemplate, never()).delete(lockKey); // 락 획득 실패했으므로 해제도 안 함
    }

    @Test
    @DisplayName("좋아요 실패 - 도서 없음 (예외 발생 시에도 락 해제 보장)")
    void toggleLike_Fail_BookNotFound_EnsureUnlock() {
        Long bookId = 999L;
        Long userId = 10L;
        String lockKey = "book-service:lock:like:" + userId + ":" + bookId;

        given(valueOperations.setIfAbsent(eq(lockKey), anyString(), any(Duration.class))).willReturn(true);

        given(bookRepository.findById(bookId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookLikeService.toggleLike(bookId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("도서를 찾을 수 없습니다");

        verify(redisTemplate).delete(lockKey);
    }

    @Test
    @DisplayName("내가 좋아요한 책 리스트 조회 (Redis 사용 안 함)")
    void getMyLikedBooks() {
        Long userId = 10L;
        Pageable pageable = PageRequest.of(0, 10);

        Book book = Book.builder()
                .id(1L)
                .title("테스트 책")
                .priceSales(15000L)
                .images(new HashSet<>())
                .bookContributors(new HashSet<>())
                .bookPublishers(new HashSet<>())
                .bookTags(new HashSet<>())
                .build();

        BookLike bookLike = BookLike.builder()
                .id(100L)
                .userId(userId)
                .book(book)
                .createdAt(LocalDateTime.now()) //좋아요 생성시간
                .build();

        List<BookLike> likeList = List.of(bookLike);
        Page<BookLike> likePage = new PageImpl<>(likeList, pageable, likeList.size());

        when(bookLikeRepository.findAllByUserId(userId, pageable)).thenReturn(likePage);

        RestPage<MyLikedBookResponse> result = bookLikeService.getMyLikedBookIds(userId, pageable);

        assertThat(result.getContent()).hasSize(1);

        MyLikedBookResponse response = result.getContent().get(0);
        assertThat(response.getBookLikeId()).isEqualTo(100L);
        assertThat(response.getBookInfo().getId()).isEqualTo(1L);
        assertThat(response.getBookInfo().getTitle()).isEqualTo("테스트 책");

        verify(bookLikeRepository).findAllByUserId(userId, pageable);
    }
}