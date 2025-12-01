package org.nhnacademy.book2onandonbookservice.service.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookCategory;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.exception.OutOfStockException;
import org.nhnacademy.book2onandonbookservice.repository.BookLikeRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.nhnacademy.book2onandonbookservice.service.mapper.BookListResponseMapper;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    @InjectMocks
    private BookServiceImpl bookService;

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookSearchIndexService bookSearchIndexService;
    @Mock
    private BookValidator bookValidator;
    @Mock
    private BookFactory bookFactory;
    @Mock
    private BookRelationService bookRelationService;
    @Mock
    private BookLikeRepository bookLikeRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BookImage mockBookImage;
    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private BookListResponseMapper bookListResponseMapper;

    private Book bookA;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);

        Contributor mockContributorEntity = mock(Contributor.class);
        lenient().when(mockContributorEntity.getId()).thenReturn(50L);
        lenient().when(mockContributorEntity.getContributorName()).thenReturn("Test Author");

        BookContributor mockBookContributor = mock(BookContributor.class);
        lenient().when(mockBookContributor.getContributor()).thenReturn(mockContributorEntity);

        Publisher mockPublisherEntity = mock(Publisher.class);
        lenient().when(mockPublisherEntity.getId()).thenReturn(60L);
        lenient().when(mockPublisherEntity.getPublisherName()).thenReturn("Test Publisher");

        BookPublisher mockBookPublisher = mock(BookPublisher.class);
        lenient().when(mockBookPublisher.getPublisher()).thenReturn(mockPublisherEntity);

        Category mockCategoryEntity = mock(Category.class);
        lenient().when(mockCategoryEntity.getId()).thenReturn(70L);
        lenient().when(mockCategoryEntity.getCategoryName()).thenReturn("Test Category");

        BookCategory mockBookCategory = mock(BookCategory.class);
        lenient().when(mockBookCategory.getCategory()).thenReturn(mockCategoryEntity);

        BookImage mockBookImage = mock(BookImage.class);
        lenient().when(mockBookImage.getImagePath()).thenReturn("/path/to/image.jpg");

        bookA = Book.builder()
                .id(1L)
                .title("Book A")
                .publishDate(LocalDate.of(2023, 1, 1))
                .priceStandard(10000L)
                .rating(5.0)
                .isWrapped(false)
                .isbn("9791191370215")
                .status(BookStatus.ON_SALE)
                .stockCount(100)
                .priceSales(9000L)
                .images(new HashSet<>(List.of(mockBookImage)))
                .bookCategories(new HashSet<>(List.of(mockBookCategory)))
                .bookContributors(new HashSet<>(List.of(mockBookContributor)))
                .bookPublishers(new HashSet<>(List.of(mockBookPublisher)))
                .bookTags(new HashSet<>())
                .reviews(new HashSet<>())
                .likes(new HashSet<>())
                .build();
    }

    @Test
    @DisplayName("도서 등록 성공 - DB 저장 및 ES 인덱싱 호출 검증")
    void createBook() {
        BookSaveRequest request = new BookSaveRequest();
        given(bookFactory.createFrom(any(BookSaveRequest.class))).willReturn(bookA);
        given(bookRepository.save(any(Book.class))).willReturn(bookA);

        Long saveId = bookService.createBook(request);

        assertThat(saveId).isEqualTo(bookA.getId());
        verify(bookValidator, times(1)).validateForCreate(request);
        verify(bookRepository, times(1)).save(bookA);
        verify(bookSearchIndexService, times(1)).index(bookA);
    }

    @Test
    @DisplayName("도서 등록 실패 - 유효성 검증 실패 (Validator 에외 전파)")
    void createBook_Fail_Validation() {
        BookSaveRequest request = new BookSaveRequest(); //잘못된 데이터라고 생각하기
        willThrow(new IllegalArgumentException("도서 제목은 필수 작성 항목입니다.")).given(bookValidator).validateForCreate(request);
        assertThatThrownBy(() -> bookService.createBook(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("도서 제목은 필수 작성 항목입니다.");

        willThrow(new IllegalArgumentException("ISBN은 필수 작성 항목입니다.")).given(bookValidator).validateForCreate(request);
        assertThatThrownBy(() -> bookService.createBook(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISBN은 필수 작성 항목입니다.");

        willThrow(new IllegalArgumentException("출판일은 필수 작성 항목입니다.")).given(bookValidator).validateForCreate(request);
        assertThatThrownBy(() -> bookService.createBook(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("출판일은 필수 작성 항목입니다.");

        willThrow(new IllegalArgumentException("정가는 필수입니다..")).given(bookValidator).validateForCreate(request);
        assertThatThrownBy(() -> bookService.createBook(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정가는 필수입니다.");

        willThrow(new IllegalArgumentException("정가는 0원 이상이어야 합니다.")).given(bookValidator).validateForCreate(request);
        assertThatThrownBy(() -> bookService.createBook(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정가는 0원 이상이어야 합니다.");

        willThrow(new IllegalArgumentException("판매가는 0원 이상이어야 합니다.")).given(bookValidator).validateForCreate(request);
        assertThatThrownBy(() -> bookService.createBook(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("판매가는 0원 이상이어야 합니다.");

        verify(bookRepository, never()).save(any());
    }


    @Test
    @DisplayName("도서 수정 성공")
    void updateBook() {
        Long bookId = 1L;
        BookSaveRequest request = new BookSaveRequest();
        given(bookRepository.findByIdWithRelations(bookId)).willReturn(Optional.of(bookA));

        bookService.updateBook(bookId, request);

        verify(bookFactory, times(1)).updateFields(bookA, request);
        verify(bookRelationService, times(1)).applyRelationsForUpdate(bookA, request);
        verify(bookSearchIndexService, times(1)).index(bookA);
    }

    @Test
    @DisplayName("도서 수정 실패")
    void updateBook_Fail() {
        Long bookId = 9999L;
        BookSaveRequest request = new BookSaveRequest();
        given(bookRepository.findByIdWithRelations(bookId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.updateBook(bookId, request))
                .isInstanceOf(NotFoundBookException.class)
                .hasMessageContaining("해당 도서를 찾을 수 없습니다 ID: " + bookId);

        verify(bookFactory, never()).updateFields(any(), any());
        verify(bookRelationService, never()).applyRelationsForUpdate(any(), any());
        verify(bookSearchIndexService, never()).index(any());
    }

    @Test
    @DisplayName("도서 삭제 성공 - 도서 미발견")
    void deleteBook() {
        Long bookId = 1L;
        given(bookRepository.findById(bookId)).willReturn(Optional.of(bookA));

        bookService.deleteBook(bookId);

        verify(bookRepository, times(1)).delete(bookA);
        verify(bookSearchIndexService, times(1)).deleteIndex(bookId);
    }

    @Test
    @DisplayName("도서 삭제 실패 - 도서 미발견")
    void deleteBook_Fail_NotFound() {
        Long bookId = 999L;
        given(bookRepository.findById(bookId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.deleteBook(bookId))
                .isInstanceOf(NotFoundBookException.class)
                .hasMessageContaining("해당 도서를 찾을 수 없습니다 ID: " + bookId);

        verify(bookRepository, never()).delete(any());
        verify(bookSearchIndexService, never()).deleteIndex(anyLong());
    }

    @Test
    void getBooks() {
        BookSearchCondition condition = new BookSearchCondition();
        Page<Book> bookPage = new PageImpl<>(List.of(bookA), pageable, 1);

        BookListResponse mockResponse = BookListResponse.builder().id(bookA.getId()).title("Book A").build();
        given(bookListResponseMapper.fromEntity(bookA)).willReturn(mockResponse);

        given(bookRepository.findAll(pageable)).willReturn(bookPage);

        Page<BookListResponse> responses = bookService.getBooks(condition, pageable);

        assertThat(responses).hasSize(1);
        verify(bookRepository, times(1)).findAll(pageable);
    }

    @Test
    @DisplayName("도서 상세 조회 성공 - 로그인 유저, 좋아요 상태 포함")
    void getBookDetail_Success() {
        Long bookId = 1L;
        Long userId = 100L;
        long mockLikeCount = 5L;

        given(bookRepository.findByIdWithRelations(bookId)).willReturn(Optional.of(bookA));
        given(bookLikeRepository.countByBookId(bookId)).willReturn(mockLikeCount);
        given(bookLikeRepository.existsByBookIdAndUserId(bookId, userId)).willReturn(true);

        BookDetailResponse result = bookService.getBookDetail(bookId, userId);

        assertThat(result).isNotNull();

        assertThat(result.getLikedByCurrentUser()).isTrue();
        assertThat(result.getLikeCount()).isEqualTo(mockLikeCount);
        assertThat(result.getRating()).isEqualTo(5.0);
        assertThat(result.getTitle()).isEqualTo("Book A");

        assertThat(result.getCategories().get(0).getName()).isEqualTo("Test Category");
        assertThat(result.getPriceStandard()).isEqualTo(10000L);

        verify(bookRepository, times(1)).findByIdWithRelations(bookId);
        verify(bookLikeRepository, times(1)).existsByBookIdAndUserId(bookId, userId);
    }


    @Test
    @DisplayName("도서 상세 조회 성공 - 비로그인 유저")
    void getBookDetail_Success_NotLoggedIn() {
        Long bookId = 1L;

        given(bookRepository.findByIdWithRelations(bookId)).willReturn(Optional.of(bookA));
        given(bookLikeRepository.countByBookId(bookId)).willReturn(10L);

        BookDetailResponse result = bookService.getBookDetail(bookId, null);

        assertThat(result.getLikedByCurrentUser()).isNull();

        verify(bookLikeRepository, times(0)).existsByBookIdAndUserId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("도서 상세 조회 실패 - 존재하지않은 도서 ID")
    void getBook_Fail_Validation() {
        Long bookId = 9999L;
        Long userId = 1L;
        given(bookRepository.findByIdWithRelations(bookId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getBookDetail(bookId, userId))
                .isInstanceOf(NotFoundBookException.class)
                .hasMessageContaining("해당 도서를 찾을 수 없습니다 ID: " + bookId);
        verify(bookLikeRepository, never()).existsByBookIdAndUserId(anyLong(), anyLong());

    }

    @Test
    @DisplayName("카테고리 조회 성공 - 트리 구조 변환 검증")
    void getCategories_Success_TreeStructure() {
        Category root = Category.builder().id(1L).categoryName("Root").build();
        Category child1 = Category.builder().id(2L).categoryName("Child 1").parent(root).build();
        Category child2 = Category.builder().id(3L).categoryName("Child 2").parent(root).build();

        given(categoryRepository.findAll()).willReturn(List.of(root, child1, child2));

        List<CategoryDto> result = bookService.getCategories();

        assertThat(result).hasSize(1);
        CategoryDto categoryDto = result.get(0);
        assertThat(categoryDto.getName()).isEqualTo("Root");

        assertThat(categoryDto.getParentId()).isNull();
        assertThat(categoryDto.getChildren()).hasSize(2);
        assertThat(categoryDto.getChildren()).extracting("name").containsExactlyInAnyOrder("Child 1", "Child 2");
        verify(categoryRepository, times(1)).findAll();
    }


    @Test
    @DisplayName("베스트 셀러 조회 성공")
    void getBestsellers() {
        String period = "DAILY";
        Book bookB = Book.builder().id(2L).title("Book B").build();

        given(orderServiceClient.getBestSellersBookIds(period)).willReturn(List.of(2L, 1L));
        given(bookRepository.findAllById(anyList())).willReturn(List.of(bookA, bookB));

        List<BookListResponse> responses = bookService.getBestsellers(period);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getId()).isEqualTo(2);
        verify(orderServiceClient, times(1)).getBestSellersBookIds(period);
    }

    @Test
    @DisplayName("베스트셀러 조회 - Order Service 장애 발생시 (예외전파 확인)")
    void getBestsellers_Fail_OrderServiceError() {
        String period = "DAILY";
        given(orderServiceClient.getBestSellersBookIds(period))
                .willThrow(new RuntimeException("Order Service Unavailable"));

        assertThatThrownBy(() -> bookService.getBestsellers(period))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Order Service Unavailable");

        verify(bookRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("신간 도서 조회 성공")
    void getNewArrivals() {
        Long categoryID = 5L;
        Page<Book> page = new PageImpl<>(List.of(bookA), pageable, 1);

        given(bookRepository.findNewArrivalsByCategoryId(eq(categoryID), any(Pageable.class))).willReturn(page);

        Page<BookListResponse> responses = bookService.getNewArrivals(categoryID, pageable);

        assertThat(responses).hasSize(1);
        verify(bookRepository, times(1)).findNewArrivalsByCategoryId(categoryID, pageable);
    }

    @Test
    @DisplayName("신간 도서 조회 실패 - DB 연결 오류 발생")
    void getNewArrivals_Fail() {
        Long categoryID = 5L;

        given(bookRepository.findNewArrivalsByCategoryId(eq(categoryID), any(Pageable.class)))
                .willThrow(new RuntimeException("DB 연결 불안정"));

        assertThatThrownBy(() -> bookService.getNewArrivals(categoryID, pageable))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 연결 불안정");
        verify(bookRepository, times(1)).findNewArrivalsByCategoryId(categoryID, pageable);
    }

    @Test
    @DisplayName("주문용 도서 정보 다건 조회 성공")
    void getBooksForOrder_Success() {
        List<Long> bookIds = List.of(1L);

        given(bookRepository.findAllById(bookIds)).willReturn(List.of(bookA));

        List<BookOrderResponse> responses = bookService.getBooksForOrder(bookIds);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getBookId()).isEqualTo(bookA.getId());
        assertThat(responses.get(0).getTitle()).isEqualTo(bookA.getTitle());
        assertThat(responses.get(0).getPriceSales()).isEqualTo(bookA.getPriceSales());

        verify(bookRepository, times(1)).findAllById(bookIds);
    }

    @Test
    @DisplayName("주문용 도서 정보 조회 - 빈 리스트 입력 시 빈 결과 반환")
    void getBooksForOrder_EmptyInput() {
        List<BookOrderResponse> responsesNull = bookService.getBooksForOrder(null);
        List<BookOrderResponse> responsesEmpty = bookService.getBooksForOrder(List.of());

        assertThat(responsesNull).isEmpty();
        assertThat(responsesEmpty).isEmpty();

        verify(bookRepository, never()).findAllById(anyList());
    }

    @Test
    @DisplayName("재고 차감 성공 - 재고가 남아있을 때 상태유지")
    void decreaseStock_Success_StatusRemains() {
        int quantity = 1;
        bookA.setStockCount(10);

        StockRequest request = mock(StockRequest.class);
        given(request.getBookId()).willReturn(bookA.getId());
        given(request.getQuantity()).willReturn(quantity);

        given(bookRepository.decreaseStock(bookA.getId(), quantity)).willReturn(1);
        given(bookRepository.findById(bookA.getId())).willReturn(Optional.of(bookA));

        List<StockRequest> requests = new ArrayList<>(List.of(request));
        bookService.decreaseStock(requests);
        assertThat(bookA.getStatus()).isEqualTo(BookStatus.ON_SALE);

        verify(bookRepository, times(1)).decreaseStock(bookA.getId(), quantity);
    }

    @Test
    @DisplayName("재고 차감 성공 - 재고가 0이 되어 품절(SOLD_OUT)로 상태변경")
    void decreaseStock_Success_ChangeToSoldOut() {
        int decreaseQuantity = 1;

        bookA.setStockCount(0);
        StockRequest request = mock(StockRequest.class);
        given(request.getBookId()).willReturn(bookA.getId());
        given(request.getQuantity()).willReturn(decreaseQuantity);

        given(bookRepository.decreaseStock(bookA.getId(), decreaseQuantity)).willReturn(1);
        given(bookRepository.findById(bookA.getId())).willReturn(Optional.of(bookA));

        List<StockRequest> requests = new ArrayList<>(List.of(request));
        bookService.decreaseStock(requests);

        assertThat(bookA.getStatus()).isEqualTo(BookStatus.SOLD_OUT);

        verify(bookRepository, times(1)).decreaseStock(bookA.getId(), decreaseQuantity);
    }

    @Test
    @DisplayName("재고 차감 실패 - DB 업데이트 실패(재고부족) -> 예외 발생")
    void decreaseStock_Fail_OutofStock() {
        int decreaseQuantity = 999;

        StockRequest request = mock(StockRequest.class);
        given(request.getBookId()).willReturn(bookA.getId());
        given(request.getQuantity()).willReturn(decreaseQuantity);

        given(bookRepository.decreaseStock(bookA.getId(), decreaseQuantity)).willReturn(0);
        List<StockRequest> requests = new ArrayList<>(List.of(request));
        assertThatThrownBy(() -> bookService.decreaseStock(requests)).isInstanceOf(OutOfStockException.class)
                .hasMessageContaining("재고가 부족합니다.");
        verify(bookRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("재고 차감 실패 - 차감은 성공했으나 도서 조회 실패 (데이터 불일치)")
    void decreaseStock_Fail_BookNotFound() {
        StockRequest request = mock(StockRequest.class);
        given(request.getBookId()).willReturn(bookA.getId());
        given(request.getQuantity()).willReturn(1);

        given(bookRepository.decreaseStock(bookA.getId(), 1)).willReturn(1);
        given(bookRepository.findById(bookA.getId())).willReturn(Optional.empty());
        List<StockRequest> requests = new ArrayList<>(List.of(request));

        assertThatThrownBy(() -> bookService.decreaseStock(requests)).isInstanceOf(NotFoundBookException.class);
    }

    @Test
    @DisplayName("재고 증가(복구) 성공 - 품절 상태였다가 재고 확보 후 판매중으로 변경")
    void increaseStock_Success_ChangeToOnSale() {
        int increaseQuantity = 5;

        bookA.setStatus(BookStatus.SOLD_OUT);
        bookA.setStockCount(10);

        StockRequest request = mock(StockRequest.class);
        given(request.getBookId()).willReturn(bookA.getId());
        given(request.getQuantity()).willReturn(increaseQuantity);

        given(bookRepository.findById(bookA.getId())).willReturn(Optional.of(bookA));
        List<StockRequest> requests = new ArrayList<>(List.of(request));

        bookService.increaseStock(requests);

        verify(bookRepository, times(1)).increaseStock(bookA.getId(), increaseQuantity);

        assertThat(bookA.getStatus()).isEqualTo(BookStatus.ON_SALE);
    }

    @Test
    @DisplayName("재고 증가 실패 - 도서 미발견")
    void increaseStock_Fail_NotFound() {
        StockRequest request = mock(StockRequest.class);
        given(request.getBookId()).willReturn(999L);
        given(request.getQuantity()).willReturn(1);

        given(bookRepository.findById(999L)).willReturn(Optional.empty());
        List<StockRequest> requests = new ArrayList<>(List.of(request));

        assertThatThrownBy(() -> bookService.increaseStock(requests)).isInstanceOf(NotFoundBookException.class);

        verify(bookRepository, times(1)).increaseStock(999L, 1);
    }

    @Test
    @DisplayName("도서 상태만 변경 - 성공")
    void updateBookStatus_Success() {
        Long bookId = 1L;
        BookStatus bookStatus = BookStatus.SOLD_OUT;

        bookA.setStatus(BookStatus.ON_SALE);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(bookA));

        bookService.updateBookStatus(bookId, bookStatus);

        assertThat(bookA.getStatus()).isEqualTo(bookStatus);

        verify(bookSearchIndexService, times(1)).index(bookA);
    }

    @Test
    @DisplayName("도서 상태 변경 성공 - ES 인덱싱 실패해도 트랜잭션은 커밋")
    void updateBookStatus_Success_EvenIfEsFails() {
        Long bookId = 1L;
        BookStatus newStatus = BookStatus.BOOK_DELETED;

        given(bookRepository.findById(bookId)).willReturn(Optional.of(bookA));

        willThrow(new RuntimeException("ES Server Error")).given(bookSearchIndexService).index(bookA);
        bookService.updateBookStatus(bookId, newStatus);

        assertThat(bookA.getStatus()).isEqualTo(newStatus);

        verify(bookSearchIndexService, times(1)).index(bookA);
    }
}