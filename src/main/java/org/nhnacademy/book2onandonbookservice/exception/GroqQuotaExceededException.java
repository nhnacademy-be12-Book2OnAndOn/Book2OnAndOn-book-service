package org.nhnacademy.book2onandonbookservice.exception;

public class GroqQuotaExceededException extends RuntimeException {
    public GroqQuotaExceededException(String message) {
        super(message);
    }
    public GroqQuotaExceededException(String message, Throwable cause){
        super(message, cause);
    }

}
