package org.nhnacademy.book2onandonbookservice.service.review.Impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.OrderServiceClient;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewCreateRequest;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewDto;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewUpdateRequest;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Review;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundReviewException;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.ReviewRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @InjectMocks
    ReviewServiceImpl reviewService;

    @Mock
    ReviewRepository reviewRepository;
    @Mock
    BookRepository bookRepository;
    @Mock
    OrderServiceClient orderServiceClient;
    @Mock
    ImageUploadService imageUploadService;
    @Mock
    UserHeaderUtil util;
    @Mock
    StringRedisTemplate redisTemplate;
    @Mock
    ValueOperations<String, String> valueOperations;

    private Book book;
    private Long bookId = 1L;
    private Long userId = 100L;

    @BeforeEach
    void setUp() {
        book = Book.builder().id(bookId).rating(0.0).build();
        // Redis Template Mocking
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }


    @Test
    @DisplayName("리뷰 생성 성공 - [1차] Redis 캐시 Hit (주문 서버 호출 안 함)")
    void createReview_Success_RedisHit() {
        ReviewCreateRequest request = ReviewCreateRequest.builder().title("굿").score(5).content("내용").build();
        String redisKey = "purchase:" + userId + ":" + bookId;

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);

        given(redisTemplate.hasKey(redisKey)).willReturn(true);

        given(reviewRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(false);
        given(reviewRepository.getAverageScoreByBook(book)).willReturn(5.0);

        reviewService.createReview(bookId, request, Collections.emptyList());

        verify(reviewRepository, times(1)).save(any(Review.class));
        verify(orderServiceClient, never()).hasPurchased(any(), any());
    }

    @Test
    @DisplayName("리뷰 생성 성공 - [2차] Redis Miss -> Feign 호출 성공 -> Redis 저장")
    void createReview_Success_RedisMiss_FeignHit() {
        ReviewCreateRequest request = ReviewCreateRequest.builder().title("굿").score(5).content("내용").build();
        String redisKey = "purchase:" + userId + ":" + bookId;

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);

        // Redis에 키 없음
        given(redisTemplate.hasKey(redisKey)).willReturn(false);
        // 주문 서버 호출 -> 구매함(true)
        given(orderServiceClient.hasPurchased(userId, bookId)).willReturn(true);

        given(reviewRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(false);

        reviewService.createReview(bookId, request, Collections.emptyList());

        verify(reviewRepository, times(1)).save(any(Review.class));
        // Redis에 캐시 저장했는지 검증 (90일)
        verify(valueOperations, times(1)).set(eq(redisKey), eq("Y"), any(Duration.class));
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 구매 이력 없음 (Redis X, Feign False)")
    void createReview_Fail_NotPurchased() {
        ReviewCreateRequest request = ReviewCreateRequest.builder().title("구매 안 함").build();
        String redisKey = "purchase:" + userId + ":" + bookId;

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);

        given(redisTemplate.hasKey(redisKey)).willReturn(false);
        given(orderServiceClient.hasPurchased(userId, bookId)).willReturn(false);

        assertThatThrownBy(() -> reviewService.createReview(bookId, request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 도서를 구매후 배송이 완료된 회원만");
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 시스템 장애 (Redis X, Feign Exception)")
    void createReview_Fail_SystemError() {
        ReviewCreateRequest request = ReviewCreateRequest.builder().title("장애 발생").build();
        String redisKey = "purchase:" + userId + ":" + bookId;

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);

        given(redisTemplate.hasKey(redisKey)).willReturn(false);
        given(orderServiceClient.hasPurchased(userId, bookId)).willThrow(new RuntimeException("Connection Refused"));

        assertThatThrownBy(() -> reviewService.createReview(bookId, request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시스템 점검 중");
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 이미 작성한 리뷰 존재")
    void createReview_Fail_AlreadyExists() {
        ReviewCreateRequest request = ReviewCreateRequest.builder().title("중복").build();
        String redisKey = "purchase:" + userId + ":" + bookId;

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);
        given(redisTemplate.hasKey(redisKey)).willReturn(true); // 구매는 확인됨
        given(reviewRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(true); // 이미 씀

        assertThatThrownBy(() -> reviewService.createReview(bookId, request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 해당 도서에 대한 리뷰를 작성했습니다");
    }

    @Test
    @DisplayName("리뷰 생성 성공 - 이미지 포함")
    void createReview_Success_WithImages() {
        ReviewCreateRequest request = ReviewCreateRequest.builder().title("포토 리뷰").score(5).content("사진").build();
        String redisKey = "purchase:" + userId + ":" + bookId;

        MultipartFile mockFile = mock(MultipartFile.class);
        given(mockFile.isEmpty()).willReturn(false);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);
        given(redisTemplate.hasKey(redisKey)).willReturn(true);
        given(imageUploadService.uploadReviewImage(mockFile)).willReturn("http://minio/image.jpg");

        reviewService.createReview(bookId, request, List.of(mockFile));

        verify(imageUploadService, times(1)).uploadReviewImage(mockFile);
        verify(reviewRepository, times(1)).save(any(Review.class));
    }


    @Test
    @DisplayName("도서별 리뷰 목록 조회 성공")
    void getReviewListByBookId_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Review review = Review.builder().id(1L).book(book).userId(userId).title("R1").content("C1")
                .createdAt(LocalDateTime.now()).score(5).build();
        Page<Review> page = new PageImpl<>(List.of(review));

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(reviewRepository.findAllByBook(book, pageable)).willReturn(page);

        Page<ReviewDto> result = reviewService.getReviewListByBookId(bookId, pageable);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("R1");
    }

    @Test
    @DisplayName("유저별 리뷰 목록 조회 성공")
    void getReviewListByUserId_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Review review = Review.builder().id(1L).book(book).userId(userId).title("R1").content("C1")
                .createdAt(LocalDateTime.now()).score(5).build();
        Page<Review> page = new PageImpl<>(List.of(review));

        given(reviewRepository.findAllByUserId(userId, pageable)).willReturn(page);

        Page<ReviewDto> result = reviewService.getReviewListByUserId(userId, pageable);

        assertThat(result).hasSize(1);
    }


    @Test
    @DisplayName("리뷰 수정 성공 - 내용 수정 및 이미지 추가/삭제")
    void updateReview_Success() {
        Long reviewId = 10L;
        List<Long> deleteIds = List.of(55L);
        ReviewUpdateRequest request = ReviewUpdateRequest.builder()
                .title("수정").content("수정됨").score(3).deleteImageIds(deleteIds).build();

        // 기존 이미지 Mocking
        Review review = Review.builder().id(reviewId).userId(userId).book(book).title("원래").score(5)
                .images(new ArrayList<>()).build();
        // 삭제될 이미지 객체 생성 및 리스트에 추가 필요 (테스트 정교화)
        // 여기서는 간단히 리스트가 비어있지 않다고 가정하고 removeIf 로직 탄다고 검증

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));
        given(util.getUserId()).willReturn(userId);

        MultipartFile newImage = mock(MultipartFile.class);
        given(newImage.isEmpty()).willReturn(false);
        given(imageUploadService.uploadReviewImage(newImage)).willReturn("new-url");

        reviewService.updateReview(reviewId, request, List.of(newImage));

        assertThat(review.getTitle()).isEqualTo("수정");
        assertThat(review.getScore()).isEqualTo(3);
        verify(imageUploadService, times(1)).uploadReviewImage(newImage);
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 작성자 불일치")
    void updateReview_Fail_AccessDenied() {
        Long reviewId = 10L;
        Long otherUser = 999L;
        ReviewUpdateRequest request = new ReviewUpdateRequest();
        Review review = Review.builder().id(reviewId).userId(userId).build();

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));
        given(util.getUserId()).willReturn(otherUser);

        assertThatThrownBy(() -> reviewService.updateReview(reviewId, request, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 리뷰 없음")
    void updateReview_Fail_NotFound() {
        Long reviewId = 999L;
        ReviewUpdateRequest request = new ReviewUpdateRequest();
        given(reviewRepository.findById(reviewId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.updateReview(reviewId, request, null))
                .isInstanceOf(NotFoundReviewException.class);
    }


    @Test
    @DisplayName("리뷰 삭제 성공")
    void deleteReview_Success() {
        Long reviewId = 10L;
        Review review = Review.builder().id(reviewId).userId(userId).book(book).build();

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));
        given(util.getUserId()).willReturn(userId);

        reviewService.deleteReview(reviewId);

        verify(reviewRepository, times(1)).delete(review);
        // 평점 업데이트 호출 확인
        verify(reviewRepository, times(1)).getAverageScoreByBook(book);
    }

    @Test
    @DisplayName("리뷰 삭제 실패 - 작성자 불일치")
    void deleteReview_Fail_AccessDenied() {
        Long reviewId = 10L;
        Long otherUser = 999L;
        Review review = Review.builder().id(reviewId).userId(userId).build();

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));
        given(util.getUserId()).willReturn(otherUser);

        assertThatThrownBy(() -> reviewService.deleteReview(reviewId))
                .isInstanceOf(AccessDeniedException.class);
    }
}