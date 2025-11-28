package org.nhnacademy.book2onandonbookservice.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

//주문쪽에 요청 보낼때만 사용
@FeignClient(name = "ORDER-SERVICE")
public interface OrderServiceClient {

    @GetMapping("orders/check-purchase/{bookId}")
    boolean hasPurchased(@RequestHeader("X-User-Id") Long userId, @PathVariable Long bookId);

    /// 판매량 순 period=DAILY, WEEKLY GET /orders/bestsellers?period=DAILY GET /orders/bestsellers?period=WEEKLY
    @GetMapping("orders/bestsellers")
    List<Long> getBestSellersBookIds(@RequestParam("period") String period);

}
