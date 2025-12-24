package org.nhnacademy.book2onandonbookservice.service.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.entity.*;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchDocument;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookListResponseMapperTest {

    private BookListResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new BookListResponseMapper();
    }

    @Test
    @DisplayName("성공: Entity -> DTO 변환 (모든 필드 데이터 존재)")
    void fromEntity_FullData() {
        Book book = mock(Book.class);
        when(book.getId()).thenReturn(1L);
        when(book.getTitle()).thenReturn("JPA Book");
        when(book.getVolume()).thenReturn("Vol.1");
        when(book.getPriceStandard()).thenReturn(20000L);
        when(book.getPriceSales()).thenReturn(18000L);
        when(book.getThumbnail()).thenReturn("thumb.jpg");

        Contributor contributor = mock(Contributor.class);
        when(contributor.getContributorName()).thenReturn("Author Lee");
        BookContributor bc = mock(BookContributor.class);
        when(bc.getContributor()).thenReturn(contributor);
        when(book.getBookContributors()).thenReturn(Set.of(bc));

        Publisher publisher = mock(Publisher.class);
        when(publisher.getPublisherName()).thenReturn("NHN Press");
        BookPublisher bp = mock(BookPublisher.class);
        when(bp.getPublisher()).thenReturn(publisher);
        when(book.getBookPublishers()).thenReturn(Set.of(bp));

        Category root = mock(Category.class);
        when(root.getCategoryName()).thenReturn("IT");
        when(root.getParent()).thenReturn(null);

        Category sub = mock(Category.class);
        when(sub.getCategoryName()).thenReturn("Backend");
        when(sub.getParent()).thenReturn(root);
        when(book.getCategory()).thenReturn(sub);

        Tag tag = mock(Tag.class);
        when(tag.getTagName()).thenReturn("Java");
        BookTag bt = mock(BookTag.class);
        when(bt.getTag()).thenReturn(tag);
        when(book.getBookTags()).thenReturn(Set.of(bt));

        BookListResponse result = mapper.fromEntity(book);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("JPA Book");
        assertThat(result.getContributorNames()).containsExactly("Author Lee");
        assertThat(result.getPublisherNames()).containsExactly("NHN Press");
        assertThat(result.getCategoryNames()).containsExactly("IT", "Backend");
        assertThat(result.getTagNames()).containsExactly("Java");
        assertThat(result.getThumbnail()).isEqualTo("thumb.jpg");
    }

    @Test
    @DisplayName("성공: Entity -> DTO 변환 (연관 관계 데이터 없음)")
    void fromEntity_MinimalData() {
        Book book = mock(Book.class);
        when(book.getId()).thenReturn(2L);
        when(book.getTitle()).thenReturn("Empty Book");

        when(book.getBookContributors()).thenReturn(Collections.emptySet());
        when(book.getBookPublishers()).thenReturn(Collections.emptySet());
        when(book.getCategory()).thenReturn(null);
        when(book.getBookTags()).thenReturn(Collections.emptySet());

        BookListResponse result = mapper.fromEntity(book);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getContributorNames()).isEmpty();
        assertThat(result.getPublisherNames()).isEmpty();
        assertThat(result.getCategoryNames()).isEmpty();
        assertThat(result.getTagNames()).isEmpty();
    }

    @Test
    @DisplayName("성공: Document -> DTO 변환")
    void fromDocument_Success() {
        BookSearchDocument doc = mock(BookSearchDocument.class);
        when(doc.getId()).thenReturn(10L);
        when(doc.getTitle()).thenReturn("Elasticsearch Guide");
        when(doc.getVolume()).thenReturn("Edition 2");
        when(doc.getPriceStandard()).thenReturn(30000L);
        when(doc.getPriceSales()).thenReturn(27000L);
        when(doc.getImagePath()).thenReturn("es.png");
        when(doc.getContributorNames()).thenReturn(List.of("Kim"));
        when(doc.getPublisherNames()).thenReturn(List.of("TechPub"));
        when(doc.getCategoryNames()).thenReturn(List.of("Database"));
        when(doc.getTagNames()).thenReturn(List.of("Search"));

        BookListResponse result = mapper.fromDocument(doc);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getTitle()).isEqualTo("Elasticsearch Guide");
        assertThat(result.getThumbnail()).isEqualTo("es.png");
        assertThat(result.getContributorNames()).containsExactly("Kim");
        assertThat(result.getPublisherNames()).containsExactly("TechPub");
        assertThat(result.getCategoryNames()).containsExactly("Database");
        assertThat(result.getTagNames()).containsExactly("Search");
    }

    @Test
    @DisplayName("성공: Document -> DTO 변환 (Null Fields)")
    void fromDocument_NullFields() {
        BookSearchDocument doc = mock(BookSearchDocument.class);
        when(doc.getId()).thenReturn(20L);
        when(doc.getImagePath()).thenReturn(null);
        when(doc.getContributorNames()).thenReturn(null);

        BookListResponse result = mapper.fromDocument(doc);

        assertThat(result.getId()).isEqualTo(20L);
        assertThat(result.getThumbnail()).isNull();
        assertThat(result.getContributorNames()).isNull();
    }
}