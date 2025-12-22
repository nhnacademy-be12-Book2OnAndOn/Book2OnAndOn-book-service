package org.nhnacademy.book2onandonbookservice.exception;

public class TagGenerationFailedException extends RuntimeException {
    public TagGenerationFailedException(String msg) {
        super(msg);
    }
}