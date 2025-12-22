//package org.nhnacademy.book2onandonbookservice.service.book;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatCode;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyList;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.BDDMockito.willThrow;
//import static org.mockito.Mockito.doThrow;
//import static org.mockito.Mockito.lenient;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.timeout;
//import static org.mockito.Mockito.times;
//import static org.mockito.Mockito.verify;
//
//import java.time.LocalDate;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.nhnacademy.book2onandonbookservice.client.OrderServiceClient;
//import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
//import org.nhnacademy.book2onandonbookservice.dto.book.BookDetailResponse;
//import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
//import org.nhnacademy.book2onandonbookservice.dto.book.BookOrderResponse;
//import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
//import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
//import org.nhnacademy.book2onandonbookservice.dto.book.BookUpdateRequest;
//import org.nhnacademy.book2onandonbookservice.dto.book.CartResponse;
//import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
//import org.nhnacademy.book2onandonbookservice.entity.Book;
//import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
//import org.nhnacademy.book2onandonbookservice.entity.BookImage;
//import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
//import org.nhnacademy.book2onandonbookservice.entity.Category;
//import org.nhnacademy.book2onandonbookservice.entity.Contributor;
//import org.nhnacademy.book2onandonbookservice.entity.Publisher;
//import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
//import org.nhnacademy.book2onandonbookservice.exception.OutOfStockException;
//import org.nhnacademy.book2onandonbookservice.repository.BookLikeRepository;
//import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
//import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
//import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
//import org.nhnacademy.book2onandonbookservice.service.mapper.BookListResponseMapper;
//import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.web.multipart.MultipartFile;
//
//@ExtendWith(MockitoExtension.class)
//class BookServiceImplTest {
//
//    @InjectMocks
//    private BookServiceImpl bookService;
//
//    @Mock
//    private BookRepository bookRepository;
//    @Mock
//    private BookSearchIndexService bookSearchIndexService;
//    @Mock
//    private BookValidator bookValidator;
//    @Mock
//    private BookFactory bookFactory;
//    @Mock
//    private BookRelationService bookRelationService;
//    @Mock
//    private BookLikeRepository bookLikeRepository;
//    @Mock
//    private CategoryRepository categoryRepository;
//    @Mock
//    private OrderServiceClient orderServiceClient;
//    @Mock
//    private BookListResponseMapper bookListResponseMapper;
//    @Mock
//    private BookHistoryService bookHistoryService;
//    @Mock
//    private ImageUploadService imageUploadService;
//
//    private Book bookA;
//    private Pageable pageable;
//
//    @BeforeEach
//    void setUp() {
//        pageable = PageRequest.of(0, 10);
//
//        Contributor mockContributorEntity = mock(Contributor.class);
//        lenient().when(mockContributorEntity.getId()).thenReturn(50L);
//        lenient().when(mockContributorEntity.getContributorName()).thenReturn("Test Author");
//
//        BookContributor mockBookContributor = mock(BookContributor.class);
//        lenient().when(mockBookContributor.getContributor()).thenReturn(mockContributorEntity);
//
//        Publisher mockPublisherEntity = mock(Publisher.class);
//        lenient().when(mockPublisherEntity.getId()).thenReturn(60L);
//        lenient().when(mockPublisherEntity.getPublisherName()).thenReturn("Test Publisher");
//
//        BookPublisher mockBookPublisher = mock(BookPublisher.class);
//        lenient().when(mockBookPublisher.getPublisher()).thenReturn(mockPublisherEntity);
//
//        BookImage image1 = BookImage.builder().id(100L).imagePath("url1").isThumbnail(true).build();
//        BookImage image2 = BookImage.builder().id(101L).imagePath("url2").isThumbnail(false).build();
//
//        bookA = Book.builder()
//                .id(1L)
//                .title("Book A")
//                .publishDate(LocalDate.of(2023, 1, 1))
//                .priceStandard(10000L)
//                .rating(5.0)
//                .isWrapped(false)
//                .isbn("9791191370215")
//                .status(BookStatus.ON_SALE)
//                .stockCount(100)
//                .priceSales(9000L)
//                .images(new HashSet<>(List.of(image1, image2)))
//                .bookContributors(new HashSet<>(List.of(mockBookContributor)))
//                .bookPublishers(new HashSet<>(List.of(mockBookPublisher)))
//                .bookTags(new HashSet<>())
//                .reviews(new HashSet<>())
//                .likes(new HashSet<>())
//                .thumbnail("url1")
//                .build();
//    }
//
//    @Test
//    void createBook() {
//        BookSaveRequest request = new BookSaveRequest();
//        MultipartFile file = mock(MultipartFile.class);
//        given(file.isEmpty()).willReturn(false);
//        List<MultipartFile> files = List.of(file);
//
//        given(bookFactory.createFrom(any(BookSaveRequest.class))).willReturn(bookA);
//        given(imageUploadService.uploadBookImage(any())).willReturn("new-url");
//        given(bookRepository.save(any(Book.class))).willReturn(bookA);
//
//        Long saveId = bookService.createBook(request, files);
//
//        assertThat(saveId).isEqualTo(bookA.getId());
//        verify(bookValidator).validateForCreate(request);
//        verify(bookRepository).save(bookA);
//        verify(bookSearchIndexService).index(bookA);
//    }
//
//    @Test
//    void createBook_ExternalUrl() {
//        BookSaveRequest request = new BookSaveRequest();
//        request.setImageUrl("http://external.com/img.jpg");
//        List<MultipartFile> files = Collections.emptyList();
//
//        given(bookFactory.createFrom(any(BookSaveRequest.class))).willReturn(bookA);
//        given(imageUploadService.uploadImageFromUrl(any())).willReturn("internal-url");
//        given(bookRepository.save(any(Book.class))).willReturn(bookA);
//
//        bookService.createBook(request, files);
//
//        verify(imageUploadService).uploadImageFromUrl("http://external.com/img.jpg");
//    }
//
//    @Test
//    void createBook_ESIndexFail() {
//        BookSaveRequest request = new BookSaveRequest();
//        given(bookFactory.createFrom(any(BookSaveRequest.class))).willReturn(bookA);
//        given(bookRepository.save(any(Book.class))).willReturn(bookA);
//        willThrow(new RuntimeException("ES Error")).given(bookSearchIndexService).index(bookA);
//
//        assertThatCode(() -> bookService.createBook(request, null)).doesNotThrowAnyException();
//
//        verify(bookRepository).save(bookA);
//    }
//
//    @Test
//    void updateBook_DeleteImage_And_AddImage() {
//        Long bookId = 1L;
//        BookUpdateRequest request = new BookUpdateRequest();
//        request.setDeleteImageIds(List.of(100L));
//
//        MultipartFile newFile = mock(MultipartFile.class);
//        given(newFile.isEmpty()).willReturn(false);
//        List<MultipartFile> newImages = List.of(newFile);
//
//        given(bookRepository.findByIdWithRelations(bookId)).willReturn(Optional.of(bookA));
//        given(imageUploadService.uploadBookImage(newFile)).willReturn("new-image-url");
//
//        bookService.updateBook(bookId, request, newImages);
//
//        assertThat(bookA.getImages()).hasSize(2);
//        assertThat(bookA.getImages().stream().anyMatch(img -> img.getImagePath().equals("new-image-url"))).isTrue();
//        assertThat(bookA.getThumbnail()).isNotNull();
//
//        verify(bookRelationService).applyRelationsForUpdate(bookA, request);
//        verify(imageUploadService).remove("url1");
//    }
//
//    @Test
//    void updateBook_DeleteImage_Failure_Catch() {
//        Long bookId = 1L;
//        BookUpdateRequest request = new BookUpdateRequest();
//        request.setDeleteImageIds(List.of(100L));
//        List<MultipartFile> newImages = Collections.emptyList();
//
//        given(bookRepository.findByIdWithRelations(bookId)).willReturn(Optional.of(bookA));
//        doThrow(new RuntimeException("MinIO Error")).when(imageUploadService).remove("url1");
//
//        assertThatCode(() -> bookService.updateBook(bookId, request, newImages)).doesNotThrowAnyException();
//    }
//
//    @Test
//    void updateBook_NotFound() {
//        Long bookId = 9999L;
//        BookUpdateRequest request = new BookUpdateRequest();
//        List<MultipartFile> images = Collections.emptyList();
//
//        given(bookRepository.findByIdWithRelations(bookId)).willReturn(Optional.empty());
//
//        assertThatThrownBy(() -> bookService.updateBook(bookId, request, images))
//                .isInstanceOf(NotFoundBookException.class);
//    }
//
//    @Test
//    void updateThumbnail() {
//        Long bookId = 1L;
//        Long targetImageId = 101L;
//
//        given(bookRepository.findByIdWithRelations(bookId)).willReturn(Optional.of(bookA));
//
//        bookService.updateThumbnail(bookId, targetImageId);
//
//        BookImage newThumb = bookA.getImages().stream().filter(img -> img.getId().equals(101L)).findFirst().get();
//        BookImage oldThumb = bookA.getImages().stream().filter(img -> img.getId().equals(100L)).findFirst().get();
//
//        assertThat(newThumb.isThumbnail()).isTrue();
//        assertThat(oldThumb.isThumbnail()).isFalse();
//        assertThat(bookA.getThumbnail()).isEqualTo("url2");
//        verify(bookSearchIndexService).index(bookA);
//    }
//
//    @Test
//    void updateThumbnail_ImageNotFound() {
//        Long bookId = 1L;
//        Long targetImageId = 999L;
//
//        given(bookRepository.findByIdWithRelations(bookId)).willReturn(Optional.of(bookA));
//
//        assertThatThrownBy(() -> bookService.updateThumbnail(bookId, targetImageId))
//                .isInstanceOf(IllegalArgumentException.class);
//    }
//
//    @Test
//    void deleteBook() {
//        Long bookId = 1L;
//        given(bookRepository.findById(bookId)).willReturn(Optional.of(bookA));
//
//        bookService.deleteBook(bookId);
//
//        verify(bookRepository).delete(bookA);
//        verify(bookRepository).flush();
//        verify(bookSearchIndexService).deleteIndex(bookId);
//        verify(imageUploadService, times(2)).remove(any());
//    }
//
//    @Test
//    void deleteBook_ExceptionFlow() {
//        Long bookId = 1L;
//        given(bookRepository.findById(bookId)).willReturn(Optional.of(bookA));
//        willThrow(new RuntimeException("ES Error")).given(bookSearchIndexService).deleteIndex(bookId);
//        doThrow(new RuntimeException("MinIO Error")).when(imageUploadService).remove(any());
//
//        assertThatCode(() -> bookService.deleteBook(bookId)).doesNotThrowAnyException();
//
//        verify(bookRepository).delete(bookA);
//    }
//
//    @Test
//    void getBookCount() {
//        given(bookRepository.count()).willReturn(10L);
//        assertThat(bookService.getBookCount()).isEqualTo(10L);
//    }
//
//    @Test
//    void getBooks() {
//        BookSearchCondition condition = new BookSearchCondition();
//        Page<Book> bookPage = new PageImpl<>(List.of(bookA), pageable, 1);
//        BookListResponse mockResponse = BookListResponse.builder().id(bookA.getId()).build();
//
//        given(bookRepository.findByStatusNot(BookStatus.BOOK_DELETED, pageable)).willReturn(bookPage);
//        given(bookListResponseMapper.fromEntity(bookA)).willReturn(mockResponse);
//
//        Page<BookListResponse> result = bookService.getBooks(condition, pageable);
//        assertThat(result).hasSize(1);
//    }
//
//    @Test
//    void getBookDetail_LoggedIn() {
//        Long bookId = 1L;
//        Long userId = 100L;
//        String guestId = "guest";
//
//        given(bookRepository.findByIdWithRelations(bookId)).willReturn(Optional.of(bookA));
//        given(bookLikeRepository.countByBookId(bookId)).willReturn(5L);
//        given(bookLikeRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(true);
//
//        BookDetailResponse result = bookService.getBookDetail(bookId, userId, guestId);
//
//        assertThat(result.getLikedByCurrentUser()).isTrue();
//        verify(bookHistoryService, timeout(100)).addRecentView(userId, guestId, bookId);
//    }
//
//    @Test
//    void getBookDetail_NotLoggedIn() {
//        Long bookId = 1L;
//        given(bookRepository.findByIdWithRelations(bookId)).willReturn(Optional.of(bookA));
//
//        BookDetailResponse result = bookService.getBookDetail(bookId, null, null);
//        assertThat(result.getLikedByCurrentUser()).isNull();
//    }
//
//    @Test
//    void getBestsellers() {
//        given(orderServiceClient.getBestSellersBookIds("DAILY")).willReturn(List.of(1L));
//        given(bookRepository.findAllById(List.of(1L))).willReturn(List.of(bookA));
//
//        List<BookListResponse> result = bookService.getBestsellers("DAILY");
//        assertThat(result).hasSize(1);
//    }
//
//    @Test
//    void getBestsellers_Empty() {
//        given(orderServiceClient.getBestSellersBookIds("DAILY")).willReturn(Collections.emptyList());
//        List<BookListResponse> result = bookService.getBestsellers("DAILY");
//        assertThat(result).isEmpty();
//    }
//
//    @Test
//    void getNewArrivals_WithCategory() {
//        Long categoryId = 10L;
//        Category root = Category.builder().id(10L).children(new ArrayList<>()).build();
//        Category child = Category.builder().id(11L).children(new ArrayList<>()).build();
//        root.getChildren().add(child);
//
//        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(root));
//        given(bookRepository.findBooksByCategoryIdsSorted(anyList(), any())).willReturn(new PageImpl<>(List.of(bookA)));
//
//        Page<BookListResponse> result = bookService.getNewArrivals(categoryId, pageable);
//        assertThat(result).hasSize(1);
//    }
//
//    @Test
//    void getNewArrivals_NoCategory() {
//        given(bookRepository.findAllByOrderByPublishDateDesc(pageable)).willReturn(new PageImpl<>(List.of(bookA)));
//        Page<BookListResponse> result = bookService.getNewArrivals(null, pageable);
//        assertThat(result).hasSize(1);
//    }
//
//    @Test
//    void getNewArrivals_EmptyBooks() {
//        given(bookRepository.findAllByOrderByPublishDateDesc(pageable)).willReturn(Page.empty());
//        Page<BookListResponse> result = bookService.getNewArrivals(null, pageable);
//        assertThat(result).isEmpty();
//    }
//
//    @Test
//    @DisplayName("주문용 도서 정보 다건 조회 성공")
//    void getBooksForOrder() {
//        List<Long> ids = List.of(1L);
//
//        Category category = Category.builder()
//                .id(100L)
//                .categoryName("테스트 카테고리")
//                .build();
//
//        bookA.setCategory(category);
//
//        given(bookRepository.findAllById(ids)).willReturn(List.of(bookA));
//
//        List<BookOrderResponse> result = bookService.getBooksForOrder(ids);
//
//        assertThat(result).hasSize(1);
//        assertThat(result.get(0).getCategoryId()).isEqualTo(100L);
//    }
//
//    @Test
//    void getBooksForOrder_Empty() {
//        assertThat(bookService.getBooksForOrder(null)).isEmpty();
//    }
//
//    @Test
//    void decreaseStock_Success() {
//        StockRequest req = StockRequest.builder().bookId(1L).quantity(1).build();
//        bookA.setStockCount(10);
//
//        given(bookRepository.decreaseStock(1L, 1)).willReturn(1);
//        given(bookRepository.findById(1L)).willReturn(Optional.of(bookA));
//
//        bookService.decreaseStock(new ArrayList<>(List.of(req)));
//        assertThat(bookA.getStatus()).isEqualTo(BookStatus.ON_SALE);
//    }
//
//    @Test
//    void decreaseStock_SoldOut() {
//        StockRequest req = StockRequest.builder().bookId(1L).quantity(100).build();
//        bookA.setStockCount(0);
//
//        given(bookRepository.decreaseStock(1L, 100)).willReturn(1);
//        given(bookRepository.findById(1L)).willReturn(Optional.of(bookA));
//
//        bookService.decreaseStock(new ArrayList<>(List.of(req)));
//        assertThat(bookA.getStatus()).isEqualTo(BookStatus.SOLD_OUT);
//    }
//
//    @Test
//    void decreaseStock_Fail_Count0() {
//        StockRequest req = StockRequest.builder().bookId(1L).quantity(1).build();
//        given(bookRepository.decreaseStock(1L, 1)).willReturn(0);
//
//        List<StockRequest> list = new ArrayList<>(List.of(req));
//        assertThatThrownBy(() -> bookService.decreaseStock(list)).isInstanceOf(OutOfStockException.class);
//    }
//
//    @Test
//    void increaseStock() {
//        StockRequest req = StockRequest.builder().bookId(1L).quantity(1).build();
//        bookA.setStatus(BookStatus.SOLD_OUT);
//        bookA.setStockCount(5);
//
//        given(bookRepository.findById(1L)).willReturn(Optional.of(bookA));
//
//        bookService.increaseStock(new ArrayList<>(List.of(req)));
//        assertThat(bookA.getStatus()).isEqualTo(BookStatus.ON_SALE);
//    }
//
//    @Test
//    void getPopularBooks() {
//        given(bookRepository.findByStatusOrderByLikeCountDesc(BookStatus.ON_SALE, pageable))
//                .willReturn(new PageImpl<>(List.of(bookA)));
//        Page<BookListResponse> result = bookService.getPopularBooks(pageable);
//        assertThat(result).hasSize(1);
//    }
//
//    @Test
//    void updateBookStatus() {
//        given(bookRepository.findById(1L)).willReturn(Optional.of(bookA));
//        bookService.updateBookStatus(1L, BookStatus.SOLD_OUT);
//        assertThat(bookA.getStatus()).isEqualTo(BookStatus.SOLD_OUT);
//        verify(bookSearchIndexService).index(bookA);
//    }
//
//    @Test
//    void updateBookStatus_ESFail() {
//        given(bookRepository.findById(1L)).willReturn(Optional.of(bookA));
//        willThrow(new RuntimeException("ES Fail")).given(bookSearchIndexService).index(bookA);
//
//        assertThatCode(() -> bookService.updateBookStatus(1L, BookStatus.SOLD_OUT))
//                .doesNotThrowAnyException();
//    }
//
//    @Test
//    void getRecentViews() {
//        given(bookHistoryService.getRecentViews(100L, "guest")).willReturn(List.of(1L));
//        given(bookRepository.findAllById(List.of(1L))).willReturn(List.of(bookA));
//
//        List<BookListResponse> result = bookService.getRecentViews(100L, "guest");
//        assertThat(result).hasSize(1);
//    }
//
//    @Test
//    void getRecentViews_Empty() {
//        given(bookHistoryService.getRecentViews(100L, "guest")).willReturn(Collections.emptyList());
//        assertThat(bookService.getRecentViews(100L, "guest")).isEmpty();
//    }
//
//    @Test
//    void mergeRecentViews() {
//        bookService.mergeRecentViews("guest", 100L);
//        verify(bookHistoryService).mergeHistory("guest", 100L);
//    }
//
//    @Test
//    void mergeRecentViews_InvalidInput() {
//        bookService.mergeRecentViews(null, 100L);
//        verify(bookHistoryService, never()).mergeHistory(any(), any());
//    }
//
//    @Test
//    void getBookSnapshots() {
//        given(bookRepository.findAllById(List.of(1L))).willReturn(List.of(bookA));
//        Map<Long, CartResponse> result = bookService.getBookSnapshots(List.of(1L));
//
//        assertThat(result).containsKey(1L);
//        assertThat(result.get(1L).getTitle()).isEqualTo("Book A");
//    }
//}