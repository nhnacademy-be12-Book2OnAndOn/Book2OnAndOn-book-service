package org.nhnacademy.book2onandonbookservice.service.book;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookLike;
import org.nhnacademy.book2onandonbookservice.repository.BookLikeRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookLikeService {
    private final BookRepository bookRepository;
    private final BookLikeRepository bookLikeRepository;
    private final StringRedisTemplate redisTemplate;

    /// 도서 좋아요 토글 기능 -> return 후 좋아요 등록(ture)/취소(false)
    @Transactional
    public BookLikeToggleResult toggleLike(Long bookId, Long userId) {
        String lockKey = "lock:like:" + userId + ":" + bookId;

        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(3));

        if (Boolean.FALSE.equals(isLocked)) {
            log.warn("중복된 좋아요 요청 차단: bookId={}, userId={}", bookId, userId);
            throw new IllegalArgumentException("이미 처리 중인 요청입니다. 잠시 후 다시 시도해주세요");
        }

        try {
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
        } finally {
            redisTemplate.delete(lockKey); //로직 종료 후 (성공하든 에러나든) 반드시 락 해제해야함
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
