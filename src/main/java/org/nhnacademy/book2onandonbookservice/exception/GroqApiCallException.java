package org.nhnacademy.book2onandonbookservice.exception;

public class GroqApiCallException extends RuntimeException {
    public GroqApiCallException(String message) {
        super(message);
    }

    public GroqApiCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
