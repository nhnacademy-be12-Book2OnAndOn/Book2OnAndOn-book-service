package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class BookPriceServiceTest {

    @InjectMocks
    @Spy
    private BookPriceService bookPriceService;

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookSearchIndexService bookSearchIndexService;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        bookPriceService.setSelf(bookPriceService);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("전체 도서 할인율 변경 - 성공 (루프 1회 실행 후 종료)")
    void updateGlobalDiscountRate_Success() {

        doReturn(true).doReturn(false)
                .when(bookPriceService).processPriceUpdateChunk(anyInt(), anyDouble());

        bookPriceService.updateGlobalDiscountRate(10);

        verify(valueOperations).set(eq("admin:price:update:status"), eq("PROCESSING"), eq(1L), eq(TimeUnit.HOURS));
        verify(bookPriceService, times(2)).processPriceUpdateChunk(anyInt(), anyDouble());
        verify(valueOperations).set(eq("admin:price:update:status"), eq("DONE"), eq(1L), eq(TimeUnit.DAYS));
    }

    @Test
    @DisplayName("전체 도서 할인율 변경 - InterruptedException 발생 시 처리")
    void updateGlobalDiscountRate_InterruptedException() {

        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return true;
        }).when(bookPriceService).processPriceUpdateChunk(anyInt(), anyDouble());

        bookPriceService.updateGlobalDiscountRate(10);

        verify(bookPriceService).processPriceUpdateChunk(anyInt(), anyDouble());

        verify(valueOperations, never()).set(eq("admin:price:update:status"), eq("FAILED"));

        verify(valueOperations).set(eq("admin:price:update:status"), eq("PROCESSING"), anyLong(), any());
    }

    @Test
    @DisplayName("전체 도서 할인율 변경 - 일반 예외 발생 시 FAILED 처리")
    void updateGlobalDiscountRate_Exception() {

        doThrow(new RuntimeException("Unexpected Error"))
                .when(bookPriceService).processPriceUpdateChunk(anyInt(), anyDouble());

        bookPriceService.updateGlobalDiscountRate(10);

        verify(valueOperations).set("admin:price:update:status", "FAILED");
    }

    @Test
    @DisplayName("청크 단위 업데이트 - 데이터가 있을 때 업데이트 및 인덱싱 수행")
    void processPriceUpdateChunk_HasBooks() {

        Book book1 = new Book();
        book1.setPriceStandard(10000L);
        book1.setPriceSales(10000L);

        Book book2 = new Book();
        book2.setPriceStandard(20000L);
        book2.setPriceSales(null);

        Page<Book> books = new PageImpl<>(List.of(book1, book2));
        when(bookRepository.findAll(any(PageRequest.class))).thenReturn(books);

        boolean result = bookPriceService.processPriceUpdateChunk(0, 0.9);

        assertThat(result).isTrue();
        assertThat(book1.getPriceSales()).isEqualTo(9000L);
        assertThat(book2.getPriceSales()).isEqualTo(18000L);
        verify(bookSearchIndexService, times(2)).index(any(Book.class));
    }

    @Test
    @DisplayName("청크 단위 업데이트 - 데이터가 없을 때 False 반환")
    void processPriceUpdateChunk_NoBooks() {

        when(bookRepository.findAll(any(PageRequest.class))).thenReturn(Page.empty());

        boolean result = bookPriceService.processPriceUpdateChunk(0, 0.9);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("업데이트 상태 조회")
    void getUpdateStatus() {

        when(valueOperations.get("admin:price:update:status")).thenReturn("PROCESSING");

        String status = bookPriceService.getUpdateStatus();

        assertThat(status).isEqualTo("PROCESSING");
    }
}