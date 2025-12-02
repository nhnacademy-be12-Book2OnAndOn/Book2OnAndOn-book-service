package org.nhnacademy.book2onandonbookservice.service.book;

import java.util.List;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.book.BookDetailResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookOrderResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookService {
    // 도서 등록
    Long createBook(BookSaveRequest request);

    // 도서 수정
    void updateBook(Long bookId, BookSaveRequest request);

    // 도서 삭제
    void deleteBook(Long bookId);

    // 도서 목록 조회(검색 시)
    Page<BookListResponse> getBooks(BookSearchCondition condition, Pageable pageable);

    // 도서 상세 조회
    BookDetailResponse getBookDetail(Long bookId, Long currentUserId, String guestId);

    List<CategoryDto> getCategories();

    //베스트셀러 조회 및 캐싱
    List<BookListResponse> getBestsellers(String period);

    //신간 도서를 출간일 최신순으로 조회하고 캐싱
    Page<BookListResponse> getNewArrivals(Long categoryId, Pageable pageable);

    //[내부 통신용] 주문서 생성 및 결제 검증을 위한 도서 정보 다건 조회
    List<BookOrderResponse> getBooksForOrder(List<Long> bookIds);

    // 인기 도서 조회(좋아요 순)
    Page<BookListResponse> getPopularBooks(Pageable pageable);

    //재고 감소
    void decreaseStock(List<StockRequest> requests);

    //재고 증가
    void increaseStock(List<StockRequest> requests);

    //도서 상태 변경
    void updateBookStatus(Long bookId, BookStatus status);

    // 최근 본 상품 조회
    List<BookListResponse> getRecentViews(Long userId, String guestId);

    //최근 본 상품 guest -> 로그인 했을 때 merge
    void mergeRecentViews(String guestId, Long userId);
}
