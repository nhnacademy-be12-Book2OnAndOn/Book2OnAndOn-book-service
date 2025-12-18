package org.nhnacademy.book2onandonbookservice.exception;

public class GeminiQuotaExceededException extends RuntimeException {
    public GeminiQuotaExceededException(String message) {
        super(message);
    }
    public GeminiQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}