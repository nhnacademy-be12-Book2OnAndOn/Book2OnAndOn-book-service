package org.nhnacademy.book2onandonbookservice.controller;

import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.service.book.BookPriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/price")
@RequiredArgsConstructor
public class AdminPriceController {

    private final BookPriceService bookPriceService;

    /// 할인율 변경 요청 (비동기 실행됨)
    @PostMapping("/discount")
    public ResponseEntity<String> updateDiscountRate(@RequestParam("rate") int rate){
        if(rate<0 || rate>100){
            return ResponseEntity.badRequest().body("할인율은 0~100 사이여야합니다.");
        }

        String status = bookPriceService.getUpdateStatus();

        if("PROCESSING".equals(status)){
            return ResponseEntity.status(409).body("이미 가격 업데이트 작업이 진행 중 입니다.");
        }

        bookPriceService.updateGlobalDiscountRate(rate);
        return ResponseEntity.ok("가격 업데이트 작업이 시작되었습니다.");
    }

    /// 가격 상태 조회 (프론트에서 10초마다 노출되어야함)
    @GetMapping("/status")
    public ResponseEntity<String> getUpdateStatus(){
        String status = bookPriceService.getUpdateStatus();
        return ResponseEntity.ok(status !=null ? status: "IDLE");
    }
}
