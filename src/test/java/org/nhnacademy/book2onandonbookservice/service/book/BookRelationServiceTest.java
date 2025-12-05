package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookUpdateRequest;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookCategory;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.nhnacademy.book2onandonbookservice.repository.ContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;

@ExtendWith(MockitoExtension.class)
class BookRelationServiceTest {
    @InjectMocks
    private BookRelationService bookRelationService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private PublisherRepository publisherRepository;

    @Mock
    private ContributorRepository contributorRepository;

    private Book book;

    @BeforeEach
    void setUp() {
        book = Book.builder()
                .id(1L)
                .bookCategories(new HashSet<>())
                .bookTags(new HashSet<>())
                .bookPublishers(new HashSet<>())
                .bookContributors(new HashSet<>())
                .images(new HashSet<>())
                .build();
    }

    @Test
    @DisplayName("도서 등록 시 연관관계 설정 - 모든 정보가 있을 때 정상 동작")
    void applyRelationsForCreate() {
        BookSaveRequest request = BookSaveRequest.builder()
                .categoryIds(List.of(10L))
                .tagNames(Set.of("태그1"))
                .publisherIds(List.of(20L))
                .publisherName("신규 출판사")
                .contributorName("작가1, 작가2")
                .imagePath("image.jpg")
                .build();

        Category category = Category.builder().id(10L).categoryName("카테고리").build();
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));

        given(tagRepository.findByTagName("태그1")).willReturn(Optional.empty());
        given(tagRepository.save(any(Tag.class))).willAnswer(inv -> inv.getArgument(0));

        Publisher pub = Publisher.builder().id(20L).publisherName("기존").build();
        given(publisherRepository.findAllById(anyList())).willReturn(List.of(pub));

        given(publisherRepository.findByPublisherName("신규 출판사")).willReturn(Optional.empty());
        given(publisherRepository.save(any(Publisher.class))).willAnswer(inv -> inv.getArgument(0));

        given(contributorRepository.findByContributorName(anyString())).willReturn(Optional.empty());
        given(contributorRepository.save(any(Contributor.class))).willAnswer(inv -> inv.getArgument(0));

        bookRelationService.applyRelationsForCreate(book, request);

        assertThat(book.getBookCategories()).hasSize(1);
        assertThat(book.getBookTags()).hasSize(1);
        assertThat(book.getBookPublishers()).hasSize(2);
        assertThat(book.getBookContributors()).hasSize(2);
        assertThat(book.getImages()).hasSize(1);
    }

    @Test
    @DisplayName("도서 수정 시 카테고리 로직 검증")
    void applyRelationForUpdate_CategoryDiff() {
        Category cat1 = Category.builder().id(1L).build();
        Category cat2 = Category.builder().id(2L).build();

        BookCategory bc1 = BookCategory.builder().book(book).category(cat1).build();
        BookCategory bc2 = BookCategory.builder().book(book).category(cat2).build();

        book.getBookCategories().add(bc1);
        book.getBookCategories().add(bc2);

        BookUpdateRequest request = BookUpdateRequest.builder()
                .categoryIds(List.of(2L, 3L))
                .build();

        Category cat3 = Category.builder().id(3L).build();
        given(categoryRepository.findById(3L)).willReturn(Optional.of(cat3));

        bookRelationService.applyRelationsForUpdate(book, request);

        Set<BookCategory> result = book.getBookCategories();
        assertThat(result).hasSize(2);

        List<Long> currentIds = result.stream()
                .map(bc -> bc.getCategory().getId())
                .toList();

        assertThat(currentIds).contains(2L, 3L).doesNotContain(1L);
    }

    @Test
    @DisplayName("도서 수정 시 요청 필드가 null이면 기존 유지")
    void applyRelationsForUpdate_NullFields() {
        book.getBookTags().add(mock(BookTag.class));

        BookUpdateRequest req = BookUpdateRequest.builder()
                .categoryIds(null)
                .tagNames(null)
                .publisherIds(null)
                .contributorName(null)
                .imagePath(null)
                .build();

        bookRelationService.applyRelationsForUpdate(book, req);

        assertThat(book.getBookTags()).hasSize(1);
        verify(categoryRepository, never()).findById(any());
        verify(tagRepository, never()).findByTagName(any());
    }

    @Test
    @DisplayName("기여자(작가) 파싱 로직 검증: 콤마 구분 및 공백 제거")
    void setContributors_Comma() {
        String contributorStr = " 작가A, 작가B ";
        BookSaveRequest request = BookSaveRequest.builder()
                .contributorName(contributorStr)
                .build();

        given(contributorRepository.findByContributorName("작가A")).willReturn(
                Optional.of(Contributor.builder().contributorName("작가A").build()));
        given(contributorRepository.findByContributorName("작가B")).willReturn(Optional.empty());
        given(contributorRepository.save(any(Contributor.class))).willAnswer(inv -> inv.getArgument(0));

        bookRelationService.applyRelationsForCreate(book, request);

        assertThat(book.getBookContributors()).hasSize(2);
        List<String> names = book.getBookContributors().stream()
                .map(bc -> bc.getContributor().getContributorName())
                .toList();

        assertThat(names).containsExactlyInAnyOrder("작가A", "작가B");
    }

    @Test
    @DisplayName("출판사 설정 검증: ID 목록과 이름이 모두 제공될 때 병합")
    void setPublishers_Merge() {
        BookSaveRequest request = BookSaveRequest.builder()
                .publisherIds(List.of(100L))
                .publisherName("직접 입력 출판사")
                .build();

        Publisher pub1 = Publisher.builder().id(100L).publisherName("DB출판사").build();
        given(publisherRepository.findAllById(anyList())).willReturn(List.of(pub1));

        given(publisherRepository.findByPublisherName("직접 입력 출판사")).willReturn(Optional.empty());
        given(publisherRepository.save(any(Publisher.class))).willAnswer(inv -> {
            Publisher p = inv.getArgument(0);
            return Publisher.builder().id(200L).publisherName(p.getPublisherName()).build();
        });

        bookRelationService.applyRelationsForCreate(book, request);
        assertThat(book.getBookPublishers()).hasSize(2);
    }

    @Test
    @DisplayName("이미지 설정 검증: 빈 문자열이나 null이 오면 추가하지않음")
    void setImages_EmptyOrNull() {
        BookSaveRequest request = BookSaveRequest.builder()
                .imagePath("")
                .build();

        bookRelationService.applyRelationsForCreate(book, request);
        assertThat(book.getImages()).isEmpty();
    }
}