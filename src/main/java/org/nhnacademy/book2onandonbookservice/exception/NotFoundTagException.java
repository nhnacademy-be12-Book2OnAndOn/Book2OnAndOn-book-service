package org.nhnacademy.book2onandonbookservice.exception;

public class NotFoundTagException extends RuntimeException {
    public NotFoundTagException(Long tagId) {
        super("Tag not found: " + tagId);
    }
}