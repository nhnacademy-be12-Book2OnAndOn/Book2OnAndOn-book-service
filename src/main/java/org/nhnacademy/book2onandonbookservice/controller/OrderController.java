package org.nhnacademy.book2onandonbookservice.controller;


import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.dto.book.BookOrderResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
import org.nhnacademy.book2onandonbookservice.service.book.BookService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/books")
public class OrderController {

    private final BookService bookService;
    private final UserHeaderUtil util;

    /**
     * Order-Service가 호출하는 API 결제 금액 검증 및 주문서(영수증) 생성을 위한 데이터 제공 GET /internal/books?bookIds=1,2,3
     */
    @GetMapping
    public ResponseEntity<List<BookOrderResponse>> getBooksForOrder(@RequestParam("bookIds") List<Long> bookIds) {
        List<BookOrderResponse> responses = bookService.getBooksForOrder(bookIds);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/stock/decrease")
    public ResponseEntity<Void> decreaseStock(@RequestBody List<StockRequest> requests) {
        bookService.decreaseStock(requests);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stock/increase")
    public ResponseEntity<Void> increaseStock(@RequestBody List<StockRequest> requests) {
        bookService.increaseStock(requests);
        return ResponseEntity.ok().build();
    }


}
