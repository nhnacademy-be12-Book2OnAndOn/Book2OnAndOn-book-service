package org.nhnacademy.book2onandonbookservice.exception;

public class AladinApiException extends RuntimeException {
    public AladinApiException(String message) {
        super(message);
    }

    public AladinApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
