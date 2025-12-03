package org.nhnacademy.book2onandonbookservice.service.book;


import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Redis 직접 접근 관련 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookHistoryService {
    private final StringRedisTemplate redisTemplate;

    private static final String MEMBER_KEY_PREFIX = "book-service:history:view:member";
    private static final String GUEST_KEY_PREFIX = "book-service:history:view:guest";
    private static final int MAX_HISTORY_SIZE = 50;

    /**
     * 최근 본 도서 추가 (Redis ZSET)
     */
    public void addRecentView(Long userId, String guestId, Long bookId) {
        String key = getKey(userId, guestId);

        if (key == null || bookId == null) {
            return;
        }

        double score = System.currentTimeMillis();
        long ttlDays = (userId != null) ? 30 : 3;

        try {
            redisTemplate.opsForZSet()
                    .add(key, String.valueOf(bookId), score); //ZADD: 데이터 추가 (이미 있으면 score만 갱신되어 맨 뒤로 이동)
            redisTemplate.opsForZSet()
                    .removeRange(key, 0, -(MAX_HISTORY_SIZE + 1)); // 50개만 남기고 오래된건 삭제 (가장 오래된 것부터 -51까지 삭제)
            redisTemplate.expire(key, Duration.ofDays(ttlDays));
        } catch (Exception e) {
            log.error("최근 본 상품 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * 최근 본 도서 ID 목록 조회 (최신순)
     */
    public List<Long> getRecentViews(Long userId, String guestId) {
        String key = getKey(userId, guestId);

        Set<String> values = redisTemplate.opsForZSet().reverseRange(key, 0, MAX_HISTORY_SIZE - 1);

        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * 로그인 시 비회원 기록 -> 회원 기록으로 병합
     */
    public void mergeHistory(String guestId, Long userId) {
        String guestKey = GUEST_KEY_PREFIX + guestId;
        String memberKey = MEMBER_KEY_PREFIX + userId;

        redisTemplate.opsForZSet().unionAndStore(memberKey, guestKey, memberKey);
        redisTemplate.delete(guestKey);
        redisTemplate.opsForZSet().removeRange(memberKey, 0, -(MAX_HISTORY_SIZE + 1));

        redisTemplate.expire(memberKey, Duration.ofDays(50));
    }

    /**
     * Redis Key 생성 로직(회원/비회원)
     */

    private String getKey(Long userId, String guestId) {
        if (userId != null) {
            return MEMBER_KEY_PREFIX + userId;
        }
        if (StringUtils.hasText(guestId)) {
            return GUEST_KEY_PREFIX + guestId;
        }
        return null;
    }
}
