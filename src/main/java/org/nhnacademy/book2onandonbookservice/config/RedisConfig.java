package org.nhnacademy.book2onandonbookservice.config;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        objectMapper.addMixIn(PageImpl.class, PageImplMixin.class);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer())) //키는 String 으로 직렬화
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        serializer)) // 값은 Json으로 직렬화
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

    @JsonIgnoreProperties(ignoreUnknown = true, value = {"pageable"})
    public abstract static class PageImplMixin<T> {
        @JsonCreator
        protected PageImplMixin(@JsonProperty("content") List<T> content,
                                @JsonProperty("pageable") Pageable pageable,
                                @JsonProperty("totalElements") long totalElements) {

        }
    }
}
