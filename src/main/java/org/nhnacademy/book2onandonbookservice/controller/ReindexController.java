package org.nhnacademy.book2onandonbookservice.controller;

import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.config.RabbitMqConfig;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage.SyncType;
import org.nhnacademy.book2onandonbookservice.service.search.BookReindexService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
public class ReindexController {

    private final BookReindexService bookReindexService;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 1. 전체 도서 재인덱싱 (비상용/초기화용)
     * 주의: 데이터 양에 따라 시간이 오래 걸림
     */
    @PostMapping("/reindex")
    public ResponseEntity<String> reindexAll() {
        // @Async로 동작하므로 즉시 반환
        bookReindexService.reindexAll();
        return ResponseEntity.ok("전체 재인덱싱 작업이 백그라운드에서 시작되었습니다. 완료 여부는 로그를 확인하세요.");
    }

    /**
     * 카테고리나 태그 관련하여 직접 RabbitMQ로 쏴주는 용도 특정한 카테고리/태그에대해서 강제 리익덱싱 버튼을 누르는 경우
     */

    /**
     * 2. 특정 카테고리 강제 재인덱싱
     * (이름 변경이 아니라, 검색 결과가 이상할 때 수동으로 맞추는 용도)
     */
    @PostMapping("/reindex/category/{categoryId}")
    public ResponseEntity<String> manualReindexCategory(@PathVariable Long categoryId) {
        // 리스너를 거치지 않고 바로 큐에 메시지를 쏴서 작업을 예약함
        SearchSyncMessage message = new SearchSyncMessage(categoryId, SyncType.CATEGORY);

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.SEARCH_SYNC_EXCHANGE,
                RabbitMqConfig.SEARCH_SYNC_ROUTING_KEY,
                message
        );
        return ResponseEntity.ok("카테고리(ID:" + categoryId + ") 재인덱싱 요청이 큐에 전송되었습니다.");
    }

    /**
     * 3. 특정 태그 강제 재인덱싱
     */
    @PostMapping("/reindex/tag/{tagId}")
    public ResponseEntity<String> manualReindexTag(@PathVariable Long tagId) {
        SearchSyncMessage message = new SearchSyncMessage(tagId, SyncType.TAG);

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.SEARCH_SYNC_EXCHANGE,
                RabbitMqConfig.SEARCH_SYNC_ROUTING_KEY,
                message
        );
        return ResponseEntity.ok("태그(ID:" + tagId + ") 재인덱싱 요청이 큐에 전송되었습니다.");
    }
}