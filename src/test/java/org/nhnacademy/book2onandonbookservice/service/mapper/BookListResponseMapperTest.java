package org.nhnacademy.book2onandonbookservice.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchDocument;
import org.springframework.test.util.ReflectionTestUtils;

class BookListResponseMapperTest {

    private final BookListResponseMapper mapper = new BookListResponseMapper();

    @Test
    @DisplayName("fromEntity: 모든 연관관계(작가, 출판사, 카테고리, 태그)가 포함된 변환")
    void fromEntity_FullData() {
        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        book.setTitle("Test Title");
        book.setVolume("1");
        book.setPriceStandard(10000L);
        book.setPriceSales(9000L);
        book.setThumbnail("thumb.jpg");

        Contributor contributor = Contributor.builder().contributorName("Author Kim").build();
        BookContributor bookContributor = BookContributor.builder().contributor(contributor).build();
        book.setBookContributors(new HashSet<>(List.of(bookContributor)));

        Publisher publisher = Publisher.builder().publisherName("Test Pub").build();
        BookPublisher bookPublisher = BookPublisher.builder().publisher(publisher).build();
        book.setBookPublishers(new HashSet<>(List.of(bookPublisher)));

        Category root = Category.builder().categoryName("Root").parent(null).build();
        Category child = Category.builder().categoryName("Child").parent(root).build();
        book.setCategory(child);

        Tag tag = Tag.builder().tagName("Java").build();
        BookTag bookTag = BookTag.builder().tag(tag).build();
        book.setBookTags(new HashSet<>(List.of(bookTag)));

        BookListResponse result = mapper.fromEntity(book);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Test Title");
        assertThat(result.getContributorNames()).containsExactly("Author Kim");
        assertThat(result.getPublisherNames()).containsExactly("Test Pub");
        assertThat(result.getCategoryNames()).containsExactly("Root", "Child");
        assertThat(result.getTagNames()).containsExactly("Java");
        assertThat(result.getPriceSales()).isEqualTo(9000L);
    }

    @Test
    @DisplayName("fromEntity: 연관관계가 비어있고 카테고리가 없는 경우")
    void fromEntity_EmptyRelations() {
        Book book = new Book();
        book.setBookContributors(new HashSet<>());
        book.setBookPublishers(new HashSet<>());
        book.setBookTags(new HashSet<>());
        book.setCategory(null);

        BookListResponse result = mapper.fromEntity(book);

        assertThat(result.getContributorNames()).isEmpty();
        assertThat(result.getPublisherNames()).isEmpty();
        assertThat(result.getCategoryNames()).isEmpty();
        assertThat(result.getTagNames()).isEmpty();
    }

    @Test
    @DisplayName("fromDocument: ES 문서 객체에서 DTO 변환")
    void fromDocument_FullData() {
        BookSearchDocument doc = new BookSearchDocument();
        ReflectionTestUtils.setField(doc, "id", 100L);
        ReflectionTestUtils.setField(doc, "title", "Search Title");
        ReflectionTestUtils.setField(doc, "volume", "2");
        ReflectionTestUtils.setField(doc, "priceStandard", 20000L);
        ReflectionTestUtils.setField(doc, "priceSales", 18000L);
        ReflectionTestUtils.setField(doc, "imagePath", "img.png");
        ReflectionTestUtils.setField(doc, "contributorNames", List.of("Author Lee"));
        ReflectionTestUtils.setField(doc, "publisherNames", List.of("Pub A"));
        ReflectionTestUtils.setField(doc, "categoryNames", List.of("Cat A", "Cat B"));
        ReflectionTestUtils.setField(doc, "tagNames", List.of("Tag A"));

        BookListResponse result = mapper.fromDocument(doc);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getTitle()).isEqualTo("Search Title");
        assertThat(result.getThumbnail()).isEqualTo("img.png");
        assertThat(result.getContributorNames()).contains("Author Lee");
        assertThat(result.getPublisherNames()).contains("Pub A");
        assertThat(result.getCategoryNames()).contains("Cat A", "Cat B");
        assertThat(result.getTagNames()).contains("Tag A");
    }
}