package org.nhnacademy.book2onandonbookservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.service.book.StockService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final StockService stockService;

    /**
     * [재고 확정 리스너]
     * Feign 호출 실패 시, Order 서비스가 보낸 RabbitMQ 메시지를 처리
     */
    @RabbitListener(queues = "${rabbitmq.queue.confirm}")
    public void handleStockConfirm(String orderNumber) {
        log.info("[RabbitMQ] 재고 확정 메시지 수신 - orderNumber: {}", orderNumber);
        try {
            stockService.confirmStock(orderNumber);
        } catch (Exception e) {
            log.error("[RabbitMQ] 재고 확정 처리 실패 -> DLQ로 이동", e);
            // 예외를 던져야 RabbitMQ가 실패로 처리하고 DLQ로 보냄
            throw e;
        }
    }

    /**
     * [재고 취소 리스너]
     * 결제 취소/실패 시 RabbitMQ 메시지 처리
     */
    @RabbitListener(queues = "${rabbitmq.queue.cancel}")
    public void handleStockCancel(String orderNumber) {
        log.info("[RabbitMQ] 재고 취소 메시지 수신 - orderNumber: {}", orderNumber);
        try {
            stockService.cancelStock(orderNumber);
        } catch (Exception e) {
            log.error("[RabbitMQ] 재고 취소 처리 실패 -> DLQ로 이동", e);
            throw e;
        }
    }
}