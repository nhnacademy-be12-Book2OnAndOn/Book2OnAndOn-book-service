package org.nhnacademy.book2onandonbookservice.service.review.Impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.UserServiceClient;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewCreateRequest;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewDto;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewEventRequest;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewUpdateRequest;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Review;
import org.nhnacademy.book2onandonbookservice.entity.ReviewImage;
import org.nhnacademy.book2onandonbookservice.exception.BookNotPurchasedException;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundReviewException;
import org.nhnacademy.book2onandonbookservice.exception.ReviewAlreadyExistsException;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.ReviewRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.service.review.PurchaseVerificationService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    ImageUploadService imageUploadService;
    @Mock
    UserHeaderUtil util;
    @Mock
    UserServiceClient userServiceClient;
    @Mock
    PurchaseVerificationService purchaseVerificationService; // Redis/OrderService 로직 위임

    private Book book;
    private final Long bookId = 1L;
    private final Long userId = 100L;

    @BeforeEach
    void setUp() {
        book = Book.builder().id(bookId).rating(0.0).build();
    }

    @Test
    @DisplayName("리뷰 생성 성공 - 구매 인증 성공 및 알림 전송")
    void createReview_Success() {
        // Given
        ReviewCreateRequest request = ReviewCreateRequest.builder().title("굿").score(5).content("내용").build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);

        // 구매 검증 통과 (Redis/Feign 로직은 Mock으로 처리)
        given(purchaseVerificationService.verifyPurchase(userId, bookId)).willReturn(true);
        // 중복 리뷰 없음
        given(reviewRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(false);
        // 평점 계산 Mock
        given(reviewRepository.getAverageScoreByBook(book)).willReturn(5.0);

        // When
        reviewService.createReview(bookId, request, Collections.emptyList());

        // Then
        verify(reviewRepository, times(1)).save(any(Review.class));

        // User Service 알림 전송 검증
        ArgumentCaptor<ReviewEventRequest> captor = ArgumentCaptor.forClass(ReviewEventRequest.class);
        verify(userServiceClient).notifyReviewCreated(captor.capture());
        assertThat(captor.getValue().isHasImage()).isFalse();
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 구매하지 않은 도서 (BookNotPurchasedException)")
    void createReview_Fail_NotPurchased() {
        // Given
        ReviewCreateRequest request = ReviewCreateRequest.builder().title("구매 안 함").build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);

        // 구매 검증 실패
        given(purchaseVerificationService.verifyPurchase(userId, bookId)).willReturn(false);

        // When & Then
        assertThatThrownBy(() -> reviewService.createReview(bookId, request, null))
                .isInstanceOf(BookNotPurchasedException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 이미 작성한 리뷰 존재 (ReviewAlreadyExistsException)")
    void createReview_Fail_AlreadyExists() {
        // Given
        ReviewCreateRequest request = ReviewCreateRequest.builder().title("중복").build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);

        // 구매는 했으나
        given(purchaseVerificationService.verifyPurchase(userId, bookId)).willReturn(true);
        // 이미 리뷰가 존재함
        given(reviewRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(true);

        // When & Then
        assertThatThrownBy(() -> reviewService.createReview(bookId, request, null))
                .isInstanceOf(ReviewAlreadyExistsException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("리뷰 생성 성공 - 이미지 포함")
    void createReview_Success_WithImages() {
        // Given
        ReviewCreateRequest request = ReviewCreateRequest.builder().title("포토 리뷰").score(5).content("사진").build();
        MultipartFile mockFile = mock(MultipartFile.class);
        given(mockFile.isEmpty()).willReturn(false);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);
        given(purchaseVerificationService.verifyPurchase(userId, bookId)).willReturn(true);
        given(reviewRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(false);
        given(imageUploadService.uploadReviewImage(mockFile)).willReturn("http://minio/image.jpg");

        // When
        reviewService.createReview(bookId, request, List.of(mockFile));

        // Then
        verify(imageUploadService, times(1)).uploadReviewImage(mockFile);
        verify(reviewRepository, times(1)).save(any(Review.class));

        // 알림 전송 시 hasImage=true 인지 확인
        ArgumentCaptor<ReviewEventRequest> captor = ArgumentCaptor.forClass(ReviewEventRequest.class);
        verify(userServiceClient).notifyReviewCreated(captor.capture());
        assertThat(captor.getValue().isHasImage()).isTrue();
    }

    @Test
    @DisplayName("리뷰 생성 성공 - User Service 알림 실패 시 예외를 삼키고 로그만 남김 (Resilience)")
    void createReview_Success_EvenIfUserClientFails() {
        // Given
        ReviewCreateRequest request = ReviewCreateRequest.builder().title("안전한 리뷰").score(5).content("내용").build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);
        given(purchaseVerificationService.verifyPurchase(userId, bookId)).willReturn(true);
        given(reviewRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(false);

        // User Service 호출 시 예외 발생 설정
        doThrow(new RuntimeException("User Service Down")).when(userServiceClient).notifyReviewCreated(any());

        // When & Then
        assertThatCode(() -> reviewService.createReview(bookId, request, Collections.emptyList()))
                .doesNotThrowAnyException(); // 예외가 전파되지 않아야 함

        verify(reviewRepository, times(1)).save(any(Review.class));
    }

    @Test
    @DisplayName("도서별 리뷰 목록 조회 성공")
    void getReviewListByBookId_Success() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Review review = Review.builder().id(1L).book(book).userId(userId).title("R1").content("C1")
                .createdAt(LocalDate.now()).score(5).build();
        Page<Review> page = new PageImpl<>(List.of(review));

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(reviewRepository.findAllByBook(book, pageable)).willReturn(page);

        // When
        Page<ReviewDto> result = reviewService.getReviewListByBookId(bookId, pageable);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("R1");
    }

    @Test
    @DisplayName("도서별 리뷰 조회 실패 - 책이 없는 경우")
    void getReviewListByBookId_Fail_NotFoundBook() {
        // Given
        given(bookRepository.findById(bookId)).willReturn(Optional.empty());
        Pageable pageable = Pageable.unpaged();

        // When & Then
        assertThatThrownBy(() -> reviewService.getReviewListByBookId(bookId, pageable))
                .isInstanceOf(NotFoundBookException.class);
    }

    @Test
    @DisplayName("유저별 리뷰 목록 조회 성공")
    void getReviewListByUserId_Success() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Review review = Review.builder().id(1L).book(book).userId(userId).title("R1").content("C1")
                .createdAt(LocalDate.now()).score(5).build();
        Page<Review> page = new PageImpl<>(List.of(review));

        given(reviewRepository.findAllByUserId(userId, pageable)).willReturn(page);

        // When
        Page<ReviewDto> result = reviewService.getReviewListByUserId(userId, pageable);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("R1");
    }

    @Test
    @DisplayName("리뷰 수정 성공 - 내용 및 이미지 추가/삭제")
    void updateReview_Success() {
        // Given
        Long reviewId = 10L;
        Long imageIdToDelete = 55L;
        List<Long> deleteIds = List.of(imageIdToDelete);

        ReviewUpdateRequest request = ReviewUpdateRequest.builder()
                .title("수정").content("수정됨").score(3).deleteImageIds(deleteIds).build();

        // 삭제할 이미지 Mock
        ReviewImage imageToDelete = ReviewImage.builder().id(imageIdToDelete).imagePath("delete/path.jpg").build();
        List<ReviewImage> currentImages = new ArrayList<>();
        currentImages.add(imageToDelete);

        Review review = Review.builder()
                .id(reviewId).userId(userId).book(book)
                .title("원래").score(5).images(currentImages)
                .build();

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));
        given(util.getUserId()).willReturn(userId);

        MultipartFile newImage = mock(MultipartFile.class);
        given(newImage.isEmpty()).willReturn(false);
        given(imageUploadService.uploadReviewImage(newImage)).willReturn("new-url");

        // When
        reviewService.updateReview(reviewId, request, List.of(newImage));

        // Then
        assertThat(review.getTitle()).isEqualTo("수정");
        assertThat(review.getScore()).isEqualTo(3);
        verify(imageUploadService).remove("delete/path.jpg"); // 이미지 삭제 호출 확인
        verify(imageUploadService).uploadReviewImage(newImage); // 새 이미지 업로드 확인
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 작성자 불일치 (AccessDeniedException)")
    void updateReview_Fail_AccessDenied() {
        // Given
        Long reviewId = 10L;
        Long otherUser = 999L;
        ReviewUpdateRequest request = new ReviewUpdateRequest();
        Review review = Review.builder().id(reviewId).userId(userId).build();

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));
        // 현재 로그인한 사용자가 다른 사람임
        given(util.getUserId()).willReturn(otherUser);

        // When & Then
        assertThatThrownBy(() -> reviewService.updateReview(reviewId, request, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("본인의 리뷰만 수정");
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 리뷰 없음 (NotFoundReviewException)")
    void updateReview_Fail_NotFound() {
        // Given
        Long reviewId = 999L;
        ReviewUpdateRequest request = new ReviewUpdateRequest();
        given(reviewRepository.findById(reviewId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> reviewService.updateReview(reviewId, request, null))
                .isInstanceOf(NotFoundReviewException.class);
    }
}