package org.nhnacademy.book2onandonbookservice.client;

import org.springframework.cloud.openfeign.FeignClient;

//요청 보낼때만 사용
@FeignClient(name = "USER-SERVICE")
public interface UserServiceClient {
    /**
     * 예시
     *@ GetMapping("orders/check-purchase/{bookId}")
     *     boolean hasPurchased(@RequestHeader("X-User-Id") Long userId, @PathVariable Long bookId);
     */

}
