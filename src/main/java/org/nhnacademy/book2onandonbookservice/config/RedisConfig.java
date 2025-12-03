package org.nhnacademy.book2onandonbookservice.config;


import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.cache.redis.key-prefix:book-service}")
    private String keyPrefix;

    @Bean //Redis가 캐시매니저인걸 EnableCaching으로 알려줌 그럼 Cacheable 어노테이션이 붙은 메서드가 호출되면 자동으로 Redis에 데이터를 저장하고 조회함
    public RedisCacheManager RedisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer())) //키는 String 으로 직렬화
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer())) // 값은 Json으로 직렬화
                .computePrefixWith(cacheName -> keyPrefix + cacheName + "::") //모든 키 앞에 "book-service:"를 붙임
                .entryTtl(Duration.ofDays(7)) // 캐시 유효시간 (TTL) 기본 7일 설정 (책 정보는 잘 안 바뀌기때문에 길게 잡는 것이 좋음)
                .disableCachingNullValues();
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("bestsellers", config.entryTtl(Duration.ofHours(12)));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
