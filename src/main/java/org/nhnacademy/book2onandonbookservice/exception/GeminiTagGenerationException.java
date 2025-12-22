package org.nhnacademy.book2onandonbookservice.exception;

public class GeminiTagGenerationException extends RuntimeException {
    public GeminiTagGenerationException(String msg, Throwable cause) {
        super(msg, cause);
    }
    public GeminiTagGenerationException(String message) {
        super(message);
    }
}