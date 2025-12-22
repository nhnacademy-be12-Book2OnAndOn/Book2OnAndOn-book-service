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
    @PostMapping("/stock/decrease")
    public ResponseEntity<Void> decreaseStock(@RequestBody List<StockRequest> requests) {
        log.info("재고 선점 요청: {}건", requests.size());
        stockService.decreaseStock(requests);
        return ResponseEntity.ok().build();
    }

    /**
     * 재고 확정 요청
     * 결제 성공 시 호출
     * 실제 DB 재고를 차감하고 Redis 선점 기록을 삭제함.
     */
    @PostMapping("/stock/confirm")
    public ResponseEntity<Void> confirmStock(@RequestBody List<StockRequest> requests) {
        log.info("재고 확정 요청: {}건", requests.size());
        stockService.confirmStock(requests);
        return ResponseEntity.ok().build();
    }

    /**
     * 재고 복구 요청
     * 결제 실패 또는 중도 취소시 호출(보상 트랜잭션)
     * Redis에서 차감했던 재고를 다시 원상 복구함
     */
    @PostMapping("/stock/cancel")
    public ResponseEntity<Void> cancelStock(@RequestBody List<StockRequest> requests){
        log.info("재고 복구(취소) 요청: {}건", requests.size());
        stockService.cancelStock(requests);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/bulk")
    public ResponseEntity<Map<Long, CartResponse>> getBookSnapshots(@RequestBody List<Long> bookIds){
        Map<Long, CartResponse> result = bookService.getBookSnapshots(bookIds);
        return ResponseEntity.ok(result);
    }

}
