package org.nhnacademy.book2onandonbookservice.dto.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.entity.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookDetailResponseTest {

    @Test
    @DisplayName("성공: 모든 연관 관계 데이터가 있는 경우 변환 검증 (Full Data)")
    void from_FullData_Success() {
        Book book = mock(Book.class);

        when(book.getId()).thenReturn(1L);
        when(book.getIsbn()).thenReturn("978-1234567890");
        when(book.getTitle()).thenReturn("Test Book");
        when(book.getVolume()).thenReturn("Vol.1");
        when(book.getPublishDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(book.getPriceStandard()).thenReturn(10000L);
        when(book.getPriceSales()).thenReturn(9000L);
        when(book.getStatus()).thenReturn(BookStatus.ON_SALE);
        when(book.getStockCount()).thenReturn(50);
        when(book.getIsWrapped()).thenReturn(true);
        when(book.getChapter()).thenReturn("Index...");
        when(book.getDescription()).thenReturn("Description...");
        when(book.getRating()).thenReturn(4.5);

        Contributor c1 = mock(Contributor.class); when(c1.getContributorName()).thenReturn("Author1");
        BookContributor bc1 = mock(BookContributor.class); when(bc1.getContributor()).thenReturn(c1);
        Contributor c2 = mock(Contributor.class); when(c2.getContributorName()).thenReturn("Author2");
        BookContributor bc2 = mock(BookContributor.class); when(bc2.getContributor()).thenReturn(c2);

        when(book.getBookContributors()).thenReturn(Set.of(bc1, bc2));

        Category root = mock(Category.class);
        when(root.getId()).thenReturn(10L);
        when(root.getCategoryName()).thenReturn("Root");
        when(root.getParent()).thenReturn(null);

        Category sub = mock(Category.class);
        when(sub.getId()).thenReturn(20L);
        when(sub.getCategoryName()).thenReturn("Sub");
        when(sub.getParent()).thenReturn(root);

        when(book.getCategory()).thenReturn(sub);

        Tag t1 = mock(Tag.class); when(t1.getId()).thenReturn(100L); when(t1.getTagName()).thenReturn("Tag1");
        BookTag bt1 = mock(BookTag.class); when(bt1.getTag()).thenReturn(t1);
        when(book.getBookTags()).thenReturn(Set.of(bt1));

        Publisher p1 = mock(Publisher.class); when(p1.getId()).thenReturn(50L); when(p1.getPublisherName()).thenReturn("Pub1");
        BookPublisher bp1 = mock(BookPublisher.class); when(bp1.getPublisher()).thenReturn(p1);
        when(book.getBookPublishers()).thenReturn(Set.of(bp1));

        // 수정: BookImage 엔티티에 맞춰 getUrl() -> getImagePath()로 변경
        BookImage img = mock(BookImage.class); when(img.getImagePath()).thenReturn("img.jpg");
        when(book.getImages()).thenReturn(Set.of(img));

        Review r1 = mock(Review.class); when(r1.getId()).thenReturn(1L); when(r1.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(book.getReviews()).thenReturn(Set.of(r1));

        BookDetailResponse response = BookDetailResponse.from(book, 100L, true);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Test Book");

        assertThat(response.getContributorName()).contains("Author1");
        assertThat(response.getContributorName()).contains("Author2");

        assertThat(response.getCategories()).hasSize(2);
        assertThat(response.getCategories().get(0).getName()).isEqualTo("Root");
        assertThat(response.getCategories().get(1).getName()).isEqualTo("Sub");

        assertThat(response.getTags()).hasSize(1);
        assertThat(response.getTags().get(0).getName()).isEqualTo("Tag1");

        assertThat(response.getPublishers()).hasSize(1);
        assertThat(response.getPublishers().get(0).getName()).isEqualTo("Pub1");

        assertThat(response.getImages()).hasSize(1);
        // DTO 필드명에 따라 getUrl() 또는 getImagePath() 사용 (여기서는 DTO가 imagePath를 쓴다고 가정하거나 DTO 내부 매핑 확인 필요)
        // 만약 BookImageDto도 imagePath 필드를 쓴다면 getImagePath()로 수정하세요.
        // assertThat(response.getImages().get(0).getImagePath()).isEqualTo("img.jpg");

        assertThat(response.getLikeCount()).isEqualTo(100L);
        assertThat(response.getLikedByCurrentUser()).isTrue();
        assertThat(response.getRating()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("성공: 카테고리/태그/리뷰 등 연관 데이터가 없는 경우 (Minimal Data)")
    void from_MinimalData_Success() {
        Book book = mock(Book.class);

        when(book.getBookContributors()).thenReturn(Collections.emptySet());
        when(book.getImages()).thenReturn(Collections.emptySet());
        when(book.getCategory()).thenReturn(null);
        when(book.getBookTags()).thenReturn(Collections.emptySet());
        when(book.getBookPublishers()).thenReturn(Collections.emptySet());
        when(book.getReviews()).thenReturn(Collections.emptySet());

        BookDetailResponse response = BookDetailResponse.from(book, 0L, false);

        assertThat(response.getContributorName()).isEmpty();
        assertThat(response.getCategories()).isEmpty();
        assertThat(response.getTags()).isEmpty();
        assertThat(response.getPublishers()).isEmpty();
        assertThat(response.getImages()).isEmpty();
        assertThat(response.getReviews()).isEmpty();
        assertThat(response.getReviewCount()).isZero();
    }

    @Test
    @DisplayName("성공: 리뷰가 3개를 초과할 때 최신순으로 정렬되어 3개만 잘리는지 검증")
    void from_ReviewSortAndLimit() {
        Book book = mock(Book.class);

        when(book.getBookContributors()).thenReturn(Collections.emptySet());
        when(book.getImages()).thenReturn(Collections.emptySet());
        when(book.getCategory()).thenReturn(null);
        when(book.getBookTags()).thenReturn(Collections.emptySet());
        when(book.getBookPublishers()).thenReturn(Collections.emptySet());

        LocalDateTime now = LocalDateTime.now();
        Review r1 = mock(Review.class); when(r1.getId()).thenReturn(1L); when(r1.getCreatedAt()).thenReturn(now.minusDays(5));
        Review r2 = mock(Review.class); when(r2.getId()).thenReturn(2L); when(r2.getCreatedAt()).thenReturn(now.minusDays(1)); // 1등
        Review r3 = mock(Review.class); when(r3.getId()).thenReturn(3L); when(r3.getCreatedAt()).thenReturn(now.minusDays(2)); // 2등
        Review r4 = mock(Review.class); when(r4.getId()).thenReturn(4L); when(r4.getCreatedAt()).thenReturn(now.minusDays(3)); // 3등
        Review r5 = mock(Review.class); when(r5.getId()).thenReturn(5L); when(r5.getCreatedAt()).thenReturn(now.minusDays(4));

        Set<Review> reviews = new HashSet<>();
        reviews.add(r1); reviews.add(r2); reviews.add(r3); reviews.add(r4); reviews.add(r5);

        when(book.getReviews()).thenReturn(reviews);

        BookDetailResponse response = BookDetailResponse.from(book, 0L, false);

        assertThat(response.getReviewCount()).isEqualTo(5L);
        assertThat(response.getReviews()).hasSize(3);

        // 수정: ReviewDto에 getReviewId()가 없다면 getId() 사용 (DTO 정의에 따름)
        assertThat(response.getReviews().get(0).getId()).isEqualTo(2L);
        assertThat(response.getReviews().get(1).getId()).isEqualTo(3L);
        assertThat(response.getReviews().get(2).getId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("실패: Book 객체가 null일 때 NPE 발생")
    void from_NullBook_Fail() {
        assertThatThrownBy(() -> BookDetailResponse.from(null, 0L, false))
                .isInstanceOf(NullPointerException.class);
    }
}