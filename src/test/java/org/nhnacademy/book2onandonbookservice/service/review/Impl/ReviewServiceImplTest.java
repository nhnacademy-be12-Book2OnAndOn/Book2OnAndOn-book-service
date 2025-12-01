package org.nhnacademy.book2onandonbookservice.service.review.Impl;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.ReviewRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

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

    @Test
    @DisplayName("리뷰 생성 성공 - 구매했고, 리뷰 처음 씀")
    void createReview() {
        Long bookId = 1L;
        Long userId = 100L;
        Long reviewId = 10L;
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .title("좋아요")
                .score(5)
                .content("내용이 알차요 배송도 빨라요")
                .build();
        Book book = Book.builder().id(bookId).rating(0.0).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);
        given(orderServiceClient.hasPurchased(userId, bookId)).willReturn(true);
        given(reviewRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(false);
        given(reviewRepository.getAverageScoreByBook(book)).willReturn(5.0);

        reviewService.createReview(bookId, request, Collections.emptyList());

        verify(reviewRepository, times(1)).save(any(Review.class));
        verify(reviewRepository, times(1)).getAverageScoreByBook(book);

    }

    @Test
    @DisplayName("리뷰 생성 실패 - 구매했고, 리뷰 처음 씀")
    void createReviewFail() {
        Long bookId = 1L;
        Long userId = 100L;

        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .title("구매 안했음")
                .score(5)
                .content("리뷰 안써질거 같은데 안써져야만함")
                .build();
        Book book = Book.builder().id(bookId).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);
        given(orderServiceClient.hasPurchased(userId, bookId)).willReturn(false);

        assertThatThrownBy(() -> reviewService.createReview(bookId, request, null)).isInstanceOf(
                IllegalArgumentException.class).hasMessage("해당 도서를 구매후 배송이 완료된 회원만 리뷰 작성 가능합니다.");
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 구매했고, 리뷰 한 번 썼음")
    void createReviewFail_AlreadyExists() {
        Long bookId = 1L;
        Long userId = 100L;

        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .title("나 리뷰 또 써요")
                .score(5)
                .content("리뷰 안써질거 같은데 안써져야만함")
                .build();
        Book book = Book.builder().id(bookId).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(util.getUserId()).willReturn(userId);
        given(orderServiceClient.hasPurchased(userId, bookId)).willReturn(true);
        given(reviewRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(bookId, request, null)).isInstanceOf(
                IllegalArgumentException.class).hasMessage("이미 해당 도서에 대한 리뷰를 작성했습니다.");
    }

    @Test
    @DisplayName("특정도서에대한 리뷰목록 반환 성공")
    void getReviewListByBookId() {
        Long bookId = 1L;

        Book book = Book.builder().id(bookId).title("테스트 책").build();

        Review review1 = Review.builder()
                .id(1L)
                .book(book)
                .userId(100L)
                .title("첫 리뷰")
                .content("내용1")
                .score(5)
                .createdAt(LocalDateTime.now())
                .build();
        Review review2 = Review.builder()
                .id(2L)
                .book(book)
                .userId(200L)
                .title("둘 리뷰")
                .content("내용2")
                .score(5)
                .createdAt(LocalDateTime.now())
                .build();

        List<Review> reviews = List.of(review1, review2);

        Pageable pageable = PageRequest.of(0, 10);

        Page<Review> mockPage = new PageImpl<>(reviews, pageable, reviews.size());

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(reviewRepository.findAllByBook(book, pageable)).willReturn(mockPage);

        Page<ReviewDto> reviewDtos = reviewService.getReviewListByBookId(bookId, pageable);

        assertThat(reviewDtos).isNotNull();
        assertThat(reviewDtos.getContent()).hasSize(2);
        assertThat(reviewDtos.getContent().get(0).getTitle()).isEqualTo("첫 리뷰");
        assertThat(reviewDtos.getTotalElements()).isEqualTo(2);

        verify(reviewRepository, times(1)).findAllByBook(book, pageable);
    }

    @Test
    @DisplayName("특정 도서 리뷰 목록 조회 실패 - 책이 없을때")
    void getReviewListByBookId_Fail() {
        Long bookId = 999L;
        Pageable pageable = PageRequest.of(0, 10);

        given(bookRepository.findById(bookId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.getReviewListByBookId(bookId, pageable)).isInstanceOf(
                NotFoundBookException.class);
    }

    @Test
    @DisplayName("특정 유저 리뷰 목록 조회 성공")
    void getReviewListByUserId() {

        Long bookId1 = 1L;
        Long bookId2 = 2L;
        Long userId = 1L;

        Book book1 = Book.builder().id(bookId1).title("테스트 책").build();
        Book book2 = Book.builder().id(bookId2).title("테스트 책2").build();

        Review review1 = Review.builder()
                .id(1L)
                .book(book1)
                .userId(1L)
                .title("첫 리뷰")
                .content("내용1")
                .score(5)
                .createdAt(LocalDateTime.now())
                .build();
        Review review2 = Review.builder()
                .id(2L)
                .book(book2)
                .userId(1L)
                .title("둘 리뷰")
                .content("내용2")
                .score(5)
                .createdAt(LocalDateTime.now())
                .build();

        List<Review> reviews = List.of(review1, review2);

        Pageable pageable = PageRequest.of(0, 10);

        Page<Review> mockPage = new PageImpl<>(reviews, pageable, reviews.size());

        given(reviewRepository.findAllByUserId(userId, pageable)).willReturn(mockPage);

        Page<ReviewDto> reviewDtos = reviewService.getReviewListByUserId(userId, pageable);

        assertThat(reviewDtos).isNotNull();
        assertThat(reviewDtos.getContent().get(0).getTitle()).isEqualTo("첫 리뷰");
        assertThat(reviewDtos.getTotalElements()).isEqualTo(2);

        verify(reviewRepository, times(1)).findAllByUserId(userId, pageable);
    }

    @Test
    @DisplayName("특정 도서 리뷰 목록 조회 비어있음 - 작성한 리뷰가 없을때")
    void getReviewListByUserId_Empty() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Page<Review> mockPage = Page.empty();
        given(reviewRepository.findAllByUserId(userId, pageable)).willReturn(mockPage);

        Page<ReviewDto> result = reviewService.getReviewListByUserId(userId, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("리뷰 수정 성공")
    void updateReview() {
        Long reviewId = 1L;
        Long userId = 100L;
        Long bookId = 50L;

        ReviewUpdateRequest reviewUpdateRequest = ReviewUpdateRequest.builder()
                .title("수정 제목")
                .content("수정된내용")
                .deleteImageIds(new ArrayList<>())
                .score(3)
                .build();

        Book book = Book.builder().id(bookId).build();

        Review review = Review.builder()
                .id(reviewId)
                .userId(userId)
                .book(book)
                .title("원래 제목임")
                .content("원래 내용임")
                .score(4)
                .images(new ArrayList<>())
                .build();

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));
        given(util.getUserId()).willReturn(userId);
        given(reviewRepository.getAverageScoreByBook(book)).willReturn(4.0);

        reviewService.updateReview(reviewId, reviewUpdateRequest, Collections.emptyList());

        assertThat(review.getTitle()).isEqualTo("수정 제목");
        assertThat(review.getContent()).isEqualTo("수정된내용");
        assertThat(review.getScore()).isEqualTo(3);

        verify(reviewRepository, times(1)).getAverageScoreByBook(book);
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 작성자가 아닐때, 권한이 없을대")
    void updateReview_Fail() {

        Long reviewId = 1L;
        Long ownerId = 100L;
        Long intruderId = 200L;
        Long bookId = 1L;

        ReviewUpdateRequest request = new ReviewUpdateRequest();

        Review review = Review.builder()
                .id(reviewId)
                .userId(ownerId) //리뷰주인
                .build();
        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));
        given(util.getUserId()).willReturn(intruderId); //다른사람

        assertThatThrownBy(() -> reviewService.updateReview(reviewId, request, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("본인의 리뷰만 수정할 수 있습니다.");
    }


    @Test
    @DisplayName("리뷰 삭제 성공 - 본인 확인도 완료")
    void deleteReview() {

        Long reviewId = 1L;
        Long userId = 100L;
        Long bookId = 1L;

        Book book = Book.builder().id(bookId).build();

        Review review = Review.builder()
                .id(reviewId)
                .userId(userId)
                .book(book)
                .build();

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));
        given(util.getUserId()).willReturn(userId);
        given(reviewRepository.getAverageScoreByBook(book)).willReturn(0.0);

        reviewService.deleteReview(reviewId);

        verify(reviewRepository, times(1)).delete(review);
        verify(reviewRepository, times(1)).getAverageScoreByBook(book);

    }

    @Test
    @DisplayName("리뷰 삭제 실패 - 작성자가 아님(권한 XX)")
    void deleteReview_Fail() {
        Long reviewId = 1L;
        Long userId = 100L;

        Long intruderId = 200L;

        Review review = Review.builder()
                .id(reviewId)
                .userId(userId)
                .build();

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));
        given(util.getUserId()).willReturn(intruderId); //다른사람

        assertThatThrownBy(() -> reviewService.deleteReview(reviewId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("본인의 리뷰만 삭제할 수 있습니다.");
    }
}