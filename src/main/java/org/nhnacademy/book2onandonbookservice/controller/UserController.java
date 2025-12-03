package org.nhnacademy.book2onandonbookservice.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.annotation.AuthCheck;
import org.nhnacademy.book2onandonbookservice.domain.Role;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewDto;
import org.nhnacademy.book2onandonbookservice.service.book.BookLikeService;
import org.nhnacademy.book2onandonbookservice.service.review.ReviewService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
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
@RequestMapping("/internal/users")
public class UserController {
    private final ReviewService reviewService;
    private final BookLikeService bookLikeService;
    private final UserHeaderUtil util;

    /// 특정 유저에대한 리뷰 목록
    @GetMapping(value = "/{userId}/reviews")
    public ResponseEntity<Page<ReviewDto>> getUserReviewList(@PathVariable Long userId,
                                                             @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Direction.DESC) Pageable pageable) {
        Page<ReviewDto> reviewPage = reviewService.getReviewListByUserId(userId, pageable);
        return ResponseEntity.ok(reviewPage);
    }

    /// 사용자 좋아요 목록 조회 GET /books/likes/me User Service에서 호출 후 List<Long> 형태를 받아감
    @GetMapping("/likes/me")
    @AuthCheck(Role.USER)
    public ResponseEntity<List<Long>> getMyLikedBooks() {
        Long userId = util.getUserId();
        List<Long> bookIds = bookLikeService.getMyLikedBookIds(userId);
        return ResponseEntity.ok(bookIds);
    }


}
