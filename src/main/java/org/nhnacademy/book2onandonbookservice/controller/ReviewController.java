package org.nhnacademy.book2onandonbookservice.controller;


import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewCreateRequest;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewDto;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewUpdateRequest;
import org.nhnacademy.book2onandonbookservice.service.review.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/books")
public class ReviewController {

    private final ReviewService reviewService;

    //리뷰생성
    @PostMapping(value = "/{bookId}/reviews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Long> createReview(@PathVariable Long bookId,
                                             @RequestPart(value = "request") @Valid ReviewCreateRequest request,
                                             @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        Long reviewId = reviewService.createReview(bookId, request, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewId);
    }

    //특정 도서에 대한 리뷰목록
    @GetMapping(value = "/{bookId}/reviews")
    public ResponseEntity<Page<ReviewDto>> getReviewList(@PathVariable Long bookId,
                                                         @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Direction.DESC) Pageable pageable) {
        Page<ReviewDto> reviewPage = reviewService.getReviewListByBookId(bookId, pageable);
        return ResponseEntity.ok(reviewPage);
    }

    //리뷰수정
    @PutMapping(value = "/reviews/{reviewId}")
    public ResponseEntity<Void> updateReview(@PathVariable Long reviewId,
                                             @RequestPart(value = "request") @Valid ReviewUpdateRequest request,
                                             @RequestPart(value = "images", required = false) List<MultipartFile> newImages) {
        reviewService.updateReview(reviewId, request, newImages);
        return ResponseEntity.ok().build();
    }

    //리뷰삭제
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.noContent().build();
    }

}
