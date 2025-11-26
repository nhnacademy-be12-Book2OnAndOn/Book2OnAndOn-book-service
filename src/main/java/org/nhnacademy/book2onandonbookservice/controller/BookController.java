package org.nhnacademy.book2onandonbookservice.controller;


import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.service.book.BookService;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> createBook(@RequestPart("book") BookSaveRequest request,
                                           @RequestPart(value = "image", required = false) MultipartFile image) {
        log.info("도서 등록 요청: {}", request.getTitle());

        /// 이미지 처리 로직 참고 (minio url 을 만들어서 db에 저장해야됨)
        if (image != null && !image.isEmpty()) {
            String minioUrl = imageUploadService.uploadBookImage(image);
            request.setImagePath(minioUrl);
        }

        Long bookId = bookService.createBook(request);
        return ResponseEntity.created(URI.create("/api/books/" + bookId)).build();
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getCategory() {
        log.info("전체 카테고리 목록 조회 요청");
        List<CategoryDto> categories = bookService.getCategories();
        log.info("조회된 카테고리 개수: {}", categories.size());
        return ResponseEntity.ok(categories);
    }

}
