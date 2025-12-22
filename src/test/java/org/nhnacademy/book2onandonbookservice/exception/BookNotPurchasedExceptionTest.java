package org.nhnacademy.book2onandonbookservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BookNotPurchasedExceptionTest {
    @Test
    @DisplayName("생성자 테스트: 메시지만 받음")
    void constructor_messageOnly() {

        BookNotPurchasedException exception = new BookNotPurchasedException();

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getCause()).isNull();
    }

}