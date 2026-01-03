package org.nhnacademy.book2onandonbookservice.service.review;

import java.util.List;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewCreateRequest;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewDto;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewUpdateRequest;
import org.nhnacademy.book2onandonbookservice.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.multipart.MultipartFile;

public interface ReviewService {
    Long createReview(Long bookId, ReviewCreateRequest req, List<MultipartFile> image);

    ReviewDto getReview(Long reviewId);

    /// 특정 책에 대한 리뷰목록
    Page<ReviewDto> getReviewListByBookId(Long bookId, Pageable pageable);

    /// 특정 유저에 대한 리뷰목록
    @Query(value = "SELECT r FROM Review r JOIN FETCH r.book WHERE r.userId = :userId",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE r.userId = :userId")
    Page<ReviewDto> getReviewListByUserId(Long userId, Pageable pageable);

    void updateReview(Long reviewId, ReviewUpdateRequest request, List<MultipartFile> images);
}
