package org.nhnacademy.book2onandonbookservice.controller;

import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewDto;
import org.nhnacademy.book2onandonbookservice.service.review.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
    private final ReviewService reviewService;

    //특정 유저에대한 리뷰 목록
    @GetMapping(value = "/{userId}/reviews")
    public ResponseEntity<Page<ReviewDto>> getUserReviewList(@PathVariable Long userId,
                                                             @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Direction.DESC) Pageable pageable) {
        Page<ReviewDto> reviewPage = reviewService.getReviewListByUserId(userId, pageable);
        return ResponseEntity.ok(reviewPage);
    }


}
