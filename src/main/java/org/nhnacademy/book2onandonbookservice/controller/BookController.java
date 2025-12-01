package org.nhnacademy.book2onandonbookservice.controller;


import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.annotation.AuthCheck;
import org.nhnacademy.book2onandonbookservice.domain.Role;
import org.nhnacademy.book2onandonbookservice.dto.book.BookDetailResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.book.BookStatusUpdateRequest;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.service.book.BookService;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookController {
    private final BookService bookService;
    private final ImageUploadService imageUploadService;

    /**
     * 도서 등록 (관리자용) - 방식: multipart/form-data -"book": Json 데이터 (BookSaveRequest) - "image" : 파일 데이터
     */
    @AuthCheck(Role.BOOK_ADMIN)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> createBook(@RequestPart("book") BookSaveRequest request,
                                           @RequestPart(value = "image", required = false) MultipartFile image) {
        log.info("도서 등록 요청: {}", request.getTitle());

        // 이미지 처리 로직 참고 (minio url 을 만들어서 db에 저장해야됨)
        if (image != null && !image.isEmpty()) {
            String minioUrl = imageUploadService.uploadBookImage(image);
            request.setImagePath(minioUrl);
        }

        Long bookId = bookService.createBook(request);
        return ResponseEntity.created(URI.create("/books/" + bookId)).build();
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getCategory() {
        log.info("전체 카테고리 목록 조회 요청");
        List<CategoryDto> categories = bookService.getCategories();
        log.info("조회된 카테고리 개수: {}", categories.size());
        return ResponseEntity.ok(categories);
    }

    /// 도서 목록 조회
    @GetMapping
    public Page<BookListResponse> getBooks(
            @ModelAttribute BookSearchCondition condition,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return bookService.getBooks(condition, pageable);
    }

    /// 도서 상세 조회
    @GetMapping("/{bookId}")
    public BookDetailResponse getBookDetail(
            @PathVariable Long bookId,
            @RequestParam(required = false) Long currentUserId
    ) {
        return bookService.getBookDetail(bookId, currentUserId);
    }

    /// 도서 수정
    @PutMapping(value = "/{bookId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuthCheck(Role.BOOK_ADMIN)
    public ResponseEntity<Void> updateBook(
            @PathVariable Long bookId,
            @RequestPart("book") BookSaveRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        log.info("도서 수정 요청: {}", request.getTitle());

        // 새 이미지 업로드가 있는 경우
        if (image != null && !image.isEmpty()) {
            String minioUrl = imageUploadService.uploadBookImage(image);
            request.setImagePath(minioUrl);
        }

        bookService.updateBook(bookId, request);

        return ResponseEntity.noContent().build();  // 204
    }

    ///  도서 삭제
    @DeleteMapping("/{bookId}")
    @AuthCheck(Role.BOOK_ADMIN)
    public ResponseEntity<Void> deleteBook(@PathVariable Long bookId) {
        log.info("도서 삭제 요청: {}", bookId);

        bookService.deleteBook(bookId); // DB 삭제 + ES 인덱스 삭제 포함

        return ResponseEntity.noContent().build(); // 204
    }

    /// 도서 상태변경
    @AuthCheck(Role.BOOK_ADMIN)
    @PatchMapping("/{bookId}/status")
    public ResponseEntity<Void> updateBookStatus(@PathVariable Long bookId,
                                                 @RequestBody @Valid BookStatusUpdateRequest request) {
        bookService.updateBookStatus(bookId, request.getStatus());
        return ResponseEntity.ok().build();
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
        Page<BookListResponse> newArrivals = bookService.getNewArrivals(categoryId, pageable);
        return ResponseEntity.ok(newArrivals);
    }

    /// 인기 도서 조회 API
    @GetMapping("/popular")
    public ResponseEntity<Page<BookListResponse>> getPopularBooks(Pageable pageable) {
        Page<BookListResponse> result = bookService.getPopularBooks(pageable);
        return ResponseEntity.ok(result);
    }
}
