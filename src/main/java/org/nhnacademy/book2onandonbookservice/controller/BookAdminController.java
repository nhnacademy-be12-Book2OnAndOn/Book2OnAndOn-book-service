package org.nhnacademy.book2onandonbookservice.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.annotation.AuthCheck;
import org.nhnacademy.book2onandonbookservice.domain.Role;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookStatusUpdateRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookUpdateRequest;
import org.nhnacademy.book2onandonbookservice.service.book.AladinService;
import org.nhnacademy.book2onandonbookservice.service.book.BookService;
import org.nhnacademy.book2onandonbookservice.service.book.StockService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/admin/books")
@RequiredArgsConstructor
public class BookAdminController {
    private final BookService bookService;
    private final AladinService aladinService;
    private final StockService stockService;

    /**
     * 도서 등록(관리자용) : GoogleBooksApi 사용
     */
    @AuthCheck(Role.BOOK_ADMIN)
    @GetMapping("/lookup")
    public ResponseEntity<BookSaveRequest> lookupBook(@RequestParam String isbn){
        BookSaveRequest response = aladinService.searchBookInfo(isbn);
        return ResponseEntity.ok(response);
    }
    /**
     * 도서 등록 (관리자용) - 방식: multipart/form-data -"book": Json 데이터 (BookSaveRequest) - "image" : 파일 데이터
     */
    @AuthCheck(Role.BOOK_ADMIN)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> createBook(@RequestPart("book") @Valid BookSaveRequest request,
                                           @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        log.info("도서 등록 요청: {}", request.getTitle());

        Long bookId = bookService.createBook(request, images);
        return ResponseEntity.created(URI.create("/books/" + bookId)).build();
    }

    /// 도서 수정
    @PutMapping(value = "/{bookId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuthCheck(Role.BOOK_ADMIN)
    public ResponseEntity<Void> updateBook(
            @PathVariable Long bookId,
            @RequestPart("book") BookUpdateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        log.info("도서 수정 요청: {}", request.getTitle());

        bookService.updateBook(bookId, request, images);

        return ResponseEntity.noContent().build();  // 204
    }

    /// 도서 이미지 썸네일 설정
    @PutMapping("/{bookId}/images/{imageId}/thumbnail")
    public ResponseEntity<Void> updateThumbnail(@PathVariable Long bookId, @PathVariable Long imageId){
        bookService.updateThumbnail(bookId,imageId);
        return ResponseEntity.ok().build();
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

    /// 도서 개수 반환
    @AuthCheck({Role.BOOK_ADMIN, Role.COUPON_ADMIN, Role.MEMBER_ADMIN, Role.ORDER_ADMIN})
    @GetMapping("/total-count")
    public ResponseEntity<Long> getBookCount(){
        long count = bookService.getBookCount();
        return ResponseEntity.ok(count);
    }

    /// 도서 재고 DB - REDIS 동기화 (재고가 진짜진짜 꼬였을때 써야함)
    /// DB가 재고 10개이고 Redis가 9개인 상태에서 쓰면 재고가 1개 뻥튀기 될 수 있음
    @AuthCheck(Role.BOOK_ADMIN)
    @PostMapping("/sync/{bookId}")
    public ResponseEntity<String> syncStock(@PathVariable Long bookId){
        stockService.synchronizeStock(bookId);
        return ResponseEntity.ok("BookId "+ bookId + " 재고 동기화 완료");
    }

}
