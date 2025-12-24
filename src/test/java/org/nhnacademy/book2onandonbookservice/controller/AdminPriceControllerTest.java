package org.nhnacademy.book2onandonbookservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.service.book.BookPriceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPriceControllerTest {

    @Mock
    private BookPriceService bookPriceService;

    @InjectMocks
    private AdminPriceController adminPriceController;

    @Test
    @DisplayName("성공: 할인율 업데이트 요청 - 정상 범위 및 IDLE 상태")
    void updateDiscountRate_Success() {
        int validRate = 10;
        when(bookPriceService.getUpdateStatus()).thenReturn("IDLE");

        ResponseEntity<String> response = adminPriceController.updateDiscountRate(validRate);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("가격 업데이트 작업이 시작되었습니다.");
        verify(bookPriceService).updateGlobalDiscountRate(validRate);
    }

    @Test
    @DisplayName("성공: 할인율 업데이트 요청 - 상태가 null일 때도 진행 가능")
    void updateDiscountRate_Success_NullStatus() {
        int validRate = 20;
        when(bookPriceService.getUpdateStatus()).thenReturn(null);

        ResponseEntity<String> response = adminPriceController.updateDiscountRate(validRate);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(bookPriceService).updateGlobalDiscountRate(validRate);
    }

    @Test
    @DisplayName("실패: 할인율이 0 미만인 경우 BadRequest 반환")
    void updateDiscountRate_Fail_RateUnderZero() {
        int invalidRate = -1;

        ResponseEntity<String> response = adminPriceController.updateDiscountRate(invalidRate);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("할인율은 0~100 사이여야합니다.");
        verify(bookPriceService, never()).updateGlobalDiscountRate(anyInt());
    }

    @Test
    @DisplayName("실패: 할인율이 100 초과인 경우 BadRequest 반환")
    void updateDiscountRate_Fail_RateOverHundred() {
        int invalidRate = 101;

        ResponseEntity<String> response = adminPriceController.updateDiscountRate(invalidRate);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("할인율은 0~100 사이여야합니다.");
        verify(bookPriceService, never()).updateGlobalDiscountRate(anyInt());
    }

    @Test
    @DisplayName("실패: 이미 작업이 진행 중(PROCESSING)인 경우 Conflict 반환")
    void updateDiscountRate_Fail_AlreadyProcessing() {
        int validRate = 50;
        when(bookPriceService.getUpdateStatus()).thenReturn("PROCESSING");

        ResponseEntity<String> response = adminPriceController.updateDiscountRate(validRate);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo("이미 가격 업데이트 작업이 진행 중 입니다.");
        verify(bookPriceService, never()).updateGlobalDiscountRate(anyInt());
    }

    @Test
    @DisplayName("성공: 상태 조회 - 값이 있을 경우 해당 값 반환")
    void getUpdateStatus_Exists() {
        String expectedStatus = "PROCESSING";
        when(bookPriceService.getUpdateStatus()).thenReturn(expectedStatus);

        ResponseEntity<String> response = adminPriceController.getUpdateStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedStatus);
    }

    @Test
    @DisplayName("성공: 상태 조회 - 값이 null일 경우 IDLE 반환")
    void getUpdateStatus_Null() {
        when(bookPriceService.getUpdateStatus()).thenReturn(null);

        ResponseEntity<String> response = adminPriceController.getUpdateStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("IDLE");
    }
}