package org.nhnacademy.book2onandonbookservice.exception;

public class NotFoundReviewException extends RuntimeException {
    public NotFoundReviewException(Long reviewId) {
        super("해당 도서를 찾을 수 없습니다 ID: " + reviewId);
    }
}
