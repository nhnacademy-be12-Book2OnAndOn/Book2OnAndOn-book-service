package org.nhnacademy.book2onandonbookservice.dto.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Review;
import org.nhnacademy.book2onandonbookservice.entity.ReviewImage;

class ReviewDtoTest {

    @Test
    @DisplayName("성공: Review 엔티티(이미지 포함)를 DTO로 변환")
    void from_Success_WithImages() {
        Review review = mock(Review.class);
        ReviewImage reviewImage = mock(ReviewImage.class);
        Book book = mock(Book.class);

        LocalDate now = LocalDate.now();

        when(review.getId()).thenReturn(1L);
        when(review.getUserId()).thenReturn(100L);
        when(review.getTitle()).thenReturn("Review Title");
        when(review.getContent()).thenReturn("Content");
        when(review.getScore()).thenReturn(5);
        when(review.getCreatedAt()).thenReturn(now);

        when(reviewImage.getId()).thenReturn(10L);
        when(reviewImage.getImagePath()).thenReturn("/img/review1.jpg");
        when(review.getImages()).thenReturn(List.of(reviewImage));
        when(review.getBook()).thenReturn(book);


        ReviewDto result = ReviewDto.from(review);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(100L);
        assertThat(result.getTitle()).isEqualTo("Review Title");
        assertThat(result.getContent()).isEqualTo("Content");
        assertThat(result.getScore()).isEqualTo(5);
        assertThat(result.getWriterDate()).isEqualTo(now);

        assertThat(result.getImages()).hasSize(1);
        assertThat(result.getImages().get(0).getId()).isEqualTo(10L);
        assertThat(result.getImages().get(0).getImagePath()).isEqualTo("/img/review1.jpg");
    }

    @Test
    @DisplayName("성공: 이미지가 없는 Review 엔티티 변환")
    void from_Success_NoImages() {
        Review review = mock(Review.class);
        LocalDate now = LocalDate.now();
        Book book = mock(Book.class);
        when(review.getId()).thenReturn(2L);
        when(review.getUserId()).thenReturn(200L);
        when(review.getTitle()).thenReturn("Title");
        when(review.getContent()).thenReturn("Content");
        when(review.getScore()).thenReturn(3);
        when(review.getCreatedAt()).thenReturn(now);
        when(review.getImages()).thenReturn(Collections.emptyList());
        when(review.getBook()).thenReturn(book);

        ReviewDto result = ReviewDto.from(review);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getImages()).isEmpty();
    }

    @Test
    @DisplayName("실패: Review 객체가 null인 경우 NPE 발생")
    void from_NullReview_Fail() {
        assertThatThrownBy(() -> ReviewDto.from(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("성공: Lombok 메서드 (Constructor, Builder, Getter) 테스트")
    void lombok_Methods_Success() {
        ReviewDto noArgs = new ReviewDto();
        assertThat(noArgs).isNotNull();

        ReviewDto allArgs = new ReviewDto(
                1L, 2L, 3L, "BookTitle","nickname","Title", "Content", 5, LocalDate.now(), Collections.emptyList()
        );
        assertThat(allArgs.getId()).isEqualTo(1L);
        assertThat(allArgs.getBookId()).isEqualTo(2L);
        assertThat(allArgs.getUserId()).isEqualTo(3L);
        assertThat(allArgs.getTitle()).isEqualTo("Title");
        assertThat(allArgs.getContent()).isEqualTo("Content");
        assertThat(allArgs.getScore()).isEqualTo(5);
        assertThat(allArgs.getImages()).isEmpty();

        ReviewDto builder = ReviewDto.builder()
                .id(10L)
                .title("Builder")
                .build();
        assertThat(builder.getId()).isEqualTo(10L);
        assertThat(builder.getTitle()).isEqualTo("Builder");
    }
}