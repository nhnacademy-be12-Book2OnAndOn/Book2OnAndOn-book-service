package org.nhnacademy.book2onandonbookservice.service.search;

import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/// ES 기반 도서 검색
/// - 풀텍스트: 제목, 권, 기여자, 출판사, 카테고리명, 태그명
/// - 필터: 가격, 출판일 등 (BookSearchCondition 기준)
public interface BookSearchService {
    Page<BookListResponse> search(BookSearchCondition condition, Pageable pageable);
}
