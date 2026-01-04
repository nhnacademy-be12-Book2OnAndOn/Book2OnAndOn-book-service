package org.nhnacademy.book2onandonbookservice.dto.book;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.entity.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookDetailResponseTest {

    private Book book;

    @BeforeEach
    void setUp() {
        book = mock(Book.class);
    }

    @Test
    @DisplayName("Full Data Conversion")
    void from_FullData_Success() {
        Set<BookContributor> contributors = createMockContributors("Author1", "Author2");
        Category category = createMockCategoryHierarchy();
        Set<BookTag> tags = createMockTags("Tag1");
        Set<BookPublisher> publishers = createMockPublishers("Pub1");
        BookImage image = createMockImage("img.jpg");
        Review review = createMockReview(1L, LocalDate.now());

        LocalDate publishDate = LocalDate.of(2024, 1, 1);
        setupBasicBookStub(publishDate);

        when(book.getBookContributors()).thenReturn(contributors);
        when(book.getCategory()).thenReturn(category);
        when(book.getBookTags()).thenReturn(tags);
        when(book.getBookPublishers()).thenReturn(publishers);
        when(book.getImages()).thenReturn(Set.of(image));
        when(book.getReviews()).thenReturn(Set.of(review));

        BookDetailResponse response = BookDetailResponse.from(book, 100L, true);

        assertBasicResponseInfo(response, publishDate);
        assertRelationInfo(response);
    }

    @Test
    @DisplayName("Minimal Data Conversion")
    void from_MinimalData_Success() {
        setupEmptyStub();

        BookDetailResponse response = BookDetailResponse.from(book, 0L, false);

        assertThat(response.getContributorName()).isEmpty();
        assertThat(response.getCategories()).isEmpty();
        assertThat(response.getReviewCount()).isZero();
    }

    @Test
    @DisplayName("Review Sort and Limit")
    void from_ReviewSortAndLimit() {
        setupEmptyStub();

        LocalDate now = LocalDate.now();

        Set<Review> reviews = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> {
                    Review r = mock(Review.class);
                    when(r.getCreatedAt()).thenReturn(now.minusDays(i));
                    lenient().when(r.getId()).thenReturn((long) i);
                    lenient().when(r.getBook()).thenReturn(book);
                    return r;
                })
                .collect(Collectors.toSet());

        when(book.getReviews()).thenReturn(reviews);

        BookDetailResponse response = BookDetailResponse.from(book, 0L, false);

        assertThat(response.getReviewCount()).isEqualTo(5L);
        assertThat(response.getReviews()).hasSize(3);
        assertThat(response.getReviews().get(0).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Category Parent Null Check")
    void from_CategoryParentNull() {
        setupEmptyStub();

        Category root = mock(Category.class);
        when(root.getId()).thenReturn(10L);
        when(root.getCategoryName()).thenReturn("Root");
        when(root.getParent()).thenReturn(null);
        when(book.getCategory()).thenReturn(root);

        BookDetailResponse response = BookDetailResponse.from(book, 0L, false);

        assertThat(response.getCategories()).hasSize(1);
        assertThat(response.getCategories().get(0).getParentId()).isNull();
    }

    @Test
    @DisplayName("Null Book Exception")
    void from_NullBook_Fail() {
        assertThatThrownBy(() -> BookDetailResponse.from(null, 0L, false))
                .isInstanceOf(NullPointerException.class);
    }

    private void assertBasicResponseInfo(BookDetailResponse response, LocalDate publishDate) {
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getIsbn()).isEqualTo("978-1234567890");
        assertThat(response.getTitle()).isEqualTo("Test Book");
        assertThat(response.getPublishDate()).isEqualTo(publishDate);
        assertThat(response.getPriceStandard()).isEqualTo(10000L);
        assertThat(response.getPriceSales()).isEqualTo(9000L);
        assertThat(response.getStatus()).isEqualTo(BookStatus.ON_SALE);
    }

    private void assertRelationInfo(BookDetailResponse response) {
        assertThat(response.getContributorName()).containsAnyOf("Author1", "Author2");
        assertThat(response.getCategories()).hasSize(2);
        assertThat(response.getTags().get(0).getName()).isEqualTo("Tag1");
        assertThat(response.getPublishers().get(0).getName()).isEqualTo("Pub1");
        assertThat(response.getLikeCount()).isEqualTo(100L);
        assertThat(response.getLikedByCurrentUser()).isTrue();
    }

    private void setupBasicBookStub(LocalDate publishDate) {
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
    }

    private void setupEmptyStub() {
        when(book.getBookContributors()).thenReturn(Collections.emptySet());
        when(book.getImages()).thenReturn(Collections.emptySet());
        when(book.getCategory()).thenReturn(null);
        when(book.getBookTags()).thenReturn(Collections.emptySet());
        when(book.getBookPublishers()).thenReturn(Collections.emptySet());
        when(book.getReviews()).thenReturn(Collections.emptySet());
    }

    private Set<BookContributor> createMockContributors(String... names) {
        return List.of(names).stream().map(name -> {
            Contributor c = mock(Contributor.class);
            when(c.getContributorName()).thenReturn(name);
            BookContributor bc = mock(BookContributor.class);
            when(bc.getContributor()).thenReturn(c);
            return bc;
        }).collect(Collectors.toSet());
    }

    private Category createMockCategoryHierarchy() {
        Category root = mock(Category.class);
        when(root.getId()).thenReturn(10L);
        when(root.getCategoryName()).thenReturn("Root");
        when(root.getParent()).thenReturn(null);

        Category sub = mock(Category.class);
        when(sub.getId()).thenReturn(20L);
        when(sub.getCategoryName()).thenReturn("Sub");
        when(sub.getParent()).thenReturn(root);
        return sub;
    }

    private Set<BookTag> createMockTags(String tagName) {
        Tag t = mock(Tag.class);
        when(t.getTagName()).thenReturn(tagName);
        BookTag bt = mock(BookTag.class);
        when(bt.getTag()).thenReturn(t);
        return Set.of(bt);
    }

    private Set<BookPublisher> createMockPublishers(String pubName) {
        Publisher p = mock(Publisher.class);
        when(p.getPublisherName()).thenReturn(pubName);
        BookPublisher bp = mock(BookPublisher.class);
        when(bp.getPublisher()).thenReturn(p);
        return Set.of(bp);
    }

    private BookImage createMockImage(String path) {
        BookImage img = mock(BookImage.class);
        when(img.getImagePath()).thenReturn(path);
        return img;
    }

    private Review createMockReview(Long id, LocalDate createdAt) {
        Review r = mock(Review.class);
        when(r.getId()).thenReturn(id);
        when(r.getCreatedAt()).thenReturn(createdAt);
        when(r.getBook()).thenReturn(book);
        return r;
    }
}