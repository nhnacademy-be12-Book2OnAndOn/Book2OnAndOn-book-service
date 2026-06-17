package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookStockReservation;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookStockReservationRepository;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @InjectMocks
    private StockService stockService;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookStockReservationRepository reservationRepository;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    @DisplayName("재고 선점 - 성공 (Redis에 키가 있고 수량 충분)")
    void decreaseStock_Success() {
        StockRequest req = new StockRequest("ORDER-001", List.of(new StockRequest.StockItem(1L, 2)));

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(0L);
        when(hashOperations.hasKey(anyString(), anyString())).thenReturn(false);

        stockService.decreaseStock(req);

        verify(reservationRepository).save(any(BookStockReservation.class));
    }

    @Test
    @DisplayName("재고 선점 - 이미 예약된 건이면 스킵")
    void decreaseStock_AlreadyReserved() {
        StockRequest req = new StockRequest("ORDER-001", List.of(new StockRequest.StockItem(1L, 2)));
        when(hashOperations.hasKey("book:reserved_order:ORDER-001", "1")).thenReturn(true);

        stockService.decreaseStock(req);

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("재고 선점 - Redis 키 없음 (-1) -> DB 로딩 후 재시도 -> 성공")
    void decreaseStock_KeyMissing_ThenSuccess() {
        StockRequest req = new StockRequest("ORDER-001", List.of(new StockRequest.StockItem(1L, 1)));

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(-1L)
                .thenReturn(0L);
        
        when(bookRepository.findStockCountById(1L)).thenReturn(10);

        stockService.decreaseStock(req);

        verify(valueOperations).setIfAbsent("book:stock:1", "10");
    }

    @Test
    @DisplayName("재고 확정 - 성공 (Redis 데이터 존재)")
    void confirmStock_Success() {
        String orderNo = "ORDER-001";
        Map<Object, Object> reservations = Map.of("1", "5");
        
        when(hashOperations.entries("book:reserved_order:ORDER-001")).thenReturn(reservations);
        when(redisTemplate.hasKey("book:processed:ORDER-001:1")).thenReturn(false);
        when(bookRepository.decreaseStock(1L, 5)).thenReturn(1);
        
        Book book = new Book();
        book.setStockCount(0);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        when(reservationRepository.findByOrderNumberAndStatus(orderNo, BookStockReservation.ReservationStatus.RESERVED))
                .thenReturn(List.of(BookStockReservation.builder().bookId(1L).quantity(5).build()));

        stockService.confirmStock(orderNo);

        verify(redisTemplate).delete("book:reserved_order:ORDER-001");
        verify(valueOperations).set(eq("book:processed:ORDER-001:1"), eq("DONE"), any());
        assertThat(book.getStatus()).isEqualTo(BookStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("재고 확정 - Redis 데이터 만료 시 DB Fallback 성공")
    void confirmStock_FallbackSuccess() {
        String orderNo = "ORDER-001";
        when(hashOperations.entries(anyString())).thenReturn(Collections.emptyMap());
        
        BookStockReservation reservation = BookStockReservation.builder()
                .orderNumber(orderNo).bookId(1L).quantity(5).status(BookStockReservation.ReservationStatus.RESERVED).build();
        when(reservationRepository.findByOrderNumberAndStatus(orderNo, BookStockReservation.ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));

        when(bookRepository.decreaseStock(1L, 5)).thenReturn(1);
        Book book = new Book();
        book.setStockCount(10);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        stockService.confirmStock(orderNo);

        verify(bookRepository).decreaseStock(1L, 5);
        assertThat(reservation.getStatus()).isEqualTo(BookStockReservation.ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("재고 취소 - 성공 (Redis 데이터 존재)")
    void cancelStock_Success() {
        String orderNo = "ORDER-001";
        Map<Object, Object> reservations = Map.of("1", "5");
        when(hashOperations.entries("book:reserved_order:ORDER-001")).thenReturn(reservations);
        
        BookStockReservation reservation = BookStockReservation.builder()
                .orderNumber(orderNo).bookId(1L).quantity(5).status(BookStockReservation.ReservationStatus.RESERVED).build();
        when(reservationRepository.findByOrderNumberAndStatus(orderNo, BookStockReservation.ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));

        stockService.cancelStock(orderNo);

        verify(valueOperations).increment("book:stock:1", 5);
        verify(redisTemplate).delete("book:reserved_order:ORDER-001");
        assertThat(reservation.getStatus()).isEqualTo(BookStockReservation.ReservationStatus.CANCELED);
    }

    @Test
    @DisplayName("재고 동기화 - 성공")
    void synchronizeStock_Success() {
        doReturn(false).when(redisTemplate).execute(any(RedisCallback.class));
        when(bookRepository.findStockCountById(1L)).thenReturn(50);

        stockService.synchronizeStock(1L);

        verify(valueOperations).set("book:stock:1", "50");
    }
}
