package org.nhnacademy.book2onandonbookservice.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class RedisConfig {

    private static final String PROPERTY = "property";
    private static final String DIRECTION = "direction";
    private static final String SORT = "sort";
    private static final String ORDERS = "orders";
    private static final String PAGE_NUMBER = "pageNumber";
    private static final String PAGE_SIZE = "pageSize";
    private static final String ASC = "ASC";

    // 1. 공통 설정 (Serializer 등)
    private RedisCacheConfiguration createBaseConfig() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        SimpleModule module = new SimpleModule();
        module.addDeserializer(PageRequest.class, new PageRequestDeserializer());
        module.addDeserializer(Sort.class, new SortDeserializer());
        objectMapper.registerModule(module);
        objectMapper.addMixIn(PageImpl.class, PageImplMixin.class);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();
    }

    // 2. [기본] 캐시 매니저 (TTL: 7일) - @Primary
    @Primary
    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = createBaseConfig().entryTtl(Duration.ofDays(7));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    // 3. [베스트셀러용] 캐시 매니저 (TTL: 12시간)
    @Bean
    public RedisCacheManager bestsellersCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = createBaseConfig().entryTtl(Duration.ofHours(12));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    // 4. [구매내역용] 캐시 매니저 (TTL: 90일)
    @Bean
    public RedisCacheManager purchaseHistoryCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = createBaseConfig().entryTtl(Duration.ofDays(90));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    // --- 내부 클래스 (기존 유지) ---
    @JsonIgnoreProperties(ignoreUnknown = true)
    public abstract static class PageImplMixin<T> {
        @JsonCreator
        protected PageImplMixin(@JsonProperty("content") List<T> content, @JsonProperty("pageable") Pageable pageable, @JsonProperty("totalElements") long totalElements) {}
    }

    public static class PageRequestDeserializer extends JsonDeserializer<PageRequest> {
        @Override
        public PageRequest deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            int pageNumber = node.has(PAGE_NUMBER) ? node.get(PAGE_NUMBER).asInt() : 0;
            int pageSize = node.has(PAGE_SIZE) ? node.get(PAGE_SIZE).asInt() : 10;
            Sort sort = parseSort(node);
            return PageRequest.of(pageNumber, pageSize, sort);
        }
        private Sort parseSort(JsonNode node) {
            if (!node.has(SORT) || node.get(SORT).isNull()) return Sort.unsorted();
            JsonNode sortNode = node.get(SORT);
            List<Sort.Order> orders = new ArrayList<>();
            if (sortNode.isArray()) { for (JsonNode orderNode : sortNode) orders.add(parseOrder(orderNode)); }
            else if (sortNode.has(ORDERS)) { for (JsonNode orderNode : sortNode.get(ORDERS)) orders.add(parseOrder(orderNode)); }
            return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
        }
        private Sort.Order parseOrder(JsonNode orderNode) {
            String property = orderNode.has(PROPERTY) ? orderNode.get(PROPERTY).asText() : "";
            String direction = orderNode.has(DIRECTION) ? orderNode.get(DIRECTION).asText() : ASC;
            return new Sort.Order(Sort.Direction.fromString(direction), property);
        }
    }

    public static class SortDeserializer extends JsonDeserializer<Sort> {
        @Override
        public Sort deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node.isNull() || node.isEmpty()) return Sort.unsorted();
            List<Sort.Order> orders = new ArrayList<>();
            if (node.has(ORDERS)) { for (JsonNode orderNode : node.get(ORDERS)) {
                String property = orderNode.has(PROPERTY) ? orderNode.get(PROPERTY).asText() : "";
                String direction = orderNode.has(DIRECTION) ? orderNode.get(DIRECTION).asText() : ASC;
                orders.add(new Sort.Order(Sort.Direction.fromString(direction), property));
            }}
            return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
        }
    }
}