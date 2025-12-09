package org.nhnacademy.book2onandonbookservice.client;

import org.nhnacademy.book2onandonbookservice.dto.review.ReviewEventRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "USER-SERVICE")
public interface UserServiceClient {

    /// 리뷰 작성 이벤트 알림 (포인트 적립 등)
    /// POST /internal/users/reviews/event
    @PostMapping("/internal/users/reviews/event")
    void notifyReviewCreated(@RequestBody ReviewEventRequest reviewEventRequest);
}
