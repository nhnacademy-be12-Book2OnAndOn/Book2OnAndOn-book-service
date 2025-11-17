package org.nhnacademy.book2onandonbookservice.exception;

public class DataParserException extends RuntimeException {

    public DataParserException(String message) {
        super(message);
    }

    /**
     *
     * @param message 예외 메시지
     * @param cause   근본 원인이 되는 예외
     */
    public DataParserException(String message, Throwable cause) {
        super(message, cause);
    }


}
