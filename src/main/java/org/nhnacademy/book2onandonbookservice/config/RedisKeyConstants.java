package org.nhnacademy.book2onandonbookservice.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisKeyConstants {
    public static final String STOCK_PREFIX = "book:stock:";
    public static final String RESERVE_HASH_PREFIX = "book:reserved_order:";
    public static final String PROCESSED_KEY_PREFIX = "book:processed:";
    public static final String EMBEDDING_CACHE_PREFIX = "search:embedding:";
}
