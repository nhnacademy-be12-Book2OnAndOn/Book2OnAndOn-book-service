package org.nhnacademy.book2onandonbookservice.dto.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.entity.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookListResponseTest {

    @Test
    @DisplayName("성공: 모든 필드가 존재하고 썸네일 컬럼이 있는 경우 (Happy Path)")
    void from_FullData_WithDirectThumbnail() {
        Book book = mock(Book.class);
        when(book.getId()).thenReturn(1L);
        when(book.getTitle()).thenReturn("Test Book");
        when(book.getVolume()).thenReturn("Vol.1");
        when(book.getPriceStandard()).thenReturn(10000L);
        when(book.getPriceSales()).thenReturn(9000L);
        when(book.getRating()).thenReturn(4.5);
        when(book.getPublishDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(book.getThumbnail()).thenReturn("direct-thumb.jpg");

        Category root = mock(Category.class);
        when(root.getCategoryName()).thenReturn("Root");
        when(root.getParent()).thenReturn(null);

        Category sub = mock(Category.class);
        when(sub.getCategoryName()).thenReturn("Sub");
        when(sub.getParent()).thenReturn(root);

        when(book.getCategory()).thenReturn(sub);

        Contributor c = mock(Contributor.class); when(c.getContributorName()).thenReturn("Author");
        BookContributor bc = mock(BookContributor.class); when(bc.getContributor()).thenReturn(c);
        when(book.getBookContributors()).thenReturn(Set.of(bc));

        Publisher p = mock(Publisher.class); when(p.getPublisherName()).thenReturn("Pub");
        BookPublisher bp = mock(BookPublisher.class); when(bp.getPublisher()).thenReturn(p);
        when(book.getBookPublishers()).thenReturn(Set.of(bp));

        Tag t = mock(Tag.class); when(t.getTagName()).thenReturn("Tag");
        BookTag bt = mock(BookTag.class); when(bt.getTag()).thenReturn(t);
        when(book.getBookTags()).thenReturn(Set.of(bt));

        BookListResponse response = BookListResponse.from(book);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Test Book");
        assertThat(response.getThumbnail()).isEqualTo("direct-thumb.jpg");
        assertThat(response.getCategoryNames()).containsExactly("Root", "Sub");
        assertThat(response.getContributorNames()).containsExactly("Author");
        assertThat(response.getPublisherNames()).containsExactly("Pub");
        assertThat(response.getTagNames()).containsExactly("Tag");
    }

    @Test
    @DisplayName("성공: 썸네일 컬럼이 없고 이미지 리스트가 있는 경우 첫 번째 이미지 사용")
    void from_NoThumbnailColumn_WithImageList() {
        Book book = mock(Book.class);
        when(book.getThumbnail()).thenReturn(null);

        BookImage image1 = mock(BookImage.class); when(image1.getImagePath()).thenReturn("image1.jpg");
        BookImage image2 = mock(BookImage.class); when(image2.getImagePath()).thenReturn("image2.jpg");
        when(book.getImages()).thenReturn(Set.of(image1, image2));

        when(book.getCategory()).thenReturn(null);
        when(book.getBookContributors()).thenReturn(Collections.emptySet());
        when(book.getBookPublishers()).thenReturn(Collections.emptySet());
        when(book.getBookTags()).thenReturn(Collections.emptySet());

        BookListResponse response = BookListResponse.from(book);

        assertThat(response.getThumbnail()).isIn("image1.jpg", "image2.jpg");
    }

    @Test
    @DisplayName("성공: 썸네일 컬럼도 없고 이미지 리스트도 비어있는 경우 기본 이미지 사용")
    void from_NoThumbnail_NoImages() {
        Book book = mock(Book.class);
        when(book.getThumbnail()).thenReturn("");
        when(book.getImages()).thenReturn(Collections.emptySet());

        when(book.getCategory()).thenReturn(null);
        when(book.getBookContributors()).thenReturn(Collections.emptySet());
        when(book.getBookPublishers()).thenReturn(Collections.emptySet());
        when(book.getBookTags()).thenReturn(Collections.emptySet());

        BookListResponse response = BookListResponse.from(book);

        assertThat(response.getThumbnail()).isEqualTo("/images/no-image.png");
    }

    @Test
    @DisplayName("성공: 카테고리 및 연관 관계 데이터가 없는 경우 (Minimal Data)")
    void from_MinimalData() {
        Book book = mock(Book.class);
        when(book.getThumbnail()).thenReturn("thumb.jpg");
        when(book.getCategory()).thenReturn(null);
        when(book.getBookContributors()).thenReturn(Collections.emptySet());
        when(book.getBookPublishers()).thenReturn(Collections.emptySet());
        when(book.getBookTags()).thenReturn(Collections.emptySet());

        BookListResponse response = BookListResponse.from(book);

        assertThat(response.getCategoryNames()).isEmpty();
        assertThat(response.getContributorNames()).isEmpty();
        assertThat(response.getPublisherNames()).isEmpty();
        assertThat(response.getTagNames()).isEmpty();
    }

    @Test
    @DisplayName("성공: Lombok 생성자 및 Setter 테스트")
    void lombok_Methods_Success() {
        BookListResponse dto = new BookListResponse();
        dto.setId(1L);
        dto.setTitle("Title");
        dto.setVolume("Vol.1");
        dto.setPriceStandard(1000L);
        dto.setPriceSales(900L);
        dto.setRating(5.0);
        dto.setPublisherDate(LocalDate.now());
        dto.setThumbnail("img.jpg");
        dto.setContributorNames(List.of("C"));
        dto.setPublisherNames(List.of("P"));
        dto.setTagNames(List.of("T"));
        dto.setCategoryNames(List.of("Cat"));

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("Title");

        BookListResponse allArgs = new BookListResponse(
                1L, "Title", "Vol", 1000L, 900L, 4.5, BookStatus.BOOK_DELETED, LocalDate.now(),
                List.of("C"), List.of("P"), List.of("T"), List.of("Cat"), "thumb.jpg"
        );
        assertThat(allArgs.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("성공: Builder 테스트")
    void builder_Success() {
        BookListResponse dto = BookListResponse.builder()
                .id(1L)
                .title("Builder")
                .build();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("Builder");
    }

    @Test
    @DisplayName("실패: Book 객체가 null인 경우 NPE 발생")
    void from_NullBook_Fail() {
        assertThatThrownBy(() -> BookListResponse.from(null))
                .isInstanceOf(NullPointerException.class);
    }
}