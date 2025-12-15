package org.nhnacademy.book2onandonbookservice.scheduler;

import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.DoorayHookClient;
import org.nhnacademy.book2onandonbookservice.config.RabbitMqConfig;
import org.nhnacademy.book2onandonbookservice.dto.dooray.DoorayMessagePayload;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqRetryScheduler {
    private final RabbitTemplate rabbitTemplate;
    private final DoorayHookClient doorayHookClient;
    private static final int MAX_DLQ_RETRY=3;
    private static final String HEADER_DLQ_RETRY_COUNT = "x-dlq-retry-count";

    @Value("${dooray.service.id}")
    private String serviceId;

    @Value("${dooray.bot.id}")
    private String botId;

    @Value("${dooray.bot.botToken}")
    private String botToken;

    /**
     * 매일 자정에 실행
     * DLQ에 있는 메시지를 꺼내서 원래 큐로 다시 보냄
     */
    @Scheduled(cron="0 0 0 * * *")
    public void retryDlqMessage(){
        log.info("=== [Scheduler] DLQ 재처리 작업 시작 ===");

        while(true){
            Message message = rabbitTemplate.receive(RabbitMqConfig.SEARCH_SYNC_DLQ );

            if(message == null){
                log.info("DLQ가 비어있습니다. 작업종료");
                break;
            }

            MessageProperties props = message.getMessageProperties();
            Map<String, Object> headers = props.getHeaders();

            int retryCount = 0 ;
            if(headers.containsKey(HEADER_DLQ_RETRY_COUNT)){
                retryCount=(int) headers.get(HEADER_DLQ_RETRY_COUNT);
            }

            if(retryCount >= MAX_DLQ_RETRY){
                String failedBody= new String(message.getBody());
                log.error("!!! [DLQ] 최대 재시도 횟수 초과! 메시지 폐기: {}", failedBody);
                sendDoorayAlert(failedBody, retryCount);
                //메시지 폐기 (rabbitTemplate.receive()는 Auto-Ack이므로 여기서 continue하면 삭제됨
                continue;
            }

            headers.put(HEADER_DLQ_RETRY_COUNT, retryCount+1);

            log.info("[DLQ] 메시지 재발송 (시도횟수: {}/{})", retryCount + 1, MAX_DLQ_RETRY);

            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.SEARCH_SYNC_EXCHANGE,
                    RabbitMqConfig.SEARCH_SYNC_ROUTING_KEY,
                    message
            );
        }
    }

    private void sendDoorayAlert(String failedMessageBody, int retryCount){
        try{
            DoorayMessagePayload payload = DoorayMessagePayload.builder()
                    .botName("Book-Service-Alarm")
                    .text("[긴급] 검색 인덱싱 동기화 실패 (DLQ)")
                    .attachments(Collections.singletonList(
                            DoorayMessagePayload.Attachment.builder()
                                    .title("최대 재시도 횟수("+retryCount+"회) 초과")
                                    .text("메시지 내용:\n"+ failedMessageBody)
                                    .color("red")
                                    .build()
                    ))
                    .build();

            doorayHookClient.sendMessage(serviceId, botId, botToken, payload);
            log.info("Dooray 알림 전송 완료");
        } catch (Exception e) {
            log.error("Dooray 알림 전송 실패 (설정확인 필요)",e);
        }
    }
}
