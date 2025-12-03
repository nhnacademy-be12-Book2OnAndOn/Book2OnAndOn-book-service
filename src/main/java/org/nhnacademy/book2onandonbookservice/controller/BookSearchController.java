package org.nhnacademy.book2onandonbookservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class BookSearchController {

    private final BookSearchService bookSearchService;

    // ES 기반 도서 검색
    @GetMapping("/books")
    public ResponseEntity<Page<BookListResponse>> searchBooks(
            @ModelAttribute BookSearchCondition condition,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<BookListResponse> result = bookSearchService.search(condition, pageable);
        return ResponseEntity.ok(result);
    }
}