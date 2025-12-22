package org.nhnacademy.book2onandonbookservice.service.book;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.exception.OutOfStockException;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
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
    public void decreaseStock(List<StockRequest> reqs) {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        for (StockRequest req : reqs) {
            String stockKey = STOCK_KEY_PREFIX + req.getBookId();
            String reserveKey = RESERVE_KEY_PREFIX + req.getOrderId() + ":" + req.getBookId();

            if (Boolean.TRUE.equals(redisTemplate.hasKey(reserveKey))) {
                continue; // 이미 처리된 건
            }

            Long result = executeDecreaseScript(stockKey, req.getQuantity());

            if (result == -1) { // 키 없음 -> DB 로딩 후 재시도
                initializeStockFromDb(req.getBookId());
                result = executeDecreaseScript(stockKey, req.getQuantity());
            }

            if (result == 1) { // 재고 부족
                throw new OutOfStockException("재고 부족 BookId: " + req.getBookId());
            }

            ops.set(reserveKey, String.valueOf(req.getQuantity()), RESERVATION_TTL);
        }
    }

    // 2. 재고 확정 (RabbitMQ/Feign 둘 다 여기를 호출)
    @Transactional
    public void confirmStock(List<StockRequest> requests) {
        for (StockRequest req : requests) {
            String reserveKey = RESERVE_KEY_PREFIX + req.getOrderId() + ":" + req.getBookId();

            // ★ 중요: Redis 키가 없어도(만료됐어도) 결제 성공이면 DB 무조건 깐다.
            if (Boolean.FALSE.equals(redisTemplate.hasKey(reserveKey))) {
                log.warn("[Risk] 예약 키 만료/유실됨. DB 강제 차감 시도. OrderId: {}", req.getOrderId());
            }

            int updateCount = bookRepository.decreaseStock(req.getBookId(), req.getQuantity());

            if (updateCount == 0) {
                // DB에도 재고가 없다? -> 심각한 오류 (환불 필요)
                log.error("[Critical] 결제 완료됐으나 DB 재고 부족! 환불 필요. BookId: {}", req.getBookId());
                throw new OutOfStockException("재고 불일치 발생 (관리자 확인 필요) BookId: " + req.getBookId());
            }

            // 품절 처리
            Book book = bookRepository.findById(req.getBookId())
                    .orElseThrow(() -> new NotFoundBookException(req.getBookId()));
            if (book.getStockCount() <= 0) {
                book.setStatus(BookStatus.SOLD_OUT);
            }

            redisTemplate.delete(reserveKey);
        }
    }

    // 3. 재고 취소
    public void cancelStock(List<StockRequest> requests) {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        for (StockRequest req : requests) {
            String stockKey = STOCK_KEY_PREFIX + req.getBookId();
            String reserveKey = RESERVE_KEY_PREFIX + req.getOrderId() + ":" + req.getBookId();

            if (Boolean.TRUE.equals(redisTemplate.delete(reserveKey))) {
                ops.increment(stockKey, req.getQuantity());
            }
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
}