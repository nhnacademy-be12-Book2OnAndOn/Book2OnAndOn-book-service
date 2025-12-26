package org.nhnacademy.book2onandonbookservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.book.BookOrderResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.CartResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
import org.nhnacademy.book2onandonbookservice.service.book.BookService;
import org.nhnacademy.book2onandonbookservice.service.book.StockService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private BookService bookService;

    @Mock
    private StockService stockService;

    @InjectMocks
    private OrderController orderController;

    @Test
    @DisplayName("성공: 주문 검증용 도서 정보 조회 (GET /internal/books)")
    void getBooksForOrder_Success() {

        List<Long> bookIds = List.of(1L, 2L);
        BookOrderResponse responseDto = new BookOrderResponse();
        List<BookOrderResponse> expectedResponse = List.of(responseDto);

        when(bookService.getBooksForOrder(bookIds)).thenReturn(expectedResponse);

        ResponseEntity<List<BookOrderResponse>> response = orderController.getBooksForOrder(bookIds);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(bookService).getBooksForOrder(bookIds);
    }

    @Test
    @DisplayName("성공: 재고 선점 요청 (POST /internal/books/stock/reserve)")
    void decreaseStock_Success() {
        StockRequest stockRequest = mock(StockRequest.class);
        when(stockRequest.getBookInfoDtoList()).thenReturn(Collections.emptyList());

        ResponseEntity<Void> response = orderController.decreaseStock(stockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(stockService).decreaseStock(stockRequest);
    }

    @Test
    @DisplayName("성공: 장바구니/주문용 도서 스냅샷 조회 (POST /internal/books/bulk)")
    void getBookSnapshots_Success() {
        List<Long> bookIds = List.of(1L, 2L);
        CartResponse cartResponse = new CartResponse();
        Map<Long, CartResponse> expectedResult = Map.of(1L, cartResponse);

        when(bookService.getBookSnapshots(bookIds)).thenReturn(expectedResult);

        ResponseEntity<Map<Long, CartResponse>> response = orderController.getBookSnapshots(bookIds);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedResult);
        verify(bookService).getBookSnapshots(bookIds);
    }
}