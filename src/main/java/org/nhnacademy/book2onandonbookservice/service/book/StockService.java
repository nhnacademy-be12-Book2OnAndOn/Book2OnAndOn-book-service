package org.nhnacademy.book2onandonbookservice.service.book;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.config.RedisKeyConstants;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookStockReservation;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.exception.OutOfStockException;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookStockReservationRepository;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {
    private final StringRedisTemplate redisTemplate;
    private final BookRepository bookRepository;
    private final BookStockReservationRepository reservationRepository;

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(10);
    private static final String DONE = "DONE";

    // Lua Script: 원자적 재고 차감 및 Hash에 예약 정보 저장
    private static final String DECREASE_STOCK_AND_RESERVE_SCRIPT =
            "if redis.call('exists', KEYS[1]) == 0 then return -1 end " +
            "local current = tonumber(redis.call('get', KEYS[1])) " +
            "if current < tonumber(ARGV[1]) then return 1 end " +
            "redis.call('decrby', KEYS[1], ARGV[1]) " +
            "redis.call('hset', KEYS[2], ARGV[2], ARGV[1]) " +
            "redis.call('expire', KEYS[2], ARGV[3]) " +
            "return 0";

    // 1. 재고 선점
    @Transactional
    public void decreaseStock(StockRequest request) {
        String orderId = request.getOrderNumber();
        List<StockRequest.StockItem> items = request.getBookInfoDtoList();
        String reserveHashKey = RedisKeyConstants.RESERVE_HASH_PREFIX + orderId;

        for (StockRequest.StockItem req : items) {
            String stockKey = RedisKeyConstants.STOCK_PREFIX + req.getBookId();
            String bookIdStr = String.valueOf(req.getBookId());

            // 1. Redis에서 이미 선점되었는지 확인 (멱등성)
            if (Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(reserveHashKey, bookIdStr))) {
                continue;
            }

            // 2. Lua Script 실행
            Long result = executeDecreaseAndReserveScript(stockKey, reserveHashKey, bookIdStr, req.getQuantity());

            if (result != null && result == -1) { // 키 없음 -> DB 로딩 후 재시도
                initializeStockFromDb(req.getBookId());
                result = executeDecreaseAndReserveScript(stockKey, reserveHashKey, bookIdStr, req.getQuantity());
            }

            if (result == null || result == 1) { // 재고 부족
                throw new OutOfStockException("재고 부족 BookId: " + req.getBookId());
            }

            // 3. DB에 예약 정보 저장 (Redis 만료 시 대비 Fallback)
            reservationRepository.save(BookStockReservation.builder()
                    .orderNumber(orderId)
                    .bookId(req.getBookId())
                    .quantity(req.getQuantity())
                    .status(BookStockReservation.ReservationStatus.RESERVED)
                    .build());
        }
    }

    // 2. 재고 확정
    @Transactional
    public void confirmStock(String orderNumber) {
        String reserveHashKey = RedisKeyConstants.RESERVE_HASH_PREFIX + orderNumber;
        Map<Object, Object> reservations = redisTemplate.opsForHash().entries(reserveHashKey);

        if (reservations.isEmpty()) {
            // Redis 만료 시 DB에서 조회 (Fallback)
            log.info("Redis 예약 정보 만료됨. DB에서 복구 시도. OrderNumber: {}", orderNumber);
            processConfirmFromDb(orderNumber);
            return;
        }

        for (Map.Entry<Object, Object> entry : reservations.entrySet()) {
            Long bookId = Long.parseLong(entry.getKey().toString());
            int quantity = Integer.parseInt(entry.getValue().toString());

            confirmBookStock(orderNumber, bookId, quantity);
        }

        // Redis 예약 데이터 삭제
        redisTemplate.delete(reserveHashKey);
    }

    private void processConfirmFromDb(String orderNumber) {
        List<BookStockReservation> dbReservations = reservationRepository.findByOrderNumberAndStatus(
                orderNumber, BookStockReservation.ReservationStatus.RESERVED);

        if (dbReservations.isEmpty()) {
            log.warn("확정할 예약 정보가 없습니다 (이미 처리됨/만료됨). OrderNumber: {}", orderNumber);
            return;
        }

        for (BookStockReservation res : dbReservations) {
            confirmBookStock(orderNumber, res.getBookId(), res.getQuantity());
        }
    }

    private void confirmBookStock(String orderNumber, Long bookId, int quantity) {
        // 중복 처리 방지용 키 (Idempotency)
        String processedKey = RedisKeyConstants.PROCESSED_KEY_PREFIX + orderNumber + ":" + bookId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(processedKey))) {
            return;
        }

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

        // DB 예약 상태 업데이트
        reservationRepository.findByOrderNumberAndStatus(orderNumber, BookStockReservation.ReservationStatus.RESERVED)
                .stream()
                .filter(r -> r.getBookId().equals(bookId))
                .forEach(r -> r.setStatus(BookStockReservation.ReservationStatus.CONFIRMED));

        // 처리 완료 마킹 (24시간 유지)
        redisTemplate.opsForValue().set(processedKey, DONE, Duration.ofDays(1));
    }

    // 3. 재고 취소
    @Transactional
    public void cancelStock(String orderNumber) {
        String reserveHashKey = RedisKeyConstants.RESERVE_HASH_PREFIX + orderNumber;
        Map<Object, Object> reservations = redisTemplate.opsForHash().entries(reserveHashKey);

        if (reservations.isEmpty()) {
            log.info("Redis 예약 정보 만료됨. DB에서 취소 시도. OrderNumber: {}", orderNumber);
            processCancelFromDb(orderNumber);
            return;
        }

        for (Map.Entry<Object, Object> entry : reservations.entrySet()) {
            Long bookId = Long.parseLong(entry.getKey().toString());
            int quantity = Integer.parseInt(entry.getValue().toString());

            cancelBookStock(orderNumber, bookId, quantity);
        }

        redisTemplate.delete(reserveHashKey);
    }

    private void processCancelFromDb(String orderNumber) {
        List<BookStockReservation> dbReservations = reservationRepository.findByOrderNumberAndStatus(
                orderNumber, BookStockReservation.ReservationStatus.RESERVED);

        if (dbReservations.isEmpty()) {
            log.info("취소할 예약 정보가 없습니다 (이미 처리됨). OrderNumber: {}", orderNumber);
            return;
        }

        for (BookStockReservation res : dbReservations) {
            cancelBookStock(orderNumber, res.getBookId(), res.getQuantity());
        }
    }

    private void cancelBookStock(String orderNumber, Long bookId, int quantity) {
        String stockKey = RedisKeyConstants.STOCK_PREFIX + bookId;

        // Redis 재고 원복
        redisTemplate.opsForValue().increment(stockKey, quantity);

        // DB 예약 상태 업데이트
        reservationRepository.findByOrderNumberAndStatus(orderNumber, BookStockReservation.ReservationStatus.RESERVED)
                .stream()
                .filter(r -> r.getBookId().equals(bookId))
                .forEach(r -> r.setStatus(BookStockReservation.ReservationStatus.CANCELED));
    }

    public void increaseStock(Long bookId, int quantity){
        String stockKey = RedisKeyConstants.STOCK_PREFIX + bookId;

        if(Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))){
            redisTemplate.opsForValue().increment(stockKey, quantity);
        }
    }

    public void synchronizeStock(Long bookId){
        String stockKey = RedisKeyConstants.STOCK_PREFIX + bookId;
        // Hash 구조 변경에 따른 동기화 로직은 복잡해지므로 SCAN 사용 권장 또는 관리자 기능 주의
        // 여기서는 기존 패턴 유지가 어려우므로 예약이 하나라도 있는지 SCAN으로 확인하는 예시
        boolean hasActiveReservation = hasKeyPattern(RedisKeyConstants.RESERVE_HASH_PREFIX + "*");

        if (hasActiveReservation) {
            log.warn("[Admin] 동기화 실패 - 현재 주문 진행 중인 건이 존재할 수 있음. BookId: {}", bookId);
            throw new IllegalStateException("현재 주문이 진행 중인 도서일 수 있습니다. 잠시 후 다시 시도해주세요.");
        }

        Integer dbStock = bookRepository.findStockCountById(bookId);
        if(dbStock == null){
            throw new NotFoundBookException(bookId);
        }

        redisTemplate.opsForValue().set(stockKey, String.valueOf(dbStock));

        log.info("[Admin] 재고 동기화 완료 - BookId: {}, DB Stock: {}", bookId, dbStock);
    }

    private Long executeDecreaseAndReserveScript(String stockKey, String reserveHashKey, String bookId, int quantity) {
        return redisTemplate.execute(
                RedisScript.of(DECREASE_STOCK_AND_RESERVE_SCRIPT, Long.class),
                Arrays.asList(stockKey, reserveHashKey),
                String.valueOf(quantity),
                bookId,
                String.valueOf(RESERVATION_TTL.toSeconds())
        );
    }

    private void initializeStockFromDb(Long bookId) {
        String stockKey = RedisKeyConstants.STOCK_PREFIX + bookId;
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
                return cursor.hasNext();
            } catch (Exception e) {
                return false;
            }
        }));
    }
}
