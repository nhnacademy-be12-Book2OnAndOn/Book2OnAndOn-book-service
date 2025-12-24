package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.exception.OutOfStockException;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
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
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("재고 선점 - 성공 (Redis에 키가 있고 수량 충분)")
    void decreaseStock_Success() {
        StockRequest req = new StockRequest("ORDER-001", List.of(new StockRequest.StockItem(1L, 2)));

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(0L);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        stockService.decreaseStock(req);

        verify(valueOperations).set(anyString(), eq("2"), any());
    }

    @Test
    @DisplayName("재고 선점 - 이미 예약된 건이면 스킵")
    void decreaseStock_AlreadyReserved() {
        StockRequest req = new StockRequest("ORDER-001", List.of(new StockRequest.StockItem(1L, 2)));
        when(redisTemplate.hasKey("book:reserved:ORDER-001:1")).thenReturn(true);

        stockService.decreaseStock(req);

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    @DisplayName("재고 선점 - Redis 키 없음 (-1) -> DB 로딩 후 재시도 -> 성공")
    void decreaseStock_KeyMissing_ThenSuccess() {
        StockRequest req = new StockRequest("ORDER-001", List.of(new StockRequest.StockItem(1L, 1)));

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(-1L)
                .thenReturn(0L);
        
        when(bookRepository.findStockCountById(1L)).thenReturn(10);

        stockService.decreaseStock(req);

        verify(valueOperations).setIfAbsent("book:stock:1", "10");
    }

    @Test
    @DisplayName("재고 선점 - DB에도 재고 정보 없음 (예외)")
    void decreaseStock_DB_NotFound() {
        StockRequest req = new StockRequest("ORDER-001", List.of(new StockRequest.StockItem(1L, 1)));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(-1L);
        when(bookRepository.findStockCountById(1L)).thenReturn(null);

        assertThatThrownBy(() -> stockService.decreaseStock(req))
                .isInstanceOf(OutOfStockException.class);
    }

    @Test
    @DisplayName("재고 선점 - 재고 부족 (1)")
    void decreaseStock_NotEnough() {
        StockRequest req = new StockRequest("ORDER-001", List.of(new StockRequest.StockItem(1L, 100)));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);

        assertThatThrownBy(() -> stockService.decreaseStock(req))
                .isInstanceOf(OutOfStockException.class);
    }

    @Test
    @DisplayName("재고 확정 - 성공 및 품절 처리")
    void confirmStock_Success() {
        String orderNo = "ORDER-001";
        String key = "book:reserved:ORDER-001:1";
        
        when(redisTemplate.keys("book:reserved:ORDER-001:*")).thenReturn(Set.of(key));
        when(redisTemplate.hasKey("book:processed:ORDER-001:1")).thenReturn(false);
        when(valueOperations.get(key)).thenReturn("5");

        when(bookRepository.decreaseStock(1L, 5)).thenReturn(1);
        
        Book book = new Book();
        book.setStockCount(0);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        stockService.confirmStock(orderNo);

        verify(redisTemplate).delete(key);
        verify(valueOperations).set(eq("book:processed:ORDER-001:1"), eq("DONE"), any());
        assertThat(book.getStatus()).isEqualTo(BookStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("재고 확정 - 키 만료됨 (Critical Log)")
    void confirmStock_Expired() {
        when(redisTemplate.keys(anyString())).thenReturn(Collections.emptySet());
        stockService.confirmStock("ORDER-999");
        verify(bookRepository, never()).decreaseStock(anyLong(), anyInt());
    }
    
    @Test
    @DisplayName("재고 확정 - DB 업데이트 실패 (데이터 불일치)")
    void confirmStock_DB_Update_Fail() {
        String key = "book:reserved:O:1";
        when(redisTemplate.keys(anyString())).thenReturn(Set.of(key));
        when(valueOperations.get(key)).thenReturn("1");
        when(bookRepository.decreaseStock(1L, 1)).thenReturn(0);

        assertThatThrownBy(() -> stockService.confirmStock("O"))
                .isInstanceOf(OutOfStockException.class);
    }

    @Test
    @DisplayName("재고 증가")
    void increaseStock() {
        when(redisTemplate.hasKey("book:stock:1")).thenReturn(true);
        stockService.increaseStock(1L, 10);
        verify(valueOperations).increment("book:stock:1", 10);
    }

    @Test
    @DisplayName("재고 취소")
    void cancelStock() {
        String key = "book:reserved:O:1";
        when(redisTemplate.keys(anyString())).thenReturn(Set.of(key));
        when(valueOperations.get(key)).thenReturn("5");

        stockService.cancelStock("O");

        verify(redisTemplate).delete(key);
        verify(valueOperations).increment("book:stock:1", 5);
    }

    @Test
    @DisplayName("재고 동기화 - 진행 중인 주문 있음 (실패)")
    void synchronizeStock_Fail_Busy() {

        doReturn(true).when(redisTemplate).execute(any(RedisCallback.class));

        assertThatThrownBy(() -> stockService.synchronizeStock(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("재고 동기화 - 성공")
    void synchronizeStock_Success() {
        // hasKeyPattern -> false
        doReturn(false).when(redisTemplate).execute(any(RedisCallback.class));
        when(bookRepository.findStockCountById(1L)).thenReturn(50);

        stockService.synchronizeStock(1L);

        verify(valueOperations).set("book:stock:1", "50");
    }
    
    @Test
    @DisplayName("재고 동기화 - DB에 책 없음")
    void synchronizeStock_NotFound() {
        doReturn(false).when(redisTemplate).execute(any(RedisCallback.class));
        when(bookRepository.findStockCountById(1L)).thenReturn(null);

        assertThatThrownBy(() -> stockService.synchronizeStock(1L))
                .isInstanceOf(NotFoundBookException.class);
    }
}