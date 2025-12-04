package org.nhnacademy.book2onandonbookservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataParserExceptionTest {

    @Test
    @DisplayName("생성자 테스트: 메시지만 받음")
    void constructor_messageOnly() {
        String message = "에러가 발생했습니다.";

        DataParserException exception = new DataParserException(message);

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("생성자 테스트: 메시지와 원인을 받는 생성자")
    void constructor_messageAndCause() {
        String message = "에러에러";
        Throwable cause = new IllegalArgumentException("근본 원인");

        DataParserException exception = new DataParserException(message, cause);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getCause().getMessage()).isEqualTo("근본 원인");
    }
}