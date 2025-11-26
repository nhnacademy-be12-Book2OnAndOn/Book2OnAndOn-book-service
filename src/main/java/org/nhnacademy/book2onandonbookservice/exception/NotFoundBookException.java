package org.nhnacademy.book2onandonbookservice.exception;

public class NotFoundBookException extends RuntimeException {
    public NotFoundBookException(Long bookId) {
        super("해당 도서를 찾을 수 없습니다 ID: " + bookId);
    }
}
