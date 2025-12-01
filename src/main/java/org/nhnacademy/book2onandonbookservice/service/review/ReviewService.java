package org.nhnacademy.book2onandonbookservice.service.review;

import java.util.List;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewCreateRequest;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewDto;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ReviewService {
    Long createReview(Long bookId, ReviewCreateRequest req, List<MultipartFile> image);

    /// 특정 책에 대한 리뷰목록
    Page<ReviewDto> getReviewListByBookId(Long bookId, Pageable pageable);

    /// 특정 유저에 대한 리뷰목록
    Page<ReviewDto> getReviewListByUserId(Long userId, Pageable pageable);

    void updateReview(Long reviewId, ReviewUpdateRequest request, List<MultipartFile> images);

    void deleteReview(Long reviewId);
}
