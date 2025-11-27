package org.nhnacademy.book2onandonbookservice.service.book;

import java.util.List;
import org.nhnacademy.book2onandonbookservice.dto.book.BookDetailResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
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
    BookDetailResponse getBookDetail(Long bookId, Long currentUserId);

    List<CategoryDto> getCategories();
}
