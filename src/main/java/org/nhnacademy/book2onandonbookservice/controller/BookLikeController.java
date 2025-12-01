package org.nhnacademy.book2onandonbookservice.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.service.book.BookLikeService;
import org.nhnacademy.book2onandonbookservice.service.book.BookLikeService.BookLikeToggleResult;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookLikeController {
    private final BookLikeService bookLikeService;

    /// 좋아요 토글 POST /books/{bookId}/likes
    @PostMapping("/{bookId}/likes")
    public ResponseEntity<BookLikeToggleResponse> toggleLike(@PathVariable Long bookId) {
        Long userId = UserHeaderUtil.getUserId();
        BookLikeToggleResult result = bookLikeService.toggleLike(bookId, userId);
        BookLikeToggleResponse body = new BookLikeToggleResponse(
                result.liked(),
                result.likeCount()
        );

        return ResponseEntity.ok(body);
    }

    /// 사용자 좋아요 목록 조회 GET /books/likes/me User Service에서 호출 후 List<Long> 형태를 받아감
    @GetMapping("/likes/me")
    public ResponseEntity<List<Long>> getMyLikedBooks() {
        Long userId = UserHeaderUtil.getUserId();
        List<Long> bookIds = bookLikeService.getMyLikedBookIds(userId);
        return ResponseEntity.ok(bookIds);
    }

    public record BookLikeToggleResponse(boolean liked, Long likeCount) {
    }
}
