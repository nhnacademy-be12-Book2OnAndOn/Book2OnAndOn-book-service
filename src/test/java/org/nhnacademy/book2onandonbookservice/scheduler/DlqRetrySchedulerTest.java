package org.nhnacademy.book2onandonbookservice.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.DoorayHookClient;
import org.nhnacademy.book2onandonbookservice.config.RabbitMqConfig;
import org.nhnacademy.book2onandonbookservice.dto.dooray.DoorayMessagePayload;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlqRetrySchedulerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private DoorayHookClient doorayHookClient;

    @InjectMocks
    private DlqRetryScheduler dlqRetryScheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dlqRetryScheduler, "serviceId", "test-service-id");
        ReflectionTestUtils.setField(dlqRetryScheduler, "botId", "test-bot-id");
        ReflectionTestUtils.setField(dlqRetryScheduler, "botToken", "test-bot-token");
    }

    @Test
    @DisplayName("성공: DLQ가 모두 비어있을 경우 아무 작업도 하지 않음")
    void retryDlqMessage_EmptyQueues() {
        when(rabbitTemplate.receive(anyString())).thenReturn(null);

        dlqRetryScheduler.retryDlqMessage();

        verify(rabbitTemplate, times(3)).receive(anyString());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Message.class));
        verify(doorayHookClient, never()).sendMessage(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("성공: 재시도 횟수가 MAX(3) 미만인 메시지 재발송 및 헤더 카운트 증가")
    void retryDlqMessage_Resend_Success() {
        MessageProperties props = new MessageProperties();
        props.setHeader("x-dlq-retry-count", 1);
        Message message = new Message("test-body".getBytes(), props);

        when(rabbitTemplate.receive(RabbitMqConfig.SEARCH_SYNC_DLQ))
                .thenReturn(message)
                .thenReturn(null);

        when(rabbitTemplate.receive(RabbitMqConfig.QUEUE_STOCK_CONFIRM_DLQ)).thenReturn(null);
        when(rabbitTemplate.receive(RabbitMqConfig.QUEUE_STOCK_CANCEL_DLQ)).thenReturn(null);

        dlqRetryScheduler.retryDlqMessage();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.SEARCH_SYNC_EXCHANGE),
                eq(RabbitMqConfig.SEARCH_SYNC_ROUTING_KEY),
                messageCaptor.capture()
        );

        Message sentMessage = messageCaptor.getValue();
        Integer retryCount = (Integer) sentMessage.getMessageProperties().getHeaders().get("x-dlq-retry-count");
        assertThat(retryCount).isEqualTo(2);
    }

    @Test
    @DisplayName("성공: 재시도 횟수 헤더가 없는 경우(최초 DLQ 진입) 1로 설정하여 재발송")
    void retryDlqMessage_NoHeader_Resend() {
        MessageProperties props = new MessageProperties();
        Message message = new Message("body".getBytes(), props);

        when(rabbitTemplate.receive(RabbitMqConfig.QUEUE_STOCK_CONFIRM_DLQ))
                .thenReturn(message)
                .thenReturn(null);

        when(rabbitTemplate.receive(RabbitMqConfig.SEARCH_SYNC_DLQ)).thenReturn(null);
        when(rabbitTemplate.receive(RabbitMqConfig.QUEUE_STOCK_CANCEL_DLQ)).thenReturn(null);

        dlqRetryScheduler.retryDlqMessage();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.ORDER_EXCHANGE),
                eq(RabbitMqConfig.ROUTING_KEY_CONFIRM),
                messageCaptor.capture()
        );

        Integer retryCount = (Integer) messageCaptor.getValue().getMessageProperties().getHeaders().get("x-dlq-retry-count");
        assertThat(retryCount).isEqualTo(1);
    }

    @Test
    @DisplayName("성공: 재시도 횟수 초과 시 Dooray 알림 전송 및 재발송 안함")
    void retryDlqMessage_MaxRetryExceeded() {
        MessageProperties props = new MessageProperties();
        props.setHeader("x-dlq-retry-count", 3);
        Message message = new Message("failed-body".getBytes(), props);

        when(rabbitTemplate.receive(RabbitMqConfig.QUEUE_STOCK_CANCEL_DLQ))
                .thenReturn(message)
                .thenReturn(null);

        when(rabbitTemplate.receive(RabbitMqConfig.SEARCH_SYNC_DLQ)).thenReturn(null);
        when(rabbitTemplate.receive(RabbitMqConfig.QUEUE_STOCK_CONFIRM_DLQ)).thenReturn(null);

        dlqRetryScheduler.retryDlqMessage();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Message.class));
        verify(doorayHookClient).sendMessage(
                eq("test-service-id"),
                eq("test-bot-id"),
                eq("test-bot-token"),
                any(DoorayMessagePayload.class)
        );
    }

    @Test
    @DisplayName("실패: Dooray 알림 전송 중 예외 발생 시 로그 출력 후 중단되지 않음")
    void retryDlqMessage_DoorayException() {
        MessageProperties props = new MessageProperties();
        props.setHeader("x-dlq-retry-count", 3);
        Message message = new Message("body".getBytes(), props);

        when(rabbitTemplate.receive(RabbitMqConfig.SEARCH_SYNC_DLQ))
                .thenReturn(message)
                .thenReturn(null);

        when(rabbitTemplate.receive(RabbitMqConfig.QUEUE_STOCK_CONFIRM_DLQ)).thenReturn(null);
        when(rabbitTemplate.receive(RabbitMqConfig.QUEUE_STOCK_CANCEL_DLQ)).thenReturn(null);

        doThrow(new RuntimeException("Dooray API Error"))
                .when(doorayHookClient).sendMessage(anyString(), anyString(), anyString(), any());

        dlqRetryScheduler.retryDlqMessage();

        verify(doorayHookClient).sendMessage(anyString(), anyString(), anyString(), any());
    }
}