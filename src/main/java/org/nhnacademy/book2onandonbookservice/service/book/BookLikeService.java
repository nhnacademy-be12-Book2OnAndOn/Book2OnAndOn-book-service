package org.nhnacademy.book2onandonbookservice.service.book;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookLike;
import org.nhnacademy.book2onandonbookservice.repository.BookLikeRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookLikeService {
    private final BookRepository bookRepository;
    private final BookLikeRepository bookLikeRepository;

    /// 도서 좋아요 토글 기능 -> return 후 좋아요 등록(ture)/취소(false)
    @Transactional
    public BookLikeToggleResult toggleLike(Long bookId, Long userId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("도서를 찾을 수 없습니다. bookId=" + bookId));

        boolean exists = bookLikeRepository.existsByBookIdAndUserId(bookId, userId);

        // 좋아요 취소
        if (exists) {
            bookLikeRepository.deleteByBookIdAndUserId(bookId, userId);
            book.decreaseLikeCount();
            log.info("도서 좋아요가 취소되었습니다. bookId={}, userId={}", bookId, userId);
            return new BookLikeToggleResult(false, book.getLikeCount());
        } else {
            // 좋아요 등록
            BookLike like = BookLike.builder()
                    .book(book)
                    .userId(userId)
                    .build();

            bookLikeRepository.save(like);
            book.increaseLikeCount();
            log.info("도서 좋아요가 추가되었습니다. bookId={}, userId={}", bookId, userId);
            return new BookLikeToggleResult(true, book.getLikeCount());
        }
    }

    /// 내가 좋아요한 책 ID 목록
    @Transactional(readOnly = true)
    public List<Long> getMyLikedBookIds(Long userId) {
        List<Long> ids = bookLikeRepository.findBookIdsByUserId(userId);
        log.info("사용자의 도서 좋아요 리스트를 조회합니다. ids={}, userId={}", ids.size(), userId);
        return ids;
    }

    public record BookLikeToggleResult(boolean liked, Long likeCount) {
    }
}
