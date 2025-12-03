package org.nhnacademy.book2onandonbookservice.controller;

import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.annotation.AuthCheck;
import org.nhnacademy.book2onandonbookservice.domain.Role;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookStatusUpdateRequest;
import org.nhnacademy.book2onandonbookservice.service.book.BookService;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/admin/books")
@RequiredArgsConstructor
public class BookAdminController {
    private final BookService bookService;
    private final ImageUploadService imageUploadService;
    private final UserHeaderUtil util;

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
}
