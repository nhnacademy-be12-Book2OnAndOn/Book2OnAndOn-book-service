package org.nhnacademy.book2onandonbookservice.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BookTest {

    @Test
    @DisplayName("Builder를 통한 객체 생성 및 기본값 확인")
    void createBook_Builder_Success() {
        String title = "Test Book";
        String isbn = "1234567890123";

        Book book = Book.builder()
                .title(title)
                .isbn(isbn)
                .priceStandard(10000L)
                .isWrapped(true)
                .publishDate(LocalDate.now())
                .status(BookStatus.ON_SALE)
                .build();

        assertThat(book.getTitle()).isEqualTo(title);
        assertThat(book.getIsbn()).isEqualTo(isbn);
        assertThat(book.getRating()).isEqualTo(0.0);
        assertThat(book.getLikeCount()).isEqualTo(0L);
        assertThat(book.getImages()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("NoArgsConstructor 및 Setter 동작 확인")
    void createBook_NoArgs_Setter_Success() {
        Book book = new Book();
        String volume = "Vol.1";
        Category category = mock(Category.class);

        book.setVolume(volume);
        book.setCategory(category);
        book.setDescription("Description");
        book.setChapter("Chapter 1");
        book.setPriceSales(9000L);
        book.setStockCount(50);
        book.setThumbnail("thumb.jpg");

        assertThat(book.getVolume()).isEqualTo(volume);
        assertThat(book.getCategory()).isEqualTo(category);
        assertThat(book.getDescription()).isEqualTo("Description");
        assertThat(book.getChapter()).isEqualTo("Chapter 1");
        assertThat(book.getPriceSales()).isEqualTo(9000L);
        assertThat(book.getStockCount()).isEqualTo(50);
        assertThat(book.getThumbnail()).isEqualTo("thumb.jpg");
    }

    @Test
    @DisplayName("평점 업데이트 (updateRating)")
    void updateRating_Success() {
        Book book = Book.builder().build();
        Double newRating = 4.5;

        book.updateRating(newRating);

        assertThat(book.getRating()).isEqualTo(newRating);
    }

    @Test
    @DisplayName("좋아요 증가 (increaseLikeCount)")
    void increaseLikeCount_Success() {
        Book book = Book.builder().build();

        book.increaseLikeCount();

        assertThat(book.getLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("좋아요 감소 (decreaseLikeCount)")
    void decreaseLikeCount_Success() {
        Book book = Book.builder().build();
        ReflectionTestUtils.setField(book, "likeCount", 5L);

        book.decreaseLikeCount();

        assertThat(book.getLikeCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("출판사 추가 및 확인 (addPublisher, hasPublisher)")
    void publisher_Logic_Success() {
        Book book = Book.builder().build();
        Publisher publisher = mock(Publisher.class);

        book.addPublisher(publisher);

        assertThat(book.getBookPublishers()).hasSize(1);
        assertThat(book.hasPublisher(publisher)).isTrue();
    }

    @Test
    @DisplayName("연관관계 컬렉션 Setter 테스트")
    void relationship_Setters_Success() {
        Book book = new Book();
        Set<BookImage> images = new HashSet<>();
        Set<BookTag> tags = new HashSet<>();
        Set<BookContributor> contributors = new HashSet<>();
        Set<BookPublisher> publishers = new HashSet<>();
        Set<Review> reviews = new HashSet<>();
        Set<BookLike> likes = new HashSet<>();

        book.setImages(images);
        book.setBookTags(tags);
        book.setBookContributors(contributors);
        book.setBookPublishers(publishers);
        book.setReviews(reviews);
        book.setLikes(likes);

        assertThat(book.getImages()).isSameAs(images);
        assertThat(book.getBookTags()).isSameAs(tags);
        assertThat(book.getBookContributors()).isSameAs(contributors);
        assertThat(book.getBookPublishers()).isSameAs(publishers);
        assertThat(book.getReviews()).isSameAs(reviews);
        assertThat(book.getLikes()).isSameAs(likes);
    }

    @Test
    @DisplayName("좋아요가 0일 때 감소 시도 시 0 유지")
    void decreaseLikeCount_FromZero() {
        Book book = Book.builder().build();

        book.decreaseLikeCount();

        assertThat(book.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("좋아요가 Null일 때 증가 시도 (Null Safe)")
    void increaseLikeCount_Null_Safe() {
        Book book = new Book();
        ReflectionTestUtils.setField(book, "likeCount", null);

        book.increaseLikeCount();

        assertThat(book.getLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("좋아요가 Null일 때 감소 시도 (Null Safe)")
    void decreaseLikeCount_Null_Safe() {
        Book book = new Book();
        ReflectionTestUtils.setField(book, "likeCount", null);

        book.decreaseLikeCount();

        assertThat(book.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("가지고 있지 않은 출판사 확인 (hasPublisher)")
    void hasPublisher_False() {
        Book book = Book.builder().build();
        Publisher publisherA = mock(Publisher.class);
        Publisher publisherB = mock(Publisher.class);

        book.addPublisher(publisherA);

        boolean result = book.hasPublisher(publisherB);

        assertThat(result).isFalse();
    }
}