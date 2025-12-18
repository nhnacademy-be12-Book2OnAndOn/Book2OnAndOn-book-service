package org.nhnacademy.book2onandonbookservice.exception;

public class OllamaApiException extends RuntimeException {
    public OllamaApiException(String message) {
        super(message);
    }
    public OllamaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
