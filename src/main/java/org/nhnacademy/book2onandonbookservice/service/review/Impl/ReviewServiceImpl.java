package org.nhnacademy.book2onandonbookservice.service.review.Impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.OrderServiceClient;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewCreateRequest;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewDto;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewUpdateRequest;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Review;
import org.nhnacademy.book2onandonbookservice.entity.ReviewImage;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundReviewException;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.ReviewRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.service.review.ReviewService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReviewServiceImpl implements ReviewService {
    private final ReviewRepository reviewRepository;

    private final BookRepository bookRepository;

    private final OrderServiceClient orderServiceClient;

    private final ImageUploadService imageUploadService;

    private final UserHeaderUtil util;

    private final StringRedisTemplate redisTemplate;


    /// 리뷰생성
    @Override
    public Long createReview(Long bookId, ReviewCreateRequest req, List<MultipartFile> image) {
        Book book = bookRepository.findById(bookId).orElseThrow(() -> new NotFoundBookException(bookId));
        Long userId = util.getUserId();
        String key = "purchase:" + userId + ":" + bookId;
        //order-service에 유저가 해당 도서를 구매하고 배송이 완료된 후 리뷰를 작성하는지 검증
        boolean isPurchased = false;

        if (redisTemplate.hasKey(key)) {
            //1차 점검 (주문쪽에서 정한 캐싱만료일안에 리뷰작성을 한다면 redis에서 꺼내쓸 수 있음)
            isPurchased = true;
            log.info("Redis 캐시에서 구매 이력 확인완료: userId={}, bookId={}", userId, bookId);
        } else {
            try {
                //2차 점검 (캐시 만료일이 지나서 redis에 없을때 직접 feign client로 요청을 보내야함)
                isPurchased = orderServiceClient.hasPurchased(userId, bookId);
                redisTemplate.opsForValue()
                        .set(key, "Y", Duration.ofDays(90)); // 혹시 모르니 요청보내서 받아온것도 redis에 캐싱해두기 90일 만료로
                log.info("Order Service API로 구매 이력 확인완료: result={}", isPurchased);
            } catch (Exception e) {
                log.error("주문 서비스 호출 실패 (Redis에도 없음): {}", e.getMessage());

                throw new IllegalArgumentException("현재 시스템 점검 중으로 구매 이력을 확인할 수 없습니다.");
            }
        }
        if (!isPurchased) {
            throw new IllegalArgumentException("해당 도서를 구매후 배송이 완료된 회원만 리뷰 작성 가능합니다.");
        }

        if (reviewRepository.existsByBookIdAndUserId(bookId, userId)) {
            throw new IllegalArgumentException("이미 해당 도서에 대한 리뷰를 작성했습니다.");
        }

        Review review = Review.builder()
                .book(book)
                .userId(userId)
                .title(req.getTitle())
                .content(req.getContent())
                .score(req.getScore())
                .createdAt(LocalDateTime.now())
                .build();

        if (image != null && !image.isEmpty()) {
            for (MultipartFile file : image) {
                if (!file.isEmpty()) {
                    String imageUrl = imageUploadService.uploadReviewImage(file);

                    ReviewImage reviewImage = ReviewImage.builder()
                            .book(book)
                            .review(review)
                            .imagePath(imageUrl)
                            .build();

                    review.getImages().add(reviewImage);
                }
            }
        }

        reviewRepository.save(review);

        updateBookRating(book);

        return review.getId();
    }


    /// 특정 책에 대한 리뷰 목록
    @Override
    public Page<ReviewDto> getReviewListByBookId(Long bookId, Pageable pageable) {
        Book book = bookRepository.findById(bookId).orElseThrow(() -> new NotFoundBookException(bookId));
        Page<Review> reviewPage = reviewRepository.findAllByBook(book, pageable);

        return reviewPage.map(ReviewDto::from);
    }

    /// 특정 유저에 대한 리뷰 목록
    @Override
    public Page<ReviewDto> getReviewListByUserId(Long userId, Pageable pageable) {
        Page<Review> reviewPage = reviewRepository.findAllByUserId(userId, pageable);
        return reviewPage.map(ReviewDto::from);
    }

    /*
    프론트엔드 구현 팁:

    수정 화면을 그릴 때, 기존 이미지 옆에 [X] 버튼을 둠.

    사용자가 [X]를 누르면 화면에서 이미지를 숨기고, 그 이미지의 ID를 deleteImageIds 배열에 담음

    수정 완료를 누르면 이 ID 목록과, 새로 추가한 파일들을 함께 서버로 보냄
     */

    /// 특정 리뷰 수정
    @Override
    public void updateReview(Long reviewId, ReviewUpdateRequest request, List<MultipartFile> images) {
        //1. DB에서 데이터를 가져오면, JPA는 이 객체를 영속성 컨텍스트 보관소에 넣고 최초 상태를 찍어둠
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new NotFoundReviewException(reviewId));

        Long currentId = util.getUserId();
        if (!review.getUserId().equals(currentId)) {
            throw new AccessDeniedException("본인의 리뷰만 수정할 수 있습니다.");
        }
        //더티 체킹 (@Transactional 덕분에 가능함)
        //2. 자바 객체의 값을 바꿈 (아직 DB에 간게 아니라 메모리 상의 객체만 바뀐 상태)
        review.update(request.getTitle(), request.getContent(), request.getScore());

        if (request.getDeleteImageIds() != null && !request.getDeleteImageIds().isEmpty()) {
            review.getImages().removeIf(image -> {
                if (request.getDeleteImageIds().contains(image.getId())) {
                    imageUploadService.remove(image.getImagePath());
                    return true;
                }
                return false;
            });
        }

        if (images != null && !images.isEmpty()) {
            for (MultipartFile file : images) {
                if (!file.isEmpty()) {
                    String imageUrl = imageUploadService.uploadReviewImage(file);

                    ReviewImage newImage = ReviewImage.builder()
                            .review(review)
                            .book(review.getBook())
                            .imagePath(imageUrl)
                            .build();

                    review.getImages().add(newImage);
                }
            }
        }
        updateBookRating(review.getBook());
        //3. 트랜잭션 종료 - JPA는 최초 상태와 현재 객체 상태를 비교해서 수정이 일어난게 보이면 바꼈다고 생각하고 자동반영
    }

    /// 특정 리뷰 삭제
    @Override
    public void deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new NotFoundReviewException(reviewId));

        Long currentId = util.getUserId();
        if (!review.getUserId().equals(currentId)) {
            throw new AccessDeniedException("본인의 리뷰만 삭제할 수 있습니다.");
        }

        Book book = review.getBook();
        reviewRepository.delete(review); // entityManager.remove(review)가 실행됨
        updateBookRating(book);
        //트랜잭션이 끝날때 진짜 DELETE가 DB로 날아감
    }

    /// 내부 로직 메서드
    //평점 업데이트 로직
    private void updateBookRating(Book book) {
        Double average = reviewRepository.getAverageScoreByBook(book);

        if (average == null) {
            book.updateRating(0.0);
            return;
        }

        double roundedAverage = Math.round(average * 10.0) / 10.0;
        book.updateRating(roundedAverage);
    }


}
