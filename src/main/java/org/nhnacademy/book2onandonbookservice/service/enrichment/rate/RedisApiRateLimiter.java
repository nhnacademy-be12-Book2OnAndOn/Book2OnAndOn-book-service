package org.nhnacademy.book2onandonbookservice.service.enrichment.rate;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisApiRateLimiter implements ApiRateLimiter{

    private final StringRedisTemplate redisTemplate;

    private static final int GROQ_LIMIT_PER_SEC=5;
    private static final int GEMINI_LIMIT_PER_MIN=20;

    @Override
    public boolean tryAcquireGroq() {
        String key = "rate:groq:"+ (System.currentTimeMillis() / 1000);
        return incrementWithinLimit(key, GROQ_LIMIT_PER_SEC, Duration.ofSeconds(2));
    }

    @Override
    public boolean tryAcquireGemini() {
        String key = "rate:gemini:"+(System.currentTimeMillis()/60000);
        return incrementWithinLimit(key, GEMINI_LIMIT_PER_MIN, Duration.ofSeconds(2));
    }

    private boolean incrementWithinLimit(String key, int limit, Duration ttl){
        Long count = redisTemplate.opsForValue().increment(key);
        if (count !=null && count==1L){
            redisTemplate.expire(key, ttl);
        }

        return count != null && count <= limit;
    }
}
