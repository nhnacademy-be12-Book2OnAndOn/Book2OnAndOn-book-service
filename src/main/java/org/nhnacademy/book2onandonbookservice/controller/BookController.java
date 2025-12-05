package org.nhnacademy.book2onandonbookservice.controller;


import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.annotation.AuthCheck;
import org.nhnacademy.book2onandonbookservice.domain.Role;
import org.nhnacademy.book2onandonbookservice.dto.api.RestPage;
import org.nhnacademy.book2onandonbookservice.dto.book.BookDetailResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.service.book.BookLikeService;
import org.nhnacademy.book2onandonbookservice.service.book.BookLikeService.BookLikeToggleResult;
import org.nhnacademy.book2onandonbookservice.service.book.BookService;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookController {
    private final BookService bookService;
    private final ImageUploadService imageUploadService;
    private final BookLikeService bookLikeService;
    private final UserHeaderUtil util;


    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getCategory() {
        log.info("전체 카테고리 목록 조회 요청");
        List<CategoryDto> categories = bookService.getCategories();
        log.info("조회된 카테고리 개수: {}", categories.size());
        return ResponseEntity.ok(categories);
    }

    /// 도서 목록 조회
    @GetMapping
    public ResponseEntity<Page<BookListResponse>> getBooks(
            @ModelAttribute BookSearchCondition condition,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<BookListResponse> page = bookService.getBooks(condition, pageable);
        return ResponseEntity.ok(page);
    }

    /// 도서 상세 조회
    @GetMapping("/{bookId}")
    public ResponseEntity<BookDetailResponse> getBookDetail(@PathVariable Long bookId) {
        Long userId = util.getUserId();
        String guestId = util.getGuestId();
        BookDetailResponse response = bookService.getBookDetail(bookId, userId, guestId);
        return ResponseEntity.ok(response);
    }

    /// 베스트셀러 조회 API GET /books/bestsellers?period=DAILY
    @GetMapping("/bestsellers")
    public ResponseEntity<List<BookListResponse>> getBestsellers(@RequestParam("period") String period) {
        List<BookListResponse> bestsellers = bookService.getBestsellers(period.toUpperCase());
        return ResponseEntity.ok(bestsellers);
    }

    /// 신간 도서 조회 GET /books/new-arrivals?categoryId=10&size=20
    @GetMapping("/new-arrivals")
    public ResponseEntity<Page<BookListResponse>> getNewArrivals(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @PageableDefault(sort = "publishDate", direction = Direction.DESC) Pageable pageable) {
        log.info("신간도서 요청 들어옴");
        Page<BookListResponse> newArrivals = bookService.getNewArrivals(categoryId, pageable);
        return ResponseEntity.ok(newArrivals);
    }

    /// 인기 도서 조회 API
    @GetMapping("/popular")
    public ResponseEntity<Page<BookListResponse>> getPopularBooks(Pageable pageable) {
        Page<BookListResponse> result = bookService.getPopularBooks(pageable);
        return ResponseEntity.ok(result);
    }

    /// 최근 본 상품 조회 (최신순 50개)
    @GetMapping("/recent-views")
    public ResponseEntity<List<BookListResponse>> getRecentViews() {
        Long userId = util.getUserId();
        String guestId = util.getGuestId();

        if (userId == null && (guestId == null || guestId.isBlank())) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<BookListResponse> responses = bookService.getRecentViews(userId, guestId);
        return ResponseEntity.ok(responses);
    }

    /**
     * 로그인 직후 비회원 기록 병합 API POST /books/recent-views/merge Header: X-User-Id, X-Guest-Id
     */
    @PostMapping("/recent-views/merge")
    public ResponseEntity<Void> mergeRecentViews(@RequestHeader("X-User-Id") Long userId,
                                                 @RequestHeader("X-Guest-Id") String guestId) {

        bookService.mergeRecentViews(guestId, userId);
        return ResponseEntity.ok().build();
    }

    /// 좋아요 토글 POST /books/{bookId}/likes
    @PostMapping("/{bookId}/likes")
    @AuthCheck(Role.USER)
    public ResponseEntity<BookLikeToggleResponse> toggleLike(@PathVariable Long bookId) {
        Long userId = util.getUserId();
        BookLikeToggleResult result = bookLikeService.toggleLike(bookId, userId);
        BookLikeToggleResponse body = new BookLikeToggleResponse(
                result.liked(),
                result.likeCount()
        );

        return ResponseEntity.ok(body);
    }

    @GetMapping("/my-likes")
    @AuthCheck(Role.USER)
    public ResponseEntity<RestPage<BookListResponse>> getMyLikedBooks(@PageableDefault(size = 12) Pageable pageable) {
        Long userId = util.getUserId();

        RestPage<BookListResponse> responses = bookLikeService.getMyLikedBookIds(userId, pageable);
        return ResponseEntity.ok(responses);
    }

    public record BookLikeToggleResponse(boolean liked, Long likeCount) {
    }
}
