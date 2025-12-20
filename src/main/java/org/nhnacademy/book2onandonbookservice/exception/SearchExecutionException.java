package org.nhnacademy.book2onandonbookservice.exception;

public class SearchExecutionException extends RuntimeException {
    public SearchExecutionException(String message) {
        super(message);
    }
    public SearchExecutionException(String message, Throwable cause){
        super(message, cause);
    }
}
