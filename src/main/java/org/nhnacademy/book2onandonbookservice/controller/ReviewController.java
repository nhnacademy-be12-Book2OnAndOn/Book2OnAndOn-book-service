package org.nhnacademy.book2onandonbookservice.controller;


import jakarta.validation.Valid;
import jakarta.ws.rs.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.annotation.AuthCheck;
import org.nhnacademy.book2onandonbookservice.domain.Role;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewCreateRequest;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewDto;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewUpdateRequest;
import org.nhnacademy.book2onandonbookservice.entity.Review;
import org.nhnacademy.book2onandonbookservice.service.review.PurchaseVerificationService;
import org.nhnacademy.book2onandonbookservice.service.review.ReviewService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/books")
public class ReviewController {

    private final ReviewService reviewService;
    private final PurchaseVerificationService purchaseVerificationService;
    private final UserHeaderUtil util;
    //리뷰생성
    @PostMapping(value = "/{bookId}/reviews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Long> createReview(@PathVariable Long bookId,
                                             @RequestPart(value = "request") @Valid ReviewCreateRequest request,
                                             @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        Long reviewId = reviewService.createReview(bookId, request, images);

        return ResponseEntity.status(HttpStatus.CREATED).body(reviewId);
    }

    @GetMapping("/review/{reviewId}")
    public ResponseEntity<ReviewDto> getReview(@PathVariable Long reviewId){
        return ResponseEntity.ok().body(reviewService.getReview(reviewId));
    }


    //리뷰수정
    @PutMapping(value = "/reviews/{reviewId}")
    public ResponseEntity<Void> updateReview(@PathVariable Long reviewId,
                                             @RequestPart(value = "request") @Valid ReviewUpdateRequest request,
                                             @RequestPart(value = "images", required = false) List<MultipartFile> newImages) {
        reviewService.updateReview(reviewId, request, newImages);
        return ResponseEntity.ok().build();
    }

    //리뷰 작성 가능 자격 확인 API
    @GetMapping("/{bookId}/reviews/eligibility")
    public ResponseEntity<Boolean> checkReviewEligibility(@PathVariable Long bookId,
                                                          @RequestHeader("X-User-Id") Long userId){
//        Long userId = util.getUserId();

        boolean isEligible = purchaseVerificationService.verifyPurchase(userId, bookId);

        return ResponseEntity.ok(isEligible);
    }



}
