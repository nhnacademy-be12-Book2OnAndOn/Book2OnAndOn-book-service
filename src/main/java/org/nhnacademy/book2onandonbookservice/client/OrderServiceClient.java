package org.nhnacademy.book2onandonbookservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "ORDER-SERVICE")
public interface OrderServiceClient {

    @GetMapping("/api/orders/check-purchase/{bookId}")
    boolean hasPurchased(@RequestHeader("X-User-Id") Long userId, @PathVariable Long bookId);
}
