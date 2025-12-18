package org.nhnacademy.book2onandonbookservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewAlreadyExistsExceptionTest {

    @Test
    @DisplayName("생성자 테스트: 메시지만 받음")
    void constructor_messageOnly() {

        ReviewAlreadyExistsException exception = new ReviewAlreadyExistsException();

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("이미 해당 도서에 대한 리뷰를 작성했습니다.");
        assertThat(exception.getCause()).isNull();
    }
}