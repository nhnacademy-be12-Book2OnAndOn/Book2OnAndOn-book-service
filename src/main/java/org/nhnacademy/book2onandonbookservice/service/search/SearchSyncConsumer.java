package org.nhnacademy.book2onandonbookservice.service.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.config.RabbitMqConfig;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage.SyncType;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchSyncConsumer {
    private final BookSearchSyncService bookSearchSyncService;

    @RabbitListener(queues = RabbitMqConfig.SEARCH_SYNC_QUEUE)
    public void consumeSyncMessage(SearchSyncMessage message) {
        log.info("[MQ - consumer] 검색 동기화 작업 start!!!: {}", message);
        try {
            long count = 0;
            if (message.getType() == SyncType.CATEGORY) {
                count = bookSearchSyncService.reindexByCategoryId(message.getTargetId());
            } else if (message.getType() == SyncType.TAG) {
                count = bookSearchSyncService.reindexByTagId(message.getTargetId());
            }

            log.info("[MQ - consumer] 검색 동기화 완료: type={}, id={}, reindexCount={}", message.getType(),
                    message.getTargetId(), count);
        } catch (Exception e) {
            log.error("[MQ - consumer] 동기화 처리 중 에러 발생(자동 재시도 예정): {}", message,
                    e); //예외를 여기서 던저면 RabbitMQ 기본 설정에 의해 자동으로 재시도 하거나 DLQ로 빠짐
            throw e;
        }
    }
}

/**
 * 기존 로직 : DB 커밋 -> 리스너 -> ES 인덱싱(즉시 실행) 변경 로직: DB 커밋 -> 리스너 -> RabbitMQ -> Consumer -> ES 인덱싱
 */