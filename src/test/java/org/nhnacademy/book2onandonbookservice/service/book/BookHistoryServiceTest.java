package org.nhnacademy.book2onandonbookservice.service.book;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class BookHistoryServiceTest {
    @InjectMocks
    private BookHistoryService bookHistoryService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private final Long userId = 1L;
    private final String guestId = "guest-uuid";
    private final Long bookId = 1L;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    @DisplayName("최근 본 도서 추가 - 회원 ttl 30일")
    void addRecentView_Member() {
        String key = "book-service:history:view:member" + userId;
        bookHistoryService.addRecentView(userId, null, bookId);

        verify(zSetOperations).add(eq(key), eq(String.valueOf(bookId)), anyDouble());
        verify(zSetOperations).removeRange(key, 0L, -51L);
        verify(stringRedisTemplate).expire(key, Duration.ofDays(30));
    }

    @Test
    @DisplayName("최근 본 도서 추가 - 비회원 ttl 3일")
    void addRecentView_Guest() {
        String key = "book-service:history:view:guest" + guestId;
        bookHistoryService.addRecentView(null, guestId, bookId);

        verify(zSetOperations).add(eq(key), eq(String.valueOf(bookId)), anyDouble());
        verify(stringRedisTemplate).expire(key, Duration.ofDays(3));
    }

    @Test
    @DisplayName("최근 본 도서 추가 User,Guest 둘다 없으면 무시")
    void addRecentView_NoGuest_NoMember() {
        bookHistoryService.addRecentView(null, null, bookId);
        bookHistoryService.addRecentView(null, "", bookId);

        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("최근 본 도서 추가 bookId가 없으면 무시")
    void addRecentView_NoBookId() {
        bookHistoryService.addRecentView(userId, null, null);

        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("최근 본 도서 추가 Redis 예외 발생")
    void addRecentView_ExceptionHandling() {
        given(zSetOperations.add(anyString(), anyString(), anyDouble()))
                .willThrow(new RuntimeException("Redis Connection Error"));

        assertThatCode(() -> bookHistoryService.addRecentView(userId, null, bookId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("최근 본 도서 조회 - 정상 반환 (최신순이어야됨)")
    void getRecentView() {
        String key = "book-service:history:view:member" + userId;

        Set<String> mockRange = new LinkedHashSet<>();
        mockRange.add("500");
        mockRange.add("501");

        given(zSetOperations.reverseRange(key, 0, 49)).willReturn(mockRange);
        List<Long> result = bookHistoryService.getRecentViews(userId, null);
        
        assertThat(result).hasSize(2).containsExactly(500L, 501L);
    }

    @Test
    @DisplayName("최근 본 도서 조회 - 키가 없거나 Redis결과가 없다면 빈리스트 반환")
    void getRecentViews_empty() {
        List<Long> result = bookHistoryService.getRecentViews(null, null);
        assertThat(result).isEmpty();

        String key = "book-service:history:view:member" + userId;
        given(zSetOperations.reverseRange(key, 0, 49)).willReturn(null);

        List<Long> result2 = bookHistoryService.getRecentViews(userId, null);
        assertThat(result2).isEmpty();

        given(zSetOperations.reverseRange(key, 0, 49)).willReturn(Collections.emptySet());
        List<Long> result3 = bookHistoryService.getRecentViews(userId, null);
        assertThat(result3).isEmpty();
    }

    @Test
    @DisplayName("로그인시 회원과 비회원 리스트 병합")
    void mergeHistory_Success() {
        String memberKey = "book-service:history:view:member" + userId;
        String guestKey = "book-service:history:view:guest" + guestId;

        bookHistoryService.mergeHistory(guestId, userId);

        verify(zSetOperations).unionAndStore(memberKey, guestKey, memberKey);

        verify(stringRedisTemplate).delete(guestKey);

        verify(zSetOperations).removeRange(memberKey, 0, -51);

        verify(stringRedisTemplate).expire(memberKey, Duration.ofDays(50));
    }
}