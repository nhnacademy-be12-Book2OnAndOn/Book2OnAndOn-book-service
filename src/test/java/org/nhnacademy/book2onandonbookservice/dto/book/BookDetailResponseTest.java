package org.nhnacademy.book2onandonbookservice.dto.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.entity.Review;
import org.nhnacademy.book2onandonbookservice.entity.Tag;

class BookDetailResponseTest {

    @Test
    @DisplayName("Full Data Conversion")
    void from_FullData_Success() {
        Book book = mock(Book.class);
        LocalDate publishDate = LocalDate.of(2024, 1, 1);

        when(book.getId()).thenReturn(1L);
        when(book.getIsbn()).thenReturn("978-1234567890");
        when(book.getTitle()).thenReturn("Test Book");
        when(book.getVolume()).thenReturn("Vol.1");
        when(book.getPublishDate()).thenReturn(publishDate);
        when(book.getPriceStandard()).thenReturn(10000L);
        when(book.getPriceSales()).thenReturn(9000L);
        when(book.getStatus()).thenReturn(BookStatus.ON_SALE);
        when(book.getStockCount()).thenReturn(50);
        when(book.getIsWrapped()).thenReturn(true);
        when(book.getChapter()).thenReturn("Index...");
        when(book.getDescription()).thenReturn("Description...");
        when(book.getRating()).thenReturn(4.5);

        Contributor c1 = mock(Contributor.class);
        when(c1.getContributorName()).thenReturn("Author1");
        BookContributor bc1 = mock(BookContributor.class);
        when(bc1.getContributor()).thenReturn(c1);

        Contributor c2 = mock(Contributor.class);
        when(c2.getContributorName()).thenReturn("Author2");
        BookContributor bc2 = mock(BookContributor.class);
        when(bc2.getContributor()).thenReturn(c2);

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

        Tag t1 = mock(Tag.class);
        when(t1.getId()).thenReturn(100L);
        when(t1.getTagName()).thenReturn("Tag1");
        BookTag bt1 = mock(BookTag.class);
        when(bt1.getTag()).thenReturn(t1);
        when(book.getBookTags()).thenReturn(Set.of(bt1));

        Publisher p1 = mock(Publisher.class);
        when(p1.getId()).thenReturn(50L);
        when(p1.getPublisherName()).thenReturn("Pub1");
        BookPublisher bp1 = mock(BookPublisher.class);
        when(bp1.getPublisher()).thenReturn(p1);
        when(book.getBookPublishers()).thenReturn(Set.of(bp1));

        BookImage img = mock(BookImage.class);
        when(img.getImagePath()).thenReturn("img.jpg");
        when(book.getImages()).thenReturn(Set.of(img));

        Review r1 = mock(Review.class);
        when(r1.getId()).thenReturn(1L);
        when(r1.getCreatedAt()).thenReturn(LocalDate.now());
        when(book.getReviews()).thenReturn(Set.of(r1));
        when(r1.getBook()).thenReturn(book);


        BookDetailResponse response = BookDetailResponse.from(book, 100L, true);

        assertThat(response)
                .extracting(
                        BookDetailResponse::getId,
                        BookDetailResponse::getIsbn,
                        BookDetailResponse::getTitle,
                        BookDetailResponse::getVolume,
                        BookDetailResponse::getPublishDate,
                        BookDetailResponse::getPriceStandard,
                        BookDetailResponse::getPriceSales,
                        BookDetailResponse::getStatus,
                        BookDetailResponse::getStockCount,
                        BookDetailResponse::getIsWrapped,
                        BookDetailResponse::getChapter,
                        BookDetailResponse::getDescriptionHtml,
                        BookDetailResponse::getRating,
                        BookDetailResponse::getLikeCount,
                        BookDetailResponse::getLikedByCurrentUser,
                        BookDetailResponse::getReviewCount
                )
                .containsExactly(
                        1L, "978-1234567890", "Test Book", "Vol.1", publishDate,
                        10000L, 9000L, BookStatus.ON_SALE, 50, true,
                        "Index...", "Description...", 4.5, 100L, true, 1L
                );

        assertThat(response.getContributorName()).contains("Author1", "Author2");

        assertThat(response.getCategories())
                .extracting(org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto::getName)
                .containsExactly("Root", "Sub");

        assertThat(response.getTags())
                .extracting(org.nhnacademy.book2onandonbookservice.dto.common.TagDto::getName)
                .containsExactly("Tag1");

        assertThat(response.getPublishers())
                .extracting(org.nhnacademy.book2onandonbookservice.dto.common.PublisherDto::getName)
                .containsExactly("Pub1");

        assertThat(response.getImages())
                .extracting(BookImageDto::getUrl)
                .containsExactly("img.jpg");

        assertThat(response.getReviews()).hasSize(1);
    }

    @Test
    @DisplayName("Minimal Data Conversion")
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
    @DisplayName("Review Sort and Limit")
    void from_ReviewSortAndLimit() {
        Book book = mock(Book.class);

        when(book.getBookContributors()).thenReturn(Collections.emptySet());
        when(book.getImages()).thenReturn(Collections.emptySet());
        when(book.getCategory()).thenReturn(null);
        when(book.getBookTags()).thenReturn(Collections.emptySet());
        when(book.getBookPublishers()).thenReturn(Collections.emptySet());

        LocalDate now = LocalDate.now();
        Review r1 = mock(Review.class);
        when(r1.getId()).thenReturn(1L);
        when(r1.getCreatedAt()).thenReturn(now.minusDays(5));
        when(r1.getBook()).thenReturn(book);

        Review r2 = mock(Review.class);
        when(r2.getId()).thenReturn(2L);
        when(r2.getCreatedAt()).thenReturn(now.minusDays(1));
        when(r2.getBook()).thenReturn(book);


        Review r3 = mock(Review.class);
        when(r3.getId()).thenReturn(3L);
        when(r3.getCreatedAt()).thenReturn(now.minusDays(2));
        when(r3.getBook()).thenReturn(book);


        Review r4 = mock(Review.class);
        when(r4.getId()).thenReturn(4L);
        when(r4.getCreatedAt()).thenReturn(now.minusDays(3));
        when(r4.getBook()).thenReturn(book);


        Review r5 = mock(Review.class);
        when(r5.getId()).thenReturn(5L);
        when(r5.getCreatedAt()).thenReturn(now.minusDays(4));
        when(r5.getBook()).thenReturn(book);


        Set<Review> reviews = new HashSet<>();
        reviews.add(r1);
        reviews.add(r2);
        reviews.add(r3);
        reviews.add(r4);
        reviews.add(r5);

        when(book.getReviews()).thenReturn(reviews);

        BookDetailResponse response = BookDetailResponse.from(book, 0L, false);

        assertThat(response.getReviewCount()).isEqualTo(5L);
        assertThat(response.getReviews()).hasSize(3);
        assertThat(response.getReviews().get(0).getId()).isEqualTo(2L);
        assertThat(response.getReviews().get(1).getId()).isEqualTo(3L);
        assertThat(response.getReviews().get(2).getId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("Category Parent Null Check")
    void from_CategoryParentNull() {
        Book book = mock(Book.class);

        when(book.getBookContributors()).thenReturn(Collections.emptySet());
        when(book.getImages()).thenReturn(Collections.emptySet());
        when(book.getBookTags()).thenReturn(Collections.emptySet());
        when(book.getBookPublishers()).thenReturn(Collections.emptySet());
        when(book.getReviews()).thenReturn(Collections.emptySet());

        Category root = mock(Category.class);
        when(root.getId()).thenReturn(10L);
        when(root.getCategoryName()).thenReturn("Root");
        when(root.getParent()).thenReturn(null);

        when(book.getCategory()).thenReturn(root);

        BookDetailResponse response = BookDetailResponse.from(book, 0L, false);

        assertThat(response.getCategories()).hasSize(1);
        assertThat(response.getCategories().get(0).getId()).isEqualTo(10L);
        assertThat(response.getCategories().get(0).getParentId()).isNull();
    }

    @Test
    @DisplayName("Null Book Exception")
    void from_NullBook_Fail() {
        assertThatThrownBy(() -> BookDetailResponse.from(null, 0L, false))
                .isInstanceOf(NullPointerException.class);
    }
}