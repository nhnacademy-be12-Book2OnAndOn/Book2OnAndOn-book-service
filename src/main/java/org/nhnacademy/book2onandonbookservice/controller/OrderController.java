package org.nhnacademy.book2onandonbookservice.controller;


import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.book.BookOrderResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.CartResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
import org.nhnacademy.book2onandonbookservice.service.book.BookService;
import org.nhnacademy.book2onandonbookservice.service.book.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/internal/books")
public class OrderController {

    private final BookService bookService;
    private final StockService stockService;

    /**
     * Order-Service가 호출하는 API 결제 금액 검증 및 주문서(영수증) 생성을 위한 데이터 제공 GET /internal/books?bookIds=1,2,3
     */
    @GetMapping
    public ResponseEntity<List<BookOrderResponse>> getBooksForOrder(@RequestParam("bookIds") List<Long> bookIds) {
        List<BookOrderResponse> responses = bookService.getBooksForOrder(bookIds);
        return ResponseEntity.ok(responses);
    }

    /**
     * 재고 선점 요청
     * Redis에서 재고를 임시 차감하고 선점 기록을 남김
     */
    @PostMapping("/stock/reserve")
    public ResponseEntity<Void> decreaseStock(@RequestBody StockRequest requests) {
        log.info("재고 선점 요청: {}건", requests.getBookInfoDtoList().size());
        stockService.decreaseStock(requests);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bulk")
    public ResponseEntity<Map<Long, CartResponse>> getBookSnapshots(@RequestBody List<Long> bookIds){
        Map<Long, CartResponse> result = bookService.getBookSnapshots(bookIds);
        return ResponseEntity.ok(result);
    }

}
