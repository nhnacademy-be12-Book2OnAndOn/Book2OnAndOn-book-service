package org.nhnacademy.book2onandonbookservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PurchaseVerificationUnavailableExceptionTest {
    @Test
    @DisplayName("생성자 테스트: 메시지만 받음")
    void constructor_messageOnly() {
        String message = "에러가 발생했습니다.";

        PurchaseVerificationUnavailableException exception = new PurchaseVerificationUnavailableException(message);

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
}