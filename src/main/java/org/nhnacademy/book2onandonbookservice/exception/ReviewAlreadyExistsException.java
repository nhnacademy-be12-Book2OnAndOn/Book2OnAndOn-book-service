package org.nhnacademy.book2onandonbookservice.exception;

public class ReviewAlreadyExistsException extends RuntimeException {
    public ReviewAlreadyExistsException() {
        super("이미 해당 도서에 대한 리뷰를 작성했습니다.");
    }
}
