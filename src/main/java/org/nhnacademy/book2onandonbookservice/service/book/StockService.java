package org.nhnacademy.book2onandonbookservice.service.book;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.exception.OutOfStockException;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {
    private final StringRedisTemplate redisTemplate;
    private final BookRepository bookRepository;

    private static final String STOCK_KEY_PREFIX = "book:stock:";
    private static final String RESERVE_KEY_PREFIX = "book:reserved:";
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(10); // 적절한 선점 시간

    // Lua Script: 원자적 재고 차감
    private static final String DECREASE_STOCK_SCRIPT =
            "if redis.call('exists', KEYS[1]) == 0 then return -1 end " +
                    "local current = tonumber(redis.call('get', KEYS[1])) " +
                    "if current < tonumber(ARGV[1]) then return 1 end " +
                    "redis.call('decrby', KEYS[1], ARGV[1]) " +
                    "return 0";

    // 1. 재고 선점
    public void decreaseStock(StockRequest request) {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        String orderId = request.getOrderNumber();
        List<StockRequest.StockItem> items = request.getBookInfoDtoList();

        for (StockRequest.StockItem req : items) {
            String stockKey = STOCK_KEY_PREFIX + req.getBookId();
            String reserveKey = RESERVE_KEY_PREFIX +  orderId + ":" + req.getBookId();

            if (Boolean.TRUE.equals(redisTemplate.hasKey(reserveKey))) {
                continue; // 이미 처리된 건
            }

            Long result = executeDecreaseScript(stockKey, req.getQuantity());

            if (result != null && result == -1) { // 키 없음 -> DB 로딩 후 재시도
                initializeStockFromDb(req.getBookId());
                result = executeDecreaseScript(stockKey, req.getQuantity());
            }

            if (result == null || result == 1) { // 재고 부족
                throw new OutOfStockException("재고 부족 BookId: " + req.getBookId());
            }

            ops.set(reserveKey, String.valueOf(req.getQuantity()), RESERVATION_TTL);
        }
    }

    // 2. 재고 확정 (RabbitMQ/Feign 둘 다 여기를 호출)
    @Transactional
    public void confirmStock(String orderNumber) {
        // 해당 주문번호로 잡힌 모든 예약 키 검색 (예: book:reserved:ORDER-001:*)
        Set<String> keys = redisTemplate.keys(RESERVE_KEY_PREFIX + orderNumber + ":*");

        if (keys == null || keys.isEmpty()) {
            // Redis 키가 만료되어 없으면 어떤 책을 샀는지 알 수 없음 -> 처리 불가 (Critical)
            log.error("[Critical] 재고 확정 실패 - 예약 정보 만료됨 (복구 불가). OrderNumber: {}", orderNumber);
            return;
        }

        for (String key : keys) {
            Long bookId = extractBookIdFromKey(key);
            String qtyStr = redisTemplate.opsForValue().get(key);

            // 중복 처리 방지용 키 (Idempotency)
            String processedKey = "book:processed:" + orderNumber + ":" + bookId;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(processedKey))) {
                continue; // 이미 처리됨
            }

            if (qtyStr != null) {
                int quantity = Integer.parseInt(qtyStr);

                // DB 차감
                int updateCount = bookRepository.decreaseStock(bookId, quantity);
                if (updateCount == 0) {
                    throw new OutOfStockException("DB 재고 부족 (데이터 불일치). BookId: " + bookId);
                }

                // 품절 처리
                Book book = bookRepository.findById(bookId)
                        .orElseThrow(() -> new NotFoundBookException(bookId));
                if (book.getStockCount() <= 0) {
                    book.setStatus(BookStatus.SOLD_OUT);
                }

                // 처리 완료 마킹 (24시간 유지)
                redisTemplate.opsForValue().set(processedKey, "DONE", Duration.ofDays(1));
            }

            // 예약 키 삭제
            redisTemplate.delete(key);
        }
    }

    public void increaseStock(Long bookId, int quantity){
        String stockKey = STOCK_KEY_PREFIX + bookId;

        if(Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))){
            redisTemplate.opsForValue().increment(stockKey, quantity);
        }
    }

    // 3. 재고 취소
    public void cancelStock(String orderNumber) {
        Set<String> keys = redisTemplate.keys(RESERVE_KEY_PREFIX + orderNumber + ":*");

        if (keys == null || keys.isEmpty()) {
            log.info("취소할 예약 정보가 없습니다 (이미 만료됨/처리됨). OrderNumber: {}", orderNumber);
            return;
        }

        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        for (String key : keys) {
            Long bookId = extractBookIdFromKey(key);
            String stockKey = STOCK_KEY_PREFIX + bookId;
            String qtyStr = ops.get(key);

            if (qtyStr != null) {
                // Redis 재고 원복 (Rollback)
                ops.increment(stockKey, Integer.parseInt(qtyStr));
            }
            // 예약 키 삭제
            redisTemplate.delete(key);
        }
    }

    public void synchronizeStock(Long bookId){
        String stockKey = STOCK_KEY_PREFIX + bookId;
        String reservePattern = RESERVE_KEY_PREFIX + "*:" + bookId;

        boolean hasActiveReservation = hasKeyPattern(reservePattern);

        if (hasActiveReservation) {
            log.warn("[Admin] 동기화 실패 - 현재 주문 진행 중인 건이 존재함. BookId: {}", bookId);
            throw new IllegalStateException("현재 주문이 진행 중인 도서입니다. 잠시 후 다시 시도해주세요.");
        }

        Integer dbStock = bookRepository.findStockCountById(bookId);
        if(dbStock == null){
            throw new NotFoundBookException(bookId);
        }

        redisTemplate.opsForValue().set(stockKey, String.valueOf(dbStock));

        log.info("[Admin] 재고 동기화 완료 - BookId: {}, DB Stock: {}", bookId, dbStock);
    }

    private Long extractBookIdFromKey(String key) {
        try {
            // "book:reserved:ORDER-001:55" -> 55 추출
            String[] parts = key.split(":");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            log.error("키 파싱 실패: {}", key);
            return 0L;
        }
    }

    private Long executeDecreaseScript(String key, int quantity) {
        return redisTemplate.execute(
                RedisScript.of(DECREASE_STOCK_SCRIPT, Long.class),
                Collections.singletonList(key),
                String.valueOf(quantity)
        );
    }

    private void initializeStockFromDb(Long bookId) {
        String stockKey = STOCK_KEY_PREFIX + bookId;
        Integer dbStock = bookRepository.findStockCountById(bookId);

        if (dbStock == null) {
            throw new OutOfStockException("존재하지 않는 도서 ID: " + bookId);
        }
        redisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(dbStock));
    }

    private boolean hasKeyPattern(String pattern){
        return Boolean.TRUE.equals(redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(10).build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                // 하나라도 있으면 true 반환
                return cursor.hasNext();
            } catch (Exception e) {
                return false;
            }
        }));
    }
}