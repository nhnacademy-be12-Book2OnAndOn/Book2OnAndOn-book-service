package org.nhnacademy.book2onandonbookservice.exception;

public class NotFoundCategoryException extends RuntimeException {
    public NotFoundCategoryException(Long categoryId) {
        super("해당 카테고리를 찾을 수 없습니다 ID: " + categoryId);
    }
}
