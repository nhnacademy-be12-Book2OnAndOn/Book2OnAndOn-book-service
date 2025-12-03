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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
        assertThat(result.likeCount()).isEqualTo(0L);

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
    @DisplayName("내가 좋아요한 책 ID 리스트 조회 (Redis 사용 안 함)")
    void getMyLikedBookIds() {
        Long userId = 10L;
        List<Long> ids = List.of(1L, 2L, 3L);

        // 이 메서드는 Redis를 안 쓰지만, setUp()에서 redisTemplate.opsForValue()가 호출되므로
        // strict stubbing 에러를 방지하기 위해 lenient() 설정이 있거나,
        // 아예 호출되지 않음을 확인하면 됨. (setUp의 given은 호출되어도 상관없음)

        when(bookLikeRepository.findBookIdsByUserId(userId)).thenReturn(ids);

        List<Long> result = bookLikeService.getMyLikedBookIds(userId);

        assertThat(result).containsExactly(1L, 2L, 3L);
        verify(bookLikeRepository).findBookIdsByUserId(userId);
    }
}