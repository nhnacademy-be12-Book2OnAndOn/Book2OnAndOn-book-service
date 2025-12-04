package org.nhnacademy.book2onandonbookservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiApiClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookCategory;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.repository.BookCategoryRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookTagRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class BookEnrichmentServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BookCategoryRepository bookCategoryRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private BookTagRepository bookTagRepository;
    @Mock
    private BookSearchIndexService bookSearchIndexService;
    @Mock
    private GeminiApiClient geminiApiClient;
    @Mock
    private AladinApiClient aladinApiClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private BookEnrichmentService bookEnrichmentService;

    private Book testBook;
    private AladinApiResponse.Item aladinItem;

    @BeforeEach
    void setUp() {
        testBook = Book.builder()
                .id(1L)
                .isbn("9788901234567")
                .title("테스트 책")
                .description("테스트 설명")
                .bookCategories(new HashSet<>())
                .bookTags(new HashSet<>())
                .images(new HashSet<>())
                .build();

        aladinItem = mock(AladinApiResponse.Item.class);
    }

    @Test
    @DisplayName("책이 존재하지 않을 때 처리")
    void enrichBookData_BookNotExists() {
        when(bookRepository.findById(1L)).thenReturn(Optional.empty());

        CompletableFuture<Void> result = bookEnrichmentService.enrichBookData(1L);

        result.join();
        assertThat(result).isCompleted();
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("ISBN으로 책을 찾을 수 없을 때")
    void enrichBookData_BookForIsbnNotFound() {
        when(bookRepository.existsById(1L)).thenReturn(true);

        when(bookRepository.findById(1L))
                .thenReturn(Optional.of(testBook))
                .thenReturn(Optional.empty());

        CompletableFuture<Void> result = bookEnrichmentService.enrichBookData(1L);

        result.join();
        assertThat(result).isCompleted();
    }

    @Test
    @DisplayName("알라딘 데이터로 책 정보 보강 성공")
    void enrichBookData_WithAladinData() {
        when(bookRepository.existsById(1L)).thenReturn(true);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(aladinItem);
        when(geminiApiClient.extractTags(anyString(), anyString()))
                .thenReturn(Arrays.asList("소설", "한국문학", "베스트셀러"));

        Tag tag1 = Tag.builder().id(1L).tagName("소설").build();
        Tag tag2 = Tag.builder().id(2L).tagName("한국문학").build();
        Tag tag3 = Tag.builder().id(3L).tagName("베스트셀러").build();

        when(tagRepository.findByTagName("소설")).thenReturn(Optional.of(tag1));
        when(tagRepository.findByTagName("한국문학")).thenReturn(Optional.of(tag2));
        when(tagRepository.findByTagName("베스트셀러")).thenReturn(Optional.of(tag3));
        when(bookTagRepository.existsByBookAndTag(any(), any())).thenReturn(false);

        doAnswer(invocation -> {
            invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        CompletableFuture<Void> result = bookEnrichmentService.enrichBookData(1L);

        result.join();
        verify(transactionTemplate).execute(any());
        verify(aladinApiClient).searchByIsbn(testBook.getIsbn());
    }

    @Test
    @DisplayName("외부 API 데이터가 없을 때 책 삭제 처리")
    void updateBookInTransaction_NoExternalData_DeleteBook() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        bookEnrichmentService.updateBookInTransaction(1L, null, null);

        assertThat(testBook.getStatus()).isEqualTo(BookStatus.BOOK_DELETED);
        verify(bookRepository).save(testBook);
        verify(bookSearchIndexService).deleteIndex(1L);
    }

    @Test
    @DisplayName("알라딘 데이터로 카테고리 저장")
    void updateBookInTransaction_SaveCategories() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinItem.getCategoryName()).thenReturn("국내도서>소설>한국소설");

        Category parentCategory = Category.builder().id(1L).categoryName("국내도서").build();
        Category middleCategory = Category.builder().id(2L).categoryName("소설").parent(parentCategory).build();
        Category leafCategory = Category.builder().id(3L).categoryName("한국소설").parent(middleCategory).build();

        when(categoryRepository.findByCategoryNameAndParentIsNull("국내도서"))
                .thenReturn(Optional.of(parentCategory));
        when(categoryRepository.findByCategoryNameAndParent("소설", parentCategory))
                .thenReturn(Optional.of(middleCategory));
        when(categoryRepository.findByCategoryNameAndParent("한국소설", middleCategory))
                .thenReturn(Optional.of(leafCategory));
        when(bookCategoryRepository.existsByBookAndCategory(any(), any())).thenReturn(false);

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        verify(bookCategoryRepository).save(any(BookCategory.class));
    }

    @Test
    @DisplayName("알라딘 데이터로 가격 정보 업데이트")
    void updateBookInTransaction_UpdatePrice() {
        testBook.setPriceStandard(null);
        testBook.setPriceSales(null);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinItem.getPriceStandard()).thenReturn(15000L);

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        assertThat(testBook.getPriceStandard()).isEqualTo(15000L);
        assertThat(testBook.getPriceSales()).isEqualTo(15000L);
        verify(bookRepository).save(testBook);
        verify(bookSearchIndexService).index(testBook);
    }

    @Test
    @DisplayName("알라딘 데이터로 출판일 업데이트")
    void updateBookInTransaction_UpdatePublishDate() {
        testBook.setPublishDate(null);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinItem.getPubDate()).thenReturn("2024-01-15");

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        assertThat(testBook.getPublishDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        verify(bookRepository).save(testBook);
    }

    @Test
    @DisplayName("알라딘 데이터로 설명 업데이트")
    void updateBookInTransaction_UpdateDescription() {
        testBook.setDescription(null);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinItem.getDescription()).thenReturn("알라딘 설명");

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        assertThat(testBook.getDescription()).isEqualTo("알라딘 설명");
        verify(bookRepository).save(testBook);
    }

    @Test
    @DisplayName("알라딘 데이터로 이미지 추가")
    void updateBookInTransaction_AddImage() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinItem.getCover()).thenReturn("http://example.com/cover.jpg");

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        assertThat(testBook.getImages()).hasSize(1);
        verify(bookRepository).save(testBook);
    }

    @Test
    @DisplayName("Gemini로 생성한 태그 저장")
    void updateBookInTransaction_SaveTags() {
        List<String> tags = Arrays.asList("소설", "한국문학", "베스트셀러");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        Tag tag1 = Tag.builder().id(1L).tagName("소설").build();
        Tag tag2 = Tag.builder().id(2L).tagName("한국문학").build();
        Tag tag3 = Tag.builder().id(3L).tagName("베스트셀러").build();

        when(tagRepository.findByTagName("소설")).thenReturn(Optional.of(tag1));
        when(tagRepository.findByTagName("한국문학")).thenReturn(Optional.of(tag2));
        when(tagRepository.findByTagName("베스트셀러")).thenReturn(Optional.of(tag3));
        when(bookTagRepository.existsByBookAndTag(any(), any())).thenReturn(false);

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, tags);

        verify(bookTagRepository, times(3)).save(any(BookTag.class));
    }

    @Test
    @DisplayName("새로운 태그 생성 및 저장")
    void updateBookInTransaction_CreateAndSaveNewTag() {
        List<String> tags = Arrays.asList("신규태그");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        Tag newTag = Tag.builder().id(10L).tagName("신규태그").build();

        when(tagRepository.findByTagName("신규태그")).thenReturn(Optional.empty());
        when(tagRepository.saveAndFlush(any(Tag.class))).thenReturn(newTag);
        when(bookTagRepository.existsByBookAndTag(any(), any())).thenReturn(false);

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, tags);

        verify(tagRepository).saveAndFlush(any(Tag.class));
        verify(bookTagRepository).save(any(BookTag.class));
    }

    @Test
    @DisplayName("태그 이름 50자로 자르기")
    void updateBookInTransaction_TruncateTagName() {
        String longTagName = "a".repeat(60);
        List<String> tags = Arrays.asList(longTagName);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        Tag tag = Tag.builder().id(1L).tagName(longTagName.substring(0, 50)).build();
        when(tagRepository.findByTagName(anyString())).thenReturn(Optional.of(tag));
        when(bookTagRepository.existsByBookAndTag(any(), any())).thenReturn(false);

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, tags);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(tagRepository).findByTagName(captor.capture());
        assertThat(captor.getValue()).hasSize(50);
    }

    @Test
    @DisplayName("빈 태그 이름은 무시")
    void updateBookInTransaction_IgnoreEmptyTagName() {
        List<String> tags = Arrays.asList("", "   ", "유효한태그");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        Tag tag = Tag.builder().id(1L).tagName("유효한태그").build();
        when(tagRepository.findByTagName("유효한태그")).thenReturn(Optional.of(tag));
        when(bookTagRepository.existsByBookAndTag(any(), any())).thenReturn(false);

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, tags);

        verify(bookTagRepository, times(1)).save(any(BookTag.class));
    }

    @Test
    @DisplayName("중복된 BookTag는 저장하지 않음")
    void updateBookInTransaction_SkipDuplicateBookTag() {
        List<String> tags = Arrays.asList("중복태그");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        Tag tag = Tag.builder().id(1L).tagName("중복태그").build();
        when(tagRepository.findByTagName("중복태그")).thenReturn(Optional.of(tag));
        when(bookTagRepository.existsByBookAndTag(testBook, tag)).thenReturn(true);

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, tags);

        verify(bookTagRepository, never()).save(any(BookTag.class));
    }

    @Test
    @DisplayName("카테고리 생성 시 이름 100자로 자르기")
    void updateBookInTransaction_TruncateCategoryName() {
        String longCategoryName = "a".repeat(120);
        when(aladinItem.getCategoryName()).thenReturn(longCategoryName);
        when(aladinItem.getPriceStandard()).thenReturn(10000L);

        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        Category category = Category.builder()
                .id(1L)
                .categoryName(longCategoryName.substring(0, 100))
                .build();
        when(categoryRepository.findByCategoryNameAndParentIsNull(anyString()))
                .thenReturn(Optional.of(category));
        when(bookCategoryRepository.existsByBookAndCategory(any(), any())).thenReturn(false);

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(categoryRepository).findByCategoryNameAndParentIsNull(captor.capture());
        assertThat(captor.getValue()).hasSize(100);
    }

    @Test
    @DisplayName("카테고리 생성 - 루트 카테고리")
    void updateBookInTransaction_CreateRootCategory() {
        when(aladinItem.getCategoryName()).thenReturn("새로운카테고리");
        when(aladinItem.getPriceStandard()).thenReturn(10000L);

        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        Category newCategory = Category.builder().id(1L).categoryName("새로운카테고리").build();
        when(categoryRepository.findByCategoryNameAndParentIsNull("새로운카테고리"))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(newCategory);
        when(bookCategoryRepository.existsByBookAndCategory(any(), any())).thenReturn(false);

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("중복된 BookCategory는 저장하지 않음")
    void updateBookInTransaction_SkipDuplicateBookCategory() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        when(aladinItem.getCategoryName()).thenReturn("국내도서");
        when(aladinItem.getPriceStandard()).thenReturn(10000L);

        Category category = Category.builder().id(1L).categoryName("국내도서").build();
        when(categoryRepository.findByCategoryNameAndParentIsNull(anyString()))
                .thenReturn(Optional.of(category));
        when(bookCategoryRepository.existsByBookAndCategory(testBook, category)).thenReturn(true);

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        verify(bookCategoryRepository, never()).save(any(BookCategory.class));
    }

    @Test
    @DisplayName("출판일 파싱 실패 시 현재 날짜로 설정")
    void updateBookInTransaction_InvalidDateFormat() {
        testBook.setPublishDate(null);
        when(aladinItem.getPubDate()).thenReturn("invalid-date");
        when(aladinItem.getPriceStandard()).thenReturn(10000L);

        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        assertThat(testBook.getPublishDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("API 호출 중 예외 발생 시 처리")
    void enrichBookData_ApiException() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.existsById(1L)).thenReturn(true);
        when(aladinApiClient.searchByIsbn(anyString())).thenThrow(new RuntimeException("API Error"));

        CompletableFuture<Void> result = bookEnrichmentService.enrichBookData(1L);
        result.join();
        assertThat(result).isCompleted();
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    @DisplayName("Gemini 태그 생성 실패 시 계속 진행")
    void enrichBookData_GeminiException() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.existsById(1L)).thenReturn(true);
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(aladinItem);
        when(geminiApiClient.extractTags(anyString(), anyString()))
                .thenThrow(new RuntimeException("Gemini Error"));

        doAnswer(invocation -> {
            invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        CompletableFuture<Void> result = bookEnrichmentService.enrichBookData(1L);

        result.join();
        verify(transactionTemplate).execute(any());
    }

    @Test
    @DisplayName("설명이 없을 때 Gemini 태그 생성 안함")
    void enrichBookData_NoDescriptionNoGemini() {
        testBook.setDescription(null);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.existsById(1L)).thenReturn(true);
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(aladinItem);

        doAnswer(invocation -> {
            invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        CompletableFuture<Void> result = bookEnrichmentService.enrichBookData(1L);

        result.join();
        verify(geminiApiClient, never()).extractTags(anyString(), anyString());
    }

    @Test
    @DisplayName("업데이트할 항목이 없을 때는 저장하지 않음")
    void updateBookInTransaction_NoUpdate() {

        testBook.setPriceStandard(15000L);
        testBook.setPriceSales(15000L);
        testBook.setDescription("기존 설명");
        testBook.setPublishDate(LocalDate.now());

        // 카테고리가 이미 있는 경우
        Category category = Category.builder().id(1L).categoryName("기존카테고리").build();
        BookCategory bookCategory = BookCategory.builder()
                .book(testBook)
                .category(category)
                .build();
        testBook.getBookCategories().add(bookCategory);

        // 이미지가 이미 있는 경우
        BookImage existingImage = BookImage.builder()
                .book(testBook)
                .imagePath("existing.jpg")
                .build();
        testBook.getImages().add(existingImage);

        // 태그가 이미 있는 경우
        Tag tag = Tag.builder().id(1L).tagName("기존태그").build();
        BookTag bookTag = BookTag.builder()
                .book(testBook)
                .tag(tag)
                .build();
        testBook.getBookTags().add(bookTag);

        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        // aladinItem은 있지만 모든 필드가 이미 채워져있어서 업데이트 안됨
        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        assertThat(testBook.getPriceStandard()).isEqualTo(15000L);
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 이미지가 있을 때 추가하지 않음")
    void updateBookInTransaction_ImageAlreadyExists() {
        BookImage existingImage = BookImage.builder()
                .book(testBook)
                .imagePath("existing.jpg")
                .build();
        testBook.getImages().add(existingImage);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        when(aladinItem.getPriceStandard()).thenReturn(10000L);

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        assertThat(testBook.getImages()).hasSize(1);
    }

    @Test
    @DisplayName("태그 저장 시 예외 발생해도 계속 진행")
    void updateBookInTransaction_TagSaveException() {
        List<String> tags = Arrays.asList("태그1", "태그2");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        when(tagRepository.findByTagName("태그1"))
                .thenThrow(new RuntimeException("DB Error"));

        Tag tag2 = Tag.builder().id(2L).tagName("태그2").build();
        when(tagRepository.findByTagName("태그2")).thenReturn(Optional.of(tag2));
        when(bookTagRepository.existsByBookAndTag(any(), any())).thenReturn(false);

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, tags);

        verify(bookTagRepository, times(1)).save(any(BookTag.class));
    }

    @Test
    @DisplayName("카테고리 경로가 빈 문자열일 때 처리")
    void updateBookInTransaction_EmptyCategoryPath() {
        when(aladinItem.getCategoryName()).thenReturn("");
        when(aladinItem.getPriceStandard()).thenReturn(10000L);

        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        verify(categoryRepository, never()).save(any());
        verify(bookCategoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("카테고리 연결 시 예외 발생해도 계속 진행")
    void updateBookInTransaction_BookCategorySaveException() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        when(aladinItem.getCategoryName()).thenReturn("국내도서");
        when(aladinItem.getPriceStandard()).thenReturn(10000L);

        Category category = Category.builder().id(1L).categoryName("국내도서").build();
        when(categoryRepository.findByCategoryNameAndParentIsNull(anyString()))
                .thenReturn(Optional.of(category));
        when(bookCategoryRepository.existsByBookAndCategory(any(), any())).thenReturn(false);
        when(bookCategoryRepository.save(any(BookCategory.class)))
                .thenThrow(new RuntimeException("Duplicate"));

        bookEnrichmentService.updateBookInTransaction(1L, aladinItem, null);

        verify(bookCategoryRepository).save(any(BookCategory.class));
        verify(bookRepository).save(testBook);
    }
}