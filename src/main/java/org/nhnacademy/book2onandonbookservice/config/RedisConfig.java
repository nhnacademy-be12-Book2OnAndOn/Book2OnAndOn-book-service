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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Value("${spring.cache.redis.key-prefix:book-service}")
    private String keyPrefix;


    @Bean
    public RedisCacheManager RedisCacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // 커스텀 Deserializer 등록
        SimpleModule module = new SimpleModule();
        module.addDeserializer(PageRequest.class, new PageRequestDeserializer());
        module.addDeserializer(Sort.class, new SortDeserializer());
        objectMapper.registerModule(module);

        objectMapper.addMixIn(PageImpl.class, PageImplMixin.class);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        serializer))
                .computePrefixWith(cacheName -> keyPrefix + cacheName + "::")
                .entryTtl(Duration.ofDays(7))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("bestsellers", config.entryTtl(Duration.ofHours(12)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public abstract static class PageImplMixin<T> {

        @JsonCreator
        public PageImplMixin(
                @JsonProperty("content") List<T> content,
                @JsonProperty("pageable") Pageable pageable,
                @JsonProperty("totalElements") long totalElements
        ) {
        }
    }

    // PageRequest 커스텀 Deserializer
    public static class PageRequestDeserializer extends JsonDeserializer<PageRequest> {
        @Override
        public PageRequest deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            int pageNumber = node.has("pageNumber") ? node.get("pageNumber").asInt() : 0;
            int pageSize = node.has("pageSize") ? node.get("pageSize").asInt() : 10;

            // Sort 처리
            Sort sort = Sort.unsorted();
            if (node.has("sort") && !node.get("sort").isNull()) {
                JsonNode sortNode = node.get("sort");
                List<Sort.Order> orders = new ArrayList<>();

                if (sortNode.isArray()) {
                    for (JsonNode orderNode : sortNode) {
                        String property = orderNode.get("property").asText();
                        String direction = orderNode.has("direction") ?
                                orderNode.get("direction").asText() : "ASC";
                        orders.add(new Sort.Order(Sort.Direction.fromString(direction), property));
                    }
                } else if (sortNode.has("orders")) {
                    JsonNode ordersNode = sortNode.get("orders");
                    for (JsonNode orderNode : ordersNode) {
                        String property = orderNode.get("property").asText();
                        String direction = orderNode.has("direction") ?
                                orderNode.get("direction").asText() : "ASC";
                        orders.add(new Sort.Order(Sort.Direction.fromString(direction), property));
                    }
                }

                if (!orders.isEmpty()) {
                    sort = Sort.by(orders);
                }
            }

            return PageRequest.of(pageNumber, pageSize, sort);
        }
    }

    // Sort 커스텀 Deserializer
    public static class SortDeserializer extends JsonDeserializer<Sort> {
        @Override
        public Sort deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            if (node.isNull() || node.isEmpty()) {
                return Sort.unsorted();
            }

            List<Sort.Order> orders = new ArrayList<>();

            if (node.has("orders")) {
                JsonNode ordersNode = node.get("orders");
                for (JsonNode orderNode : ordersNode) {
                    String property = orderNode.get("property").asText();
                    String direction = orderNode.has("direction") ?
                            orderNode.get("direction").asText() : "ASC";
                    orders.add(new Sort.Order(Sort.Direction.fromString(direction), property));
                }
            }

            return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
        }
    }

}