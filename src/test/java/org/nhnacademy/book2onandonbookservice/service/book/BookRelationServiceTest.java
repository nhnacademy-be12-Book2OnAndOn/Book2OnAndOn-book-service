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
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.nhnacademy.book2onandonbookservice.repository.ContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

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

    @Mock
    private ImageUploadService imageUploadService;

    private Book book;

    @BeforeEach
    void setUp() {
        book = Book.builder()
                .id(1L)
                .bookTags(new HashSet<>())
                .bookPublishers(new HashSet<>())
                .bookContributors(new HashSet<>())
                .images(new HashSet<>())
                .build();
    }

    @Test
    @DisplayName("도서 등록 시 연관관계 설정")
    void applyRelationsForCreate() {
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "test image".getBytes());

        BookSaveRequest request = BookSaveRequest.builder()
                .categoryId(10L)
                .tagNames(Set.of("태그1"))
                .publisherIds(List.of(20L))
                .publisherName("신규 출판사")
                .contributorName("작가1, 작가2")
                .imagePath(List.of(imageFile))
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

        given(imageUploadService.uploadBookImage(any(MultipartFile.class))).willReturn("http://minio/uploaded-image.jpg");

        bookRelationService.applyRelationsForCreate(book, request);

        assertThat(book.getCategory()).isNotNull();
        assertThat(book.getCategory().getId()).isEqualTo(10L);
        assertThat(book.getBookTags()).hasSize(1);
        assertThat(book.getBookPublishers()).hasSize(2);
        assertThat(book.getBookContributors()).hasSize(2);
        assertThat(book.getImages()).hasSize(1);
    }

    @Test
    @DisplayName("도서 수정 시 카테고리 변경")
    void applyRelationForUpdate_Category() {
        Category cat1 = Category.builder().id(1L).build();
        book.setCategory(cat1);

        BookUpdateRequest request = BookUpdateRequest.builder()
                .categoryId(2L)
                .build();

        Category cat2 = Category.builder().id(2L).build();
        given(categoryRepository.findById(2L)).willReturn(Optional.of(cat2));

        bookRelationService.applyRelationsForUpdate(book, request);

        assertThat(book.getCategory()).isNotNull();
        assertThat(book.getCategory().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("도서 수정 시 null 필드는 유지")
    void applyRelationsForUpdate_NullFields() {
        book.getBookTags().add(mock(BookTag.class));
        Category originalCategory = Category.builder().id(1L).build();
        book.setCategory(originalCategory);

        BookUpdateRequest req = BookUpdateRequest.builder()
                .categoryId(null)
                .tagNames(null)
                .publisherIds(null)
                .contributorName(null)
                .imagePath(null)
                .build();

        bookRelationService.applyRelationsForUpdate(book, req);

        assertThat(book.getBookTags()).hasSize(1);
        assertThat(book.getCategory()).isEqualTo(originalCategory);
        verify(categoryRepository, never()).findById(any());
        verify(tagRepository, never()).findByTagName(any());
    }

    @Test
    @DisplayName("기여자 콤마 구분 파싱")
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
    @DisplayName("출판사 ID와 이름 병합")
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
    @DisplayName("이미지 빈 리스트면 추가 안함")
    void setImages_EmptyList() {
        BookSaveRequest request = BookSaveRequest.builder()
                .imagePath(List.of())
                .build();

        bookRelationService.applyRelationsForCreate(book, request);
        assertThat(book.getImages()).isEmpty();
    }

    @Test
    @DisplayName("여러 이미지 업로드")
    void setImages_Multiple() {
        MockMultipartFile image1 = new MockMultipartFile("image1", "test1.jpg", "image/jpeg", "test1".getBytes());
        MockMultipartFile image2 = new MockMultipartFile("image2", "test2.jpg", "image/jpeg", "test2".getBytes());

        BookSaveRequest request = BookSaveRequest.builder()
                .imagePath(List.of(image1, image2))
                .build();

        given(imageUploadService.uploadBookImage(any(MultipartFile.class)))
                .willReturn("http://minio/image1.jpg", "http://minio/image2.jpg");

        bookRelationService.applyRelationsForCreate(book, request);
        assertThat(book.getImages()).hasSize(2);
    }
}