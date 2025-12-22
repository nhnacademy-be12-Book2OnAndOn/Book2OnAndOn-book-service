package org.nhnacademy.book2onandonbookservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class RedisConfigTest {

    private RedisConfig redisConfig;
    private ObjectMapper objectMapper;
    private final RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

    @BeforeEach
    void setUp() {
        redisConfig = new RedisConfig();
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(PageRequest.class, new RedisConfig.PageRequestDeserializer());
        module.addDeserializer(Sort.class, new RedisConfig.SortDeserializer());
        objectMapper.registerModule(module);
    }

    @Test
    void redisCacheManagers_Init() {
        RedisCacheManager defaultManager = redisConfig.redisCacheManager(connectionFactory);
        assertThat(defaultManager).isNotNull();
        assertThat(defaultManager.getCacheConfigurations()).isEmpty();

        RedisCacheManager bestsellersManager = redisConfig.bestsellersCacheManager(connectionFactory);
        assertThat(bestsellersManager).isNotNull();

        RedisCacheManager purchaseManager = redisConfig.purchaseHistoryCacheManager(connectionFactory);
        assertThat(purchaseManager).isNotNull();
    }

    @Test
    void deserialize_PageRequest_Default() throws JsonProcessingException {
        String json = "{}";

        PageRequest result = objectMapper.readValue(json, PageRequest.class);

        assertThat(result.getPageNumber()).isZero();
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getSort().isSorted()).isFalse();
    }

    @Test
    void deserialize_PageRequest_WithValues() throws JsonProcessingException {
        String json = "{\"pageNumber\": 5, \"pageSize\": 50}";

        PageRequest result = objectMapper.readValue(json, PageRequest.class);

        assertThat(result.getPageNumber()).isEqualTo(5);
        assertThat(result.getPageSize()).isEqualTo(50);
    }

    @Test
    void deserialize_PageRequest_WithSort_Null() throws JsonProcessingException {
        String json = "{\"sort\": null}";

        PageRequest result = objectMapper.readValue(json, PageRequest.class);

        assertThat(result.getSort().isSorted()).isFalse();
    }

    @Test
    void deserialize_PageRequest_WithSort_Array() throws JsonProcessingException {
        String json = "{" +
                "\"pageNumber\": 0, " +
                "\"pageSize\": 10, " +
                "\"sort\": [" +
                "  {\"property\": \"title\", \"direction\": \"DESC\"}, " +
                "  {\"property\": \"price\"}" +
                "]" +
                "}";

        PageRequest result = objectMapper.readValue(json, PageRequest.class);

        Sort sort = result.getSort();
        assertThat(sort.getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("price").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void deserialize_PageRequest_WithSort_OrdersObject() throws JsonProcessingException {
        String json = "{" +
                "\"sort\": {" +
                "  \"orders\": [" +
                "    {\"property\": \"createdDate\", \"direction\": \"ASC\"}" +
                "  ]" +
                "}" +
                "}";

        PageRequest result = objectMapper.readValue(json, PageRequest.class);

        assertThat(result.getSort().getOrderFor("createdDate").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void deserialize_PageRequest_WithSort_EmptyOrders() throws JsonProcessingException {
        String json = "{\"sort\": {\"orders\": []}}";

        PageRequest result = objectMapper.readValue(json, PageRequest.class);

        assertThat(result.getSort().isSorted()).isFalse();
    }

    @Test
    void deserialize_Sort_Null() throws JsonProcessingException {
        String json = "null";

        Sort result = objectMapper.readValue(json, Sort.class);

        assertThat(result).isNull();
    }

    @Test
    void deserialize_Sort_EmptyObject() throws JsonProcessingException {
        String json = "{}";

        Sort result = objectMapper.readValue(json, Sort.class);

        assertThat(result.isSorted()).isFalse();
    }

    @Test
    void deserialize_Sort_WithOrders() throws JsonProcessingException {
        String json = "{" +
                "\"orders\": [" +
                "  {\"property\": \"name\", \"direction\": \"DESC\"}" +
                "]" +
                "}";

        Sort result = objectMapper.readValue(json, Sort.class);

        assertThat(result.getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void deserialize_Sort_WithOrders_DefaultDirection() throws JsonProcessingException {
        String json = "{" +
                "\"orders\": [" +
                "  {\"property\": \"viewCount\"}" +
                "]" +
                "}";

        Sort result = objectMapper.readValue(json, Sort.class);

        assertThat(result.getOrderFor("viewCount").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}