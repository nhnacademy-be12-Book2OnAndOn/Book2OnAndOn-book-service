package org.nhnacademy.book2onandonbookservice.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.service.book.StockService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockEventListenerTest {

    @Mock
    private StockService stockService;

    @InjectMocks
    private StockEventListener stockEventListener;

    @Test
    @DisplayName("성공: 재고 확정 메시지 수신 및 처리 (handleStockConfirm)")
    void handleStockConfirm_Success() {
        String orderNumber = "ORDER-12345";

        stockEventListener.handleStockConfirm(orderNumber);

        verify(stockService).confirmStock(orderNumber);
    }

    @Test
    @DisplayName("실패: 재고 확정 처리 중 예외 발생 시 예외 전파 (handleStockConfirm)")
    void handleStockConfirm_Fail() {
        String orderNumber = "ORDER-FAIL";
        doThrow(new RuntimeException("DB Error"))
                .when(stockService).confirmStock(orderNumber);

        assertThatThrownBy(() -> stockEventListener.handleStockConfirm(orderNumber))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB Error");

        verify(stockService).confirmStock(orderNumber);
    }

    @Test
    @DisplayName("성공: 재고 취소 메시지 수신 및 처리 (handleStockCancel)")
    void handleStockCancel_Success() {
        String orderNumber = "ORDER-12345";

        stockEventListener.handleStockCancel(orderNumber);

        verify(stockService).cancelStock(orderNumber);
    }

    @Test
    @DisplayName("실패: 재고 취소 처리 중 예외 발생 시 예외 전파 (handleStockCancel)")
    void handleStockCancel_Fail() {
        String orderNumber = "ORDER-FAIL";
        doThrow(new RuntimeException("DB Error"))
                .when(stockService).cancelStock(orderNumber);

        assertThatThrownBy(() -> stockEventListener.handleStockCancel(orderNumber))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB Error");

        verify(stockService).cancelStock(orderNumber);
    }
}