package org.nhnacademy.book2onandonbookservice.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.book.BookOrderResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.exception.OutOfStockException;
import org.nhnacademy.book2onandonbookservice.service.book.BookService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    BookService bookService;

    @MockitoBean
    UserHeaderUtil util;

    @Test
    @DisplayName("주문용 도서 정보 조회 성공")
    void getBooksForOrder() throws Exception {
        List<Long> bookIds = List.of(1L, 2L);
        BookOrderResponse response = BookOrderResponse.builder()
                .bookId(1L)
                .title("Book 1")
                .priceSales(10000L)
                .status(BookStatus.ON_SALE)
                .stockCount(5)
                .build();
        BookOrderResponse response2 = BookOrderResponse.builder()
                .bookId(2L)
                .title("Book 2")
                .priceSales(20000L)
                .status(BookStatus.ON_SALE)
                .stockCount(10)
                .build();

        given(bookService.getBooksForOrder(bookIds)).willReturn(List.of(response, response2));

        mockMvc.perform(get("/internal/books")
                        .param("bookIds", "1,2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].bookId", is(1)))
                .andExpect(jsonPath("$[0].title", is("Book 1")))
                .andExpect(jsonPath("$[1].bookId", is(2)))
                .andExpect(jsonPath("$[1].title", is("Book 2")));
    }

    @Test
    @DisplayName("재고 차감 성공")
    void decreaseStock() throws Exception {
        List<StockRequest> reqs = List.of(
                StockRequest.builder().bookId(1L).quantity(2).build(),
                StockRequest.builder().bookId(2L).quantity(4).build()
        );

        mockMvc.perform(post("/internal/books/stock/decrease")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqs)))
                .andExpect(status().isOk());

        verify(bookService).decreaseStock(anyList());
    }

    @Test
    @DisplayName("재고 차감 실패")
    void decreaseStock_Fail() throws Exception {
        List<StockRequest> reqs = List.of(
                StockRequest.builder().bookId(1L).quantity(2).build(),
                StockRequest.builder().bookId(2L).quantity(4).build()
        );

        willThrow(new OutOfStockException("재고가 부족합니다.")).given(bookService).decreaseStock(anyList());
        mockMvc.perform(post("/internal/books/stock/decrease")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqs)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Stock Conflict")));
    }

    @Test
    @DisplayName("재고 증가 성공")
    void increaseStock() throws Exception {
        List<StockRequest> reqs = List.of(
                StockRequest.builder().bookId(1L).quantity(2).build()
        );

        doNothing().when(bookService).increaseStock(anyList());

        mockMvc.perform(post("/internal/books/stock/increase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqs)))
                .andExpect(status().isOk());

        verify(bookService).increaseStock(anyList());
    }

    @Test
    @DisplayName("재고 증가 실패")
    void increaseStock_Fail_NotFoundBook() throws Exception {
        List<StockRequest> reqs = List.of(
                StockRequest.builder().bookId(999L).quantity(2).build()
        );

        willThrow(new NotFoundBookException(999L)).given(bookService).increaseStock(anyList());

        mockMvc.perform(post("/internal/books/stock/increase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqs)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")));
    }
}