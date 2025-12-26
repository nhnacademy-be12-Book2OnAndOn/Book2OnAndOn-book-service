package org.nhnacademy.book2onandonbookservice.service.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.service.enrichment.rate.RedisApiRateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisApiRateLimiterTest {

    @InjectMocks
    private RedisApiRateLimiter rateLimiter;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("tryAcquireGroq: 처음 요청 시 성공하고 만료 시간 설정됨")
    void tryAcquireGroq_FirstRequest_Success() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        boolean result = rateLimiter.tryAcquireGroq();

        assertThat(result).isTrue();
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("tryAcquireGroq: 한도 내 요청 시 성공하고 만료 시간 설정되지 않음")
    void tryAcquireGroq_WithinLimit_Success() {
        when(valueOperations.increment(anyString())).thenReturn(5L);

        boolean result = rateLimiter.tryAcquireGroq();

        assertThat(result).isTrue();
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("tryAcquireGroq: 한도 초과 시 실패")
    void tryAcquireGroq_ExceededLimit_Fail() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        boolean result = rateLimiter.tryAcquireGroq();

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("tryAcquireGemini: 처음 요청 시 성공하고 만료 시간 설정됨")
    void tryAcquireGemini_FirstRequest_Success() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        boolean result = rateLimiter.tryAcquireGemini();

        assertThat(result).isTrue();
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("tryAcquireGemini: 한도 내 요청 시 성공")
    void tryAcquireGemini_WithinLimit_Success() {
        when(valueOperations.increment(anyString())).thenReturn(20L);

        boolean result = rateLimiter.tryAcquireGemini();

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("tryAcquireGemini: 한도 초과 시 실패")
    void tryAcquireGemini_ExceededLimit_Fail() {
        when(valueOperations.increment(anyString())).thenReturn(21L);

        boolean result = rateLimiter.tryAcquireGemini();

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Redis 응답이 null인 경우 실패 처리")
    void tryAcquire_RedisReturnNull_Fail() {
        when(valueOperations.increment(anyString())).thenReturn(null);

        boolean result = rateLimiter.tryAcquireGroq();

        assertThat(result).isFalse();
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }
}