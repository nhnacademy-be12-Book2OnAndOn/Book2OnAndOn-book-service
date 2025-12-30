package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.OrderServiceClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.book.BookDetailResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookOrderResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.book.BookUpdateRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.CartResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.repository.BookLikeRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.service.mapper.BookListResponseMapper;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils; // 핵심 import
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    @InjectMocks
    private BookServiceImpl bookService;

    @Mock private BookFactory bookFactory;
    @Mock private BookRelationService bookRelationService;
    @Mock private BookValidator bookValidator;
    @Mock private BookRepository bookRepository;
    @Mock private BookLikeRepository bookLikeRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BookSearchIndexService bookSearchIndexService;
    @Mock private BookListResponseMapper bookListResponseMapper;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private BookHistoryService bookHistoryService;
    @Mock private ImageUploadService imageUploadService;
    @Mock private StockService stockService;

    @Test
    @DisplayName("도서 생성 - 이미지 파일과 외부 URL 모두 있는 경우 (파일 우선 처리 확인)")
    void createBook_WithFileAndUrl() {

        BookSaveRequest request = BookSaveRequest.builder().imageUrl("http://external.com/img").build();
        List<MultipartFile> files = List.of(mock(MultipartFile.class));
        Book book = new Book();
        book.setImages(new HashSet<>());

        ReflectionTestUtils.setField(book, "id", 1L);

        when(bookFactory.createFrom(request)).thenReturn(book);
        when(imageUploadService.uploadBookImage(any())).thenReturn("s3/path/img.jpg");
        when(bookRepository.save(any(Book.class))).thenReturn(book);

        bookService.createBook(request, files);

        verify(bookValidator).validateForCreate(request);
        verify(bookSearchIndexService).index(book);

        assertThat(book.getImages()).hasSize(1);
        assertThat(book.getThumbnail()).isEqualTo("s3/path/img.jpg");
    }

    @Test
    @DisplayName("도서 생성 - ES 인덱싱 실패해도 DB 저장은 성공해야 함")
    void createBook_IndexFail() {

        BookSaveRequest request = BookSaveRequest.builder().build();
        Book book = new Book();
        book.setImages(new HashSet<>());
        ReflectionTestUtils.setField(book, "id", 1L);

        when(bookFactory.createFrom(request)).thenReturn(book);
        when(bookRepository.save(book)).thenReturn(book);
        doThrow(new RuntimeException("ES Fail")).when(bookSearchIndexService).index(book);

        bookService.createBook(request, null);

        verify(bookRepository).save(book);
    }

    @Test
    @DisplayName("도서 수정 - 재고 변경, 상태 변경, 이미지 삭제 및 추가")
    void updateBook_Complex() {

        Long bookId = 1L;
        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", bookId);
        book.setStockCount(10);
        book.setStatus(BookStatus.ON_SALE);

        BookImage oldImg = BookImage.builder().imagePath("old/path").book(book).build();
        ReflectionTestUtils.setField(oldImg, "id", 100L);

        book.setImages(new HashSet<>(Set.of(oldImg)));

        BookUpdateRequest request = BookUpdateRequest.builder()
                .stockCount(0)
                .deleteImageIds(List.of(100L))
                .build();

        MultipartFile newFile = mock(MultipartFile.class);
        List<MultipartFile> newImages = List.of(newFile);

        when(bookRepository.findByIdWithRelationsForUpdate(bookId)).thenReturn(Optional.of(book));
        when(imageUploadService.uploadBookImage(newFile)).thenReturn("new/path");

        bookService.updateBook(bookId, request, newImages);

        verify(stockService).increaseStock(bookId, -10);
        assertThat(book.getStatus()).isEqualTo(BookStatus.SOLD_OUT);
        verify(imageUploadService).remove("old/path");
        assertThat(book.getImages()).hasSize(1);
        verify(bookSearchIndexService).index(book);
    }

    @Test
    @DisplayName("도서 수정 - 재고가 0에서 양수가 되면 ON_SALE 로 변경")
    void updateBook_StockRestock() {

        Long bookId = 1L;
        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", bookId);
        book.setStockCount(0);
        book.setStatus(BookStatus.SOLD_OUT);
        book.setImages(new HashSet<>());

        BookUpdateRequest request = BookUpdateRequest.builder().stockCount(5).build();

        // [수정] findByIdWithRelations -> findByIdWithRelationsForUpdate 로 변경!
        when(bookRepository.findByIdWithRelationsForUpdate(bookId)).thenReturn(Optional.of(book));

        bookService.updateBook(bookId, request, null);

        verify(stockService).increaseStock(bookId, 5);
        assertThat(book.getStatus()).isEqualTo(BookStatus.ON_SALE);
    }

    @Test
    @DisplayName("썸네일 업데이트 - 존재하지 않는 이미지 ID 예외")
    void updateThumbnail_Fail() {

        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        book.setImages(new HashSet<>());
        when(bookRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> bookService.updateThumbnail(1L, 999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("썸네일 업데이트 - 성공")
    void updateThumbnail_Success() {

        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);

        BookImage img1 = BookImage.builder().isThumbnail(true).imagePath("path1").build();
        ReflectionTestUtils.setField(img1, "id", 1L);

        BookImage img2 = BookImage.builder().isThumbnail(false).imagePath("path2").build();
        ReflectionTestUtils.setField(img2, "id", 2L);

        book.setImages(new HashSet<>(Set.of(img1, img2)));

        when(bookRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(book));

        bookService.updateThumbnail(1L, 2L);

        assertThat(img1.isThumbnail()).isFalse();
        assertThat(img2.isThumbnail()).isTrue();
        assertThat(book.getThumbnail()).isEqualTo("path2");
        verify(bookSearchIndexService).index(book);
    }

    @Test
    @DisplayName("도서 삭제 - DB 삭제 후 ES, MinIO 삭제 호출 (예외 발생 포함)")
    void deleteBook() {

        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);

        BookImage img = BookImage.builder().imagePath("path/to/delete").build();
        book.setImages(Set.of(img));

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        doThrow(new RuntimeException("ES Fail")).when(bookSearchIndexService).deleteIndex(1L);
        doThrow(new RuntimeException("MinIO Fail")).when(imageUploadService).remove("path/to/delete");

        bookService.deleteBook(1L);

        verify(bookRepository).delete(book);
        verify(bookRepository).flush();
        verify(bookSearchIndexService).deleteIndex(1L);
        verify(imageUploadService).remove("path/to/delete");
    }

    @Test
    @DisplayName("신간 도서 조회 - 카테고리 필터링 포함")
    void getNewArrivals_WithCategory() {
        Long categoryId = 1L;
        Category root = Category.builder().id(1L).children(new ArrayList<>()).build();
        Category child = Category.builder().id(2L).children(null).build();
        root.getChildren().add(child);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(root));

        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 10L);
        book.setStatus(BookStatus.ON_SALE);

        ReflectionTestUtils.setField(book, "bookContributors", new HashSet<>());
        ReflectionTestUtils.setField(book, "bookPublishers", new HashSet<>());
        ReflectionTestUtils.setField(book, "bookTags", new HashSet<>());
        ReflectionTestUtils.setField(book, "images", new HashSet<>());

        Page<Book> bookPage = new PageImpl<>(List.of(book));

        when(bookRepository.findBooksByCategoryIdsSorted(anyList(), any(Pageable.class)))
                .thenReturn(bookPage);



        Page<BookListResponse> result = bookService.getNewArrivals(categoryId, PageRequest.of(0, 10));

        verify(categoryRepository).findById(1L);
        verify(bookRepository).findBooksByCategoryIdsSorted(anyList(), any(Pageable.class));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("신간 도서 조회 - 카테고리 없이 전체 조회")
    void getNewArrivals_NoCategory() {
        Page<Book> bookPage = new PageImpl<>(Collections.emptyList());
        when(bookRepository.findAllByOrderByPublishDateDesc(any(Pageable.class))).thenReturn(bookPage);

        bookService.getNewArrivals(null, PageRequest.of(0, 10));

        verify(bookRepository, never()).findBooksByCategoryIdsSorted(anyList(), any());
    }

    @Test
    @DisplayName("베스트셀러 조회 - 주문 서비스 연동")
    void getBestsellers() {

        List<Long> ids = List.of(1L, 2L);
        when(orderServiceClient.getBestSellersBookIds("WEEK")).thenReturn(ids);

        Book b1 = new Book();
        ReflectionTestUtils.setField(b1, "id", 1L);
        b1.setStatus(BookStatus.ON_SALE);

        Book b2 = new Book();
        ReflectionTestUtils.setField(b2, "id", 2L);
        b2.setStatus(BookStatus.BOOK_DELETED);

        when(bookRepository.findAllById(ids)).thenReturn(List.of(b1, b2));

        List<BookListResponse> result = bookService.getBestsellers("WEEK");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("베스트셀러 조회 - 결과 없음")
    void getBestsellers_Empty() {
        when(orderServiceClient.getBestSellersBookIds("WEEK")).thenReturn(Collections.emptyList());
        List<BookListResponse> result = bookService.getBestsellers("WEEK");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("도서 상세 조회 - 로그인 유저")
    void getBookDetail_LoginUser() {

        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L); // ID 주입
        when(bookRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(book));
        when(bookLikeRepository.existsByBookIdAndUserId(1L, 100L)).thenReturn(true);

        BookDetailResponse response = bookService.getBookDetail(1L, 100L, null);

        assertThat(response.getLikedByCurrentUser()).isTrue();
    }

    @Test
    @DisplayName("장바구니용 스냅샷 조회")
    void getBookSnapshots() {

        Book b1 = new Book();
        ReflectionTestUtils.setField(b1, "id", 1L);

        b1.setStatus(BookStatus.ON_SALE);
        b1.setPriceStandard(10000L);
        b1.setPriceSales(9000L);
        b1.setStockCount(50);

        when(bookRepository.findAllById(List.of(1L))).thenReturn(List.of(b1));

        Map<Long, CartResponse> result = bookService.getBookSnapshots(List.of(1L));

        assertThat(result).containsKey(1L);
    }

    @Test
    @DisplayName("내부 주문용 도서 조회 - ID 리스트가 null인 경우")
    void getBooksForOrder_Null() {
        List<BookOrderResponse> result = bookService.getBooksForOrder(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("내부 주문용 도서 조회 - 정상")
    void getBooksForOrder_Success() {

        Book b = new Book();
        ReflectionTestUtils.setField(b, "id", 1L);
        b.setIsWrapped(false);
        b.setStatus(BookStatus.ON_SALE);
        b.setTitle("Test Title");
        b.setPriceStandard(10000L);
        b.setPriceSales(9000L);

        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", 100L); // Category ID 설정
        b.setCategory(category);

        when(bookRepository.findAllById(List.of(1L))).thenReturn(List.of(b));

        List<BookOrderResponse> result = bookService.getBooksForOrder(List.of(1L));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("최근 본 상품 조회 및 병합")
    void recentViews() {

        when(bookHistoryService.getRecentViews(1L, null)).thenReturn(List.of(10L));
        Book b = new Book();
        ReflectionTestUtils.setField(b, "id", 10L);
        when(bookRepository.findAllById(List.of(10L))).thenReturn(List.of(b));

        List<BookListResponse> views = bookService.getRecentViews(1L, null);
        assertThat(views).hasSize(1);

        bookService.mergeRecentViews("guest", 1L);
        verify(bookHistoryService).mergeHistory("guest", 1L);

        bookService.mergeRecentViews(null, 1L);
        verify(bookHistoryService, times(1)).mergeHistory(any(), any());
    }

    @Test
    @DisplayName("도서 상태 변경 - 예외 발생 시 로그 출력")
    void updateBookStatus() {
        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        doThrow(new RuntimeException("Index Fail")).when(bookSearchIndexService).index(book);

        bookService.updateBookStatus(1L, BookStatus.SOLD_OUT);

        assertThat(book.getStatus()).isEqualTo(BookStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("getPopularBooks 테스트")
    void getPopularBooks() {
        Page<Book> page = new PageImpl<>(List.of(new Book()));
        when(bookRepository.findByStatusOrderByLikeCountDesc(eq(BookStatus.ON_SALE), any())).thenReturn(page);

        Page<BookListResponse> result = bookService.getPopularBooks(PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getBooks - 공통 검색")
    void getBooks() {
        Page<Book> page = new PageImpl<>(List.of(new Book()));
        when(bookRepository.findByStatusNot(eq(BookStatus.BOOK_DELETED), any())).thenReturn(page);
        when(bookListResponseMapper.fromEntity(any())).thenReturn(BookListResponse.builder().build());

        bookService.getBooks(new BookSearchCondition(), PageRequest.of(0, 10));

        verify(bookListResponseMapper).fromEntity(any());
    }

    @Test
    @DisplayName("getBookCount")
    void getBookCount() {
        when(bookRepository.count()).thenReturn(100L);
        assertThat(bookService.getBookCount()).isEqualTo(100L);
    }
}