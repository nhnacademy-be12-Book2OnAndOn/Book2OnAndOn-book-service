package org.nhnacademy.book2onandonbookservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.config.RabbitMqConfig;
import org.nhnacademy.book2onandonbookservice.dto.message.SearchSyncMessage;
import org.nhnacademy.book2onandonbookservice.service.search.BookReindexService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReindexController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReindexControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BookReindexService bookReindexService;

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("[성공] 전체 도서 재인덱싱 요청")
    void reindexAll_Success() throws Exception {
        willDoNothing().given(bookReindexService).reindexAll();

        mockMvc.perform(post("/admin/search/reindex"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("전체 재인덱싱 작업이 백그라운드에서 시작되었습니다")));

        verify(bookReindexService).reindexAll();
    }

    @Test
    @DisplayName("[성공] 특정 카테고리 강제 재인덱싱 요청")
    void manualReindexCategory_Success() throws Exception {
        Long categoryId = 123L;

        mockMvc.perform(post("/admin/search/reindex/category/{categoryId}", categoryId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("카테고리(ID:" + categoryId + ")")));

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.SEARCH_SYNC_EXCHANGE),
                eq(RabbitMqConfig.SEARCH_SYNC_ROUTING_KEY),
                any(SearchSyncMessage.class)
        );
    }

    @Test
    @DisplayName("[성공] 특정 태그 강제 재인덱싱 요청")
    void manualReindexTag_Success() throws Exception {
        Long tagId = 456L;

        mockMvc.perform(post("/admin/search/reindex/tag/{tagId}", tagId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("태그(ID:" + tagId + ")")));

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.SEARCH_SYNC_EXCHANGE),
                eq(RabbitMqConfig.SEARCH_SYNC_ROUTING_KEY),
                any(SearchSyncMessage.class)
        );
    }

    @Test
    @DisplayName("[실패] 전체 재인덱싱 중 서비스 예외 발생")
    void reindexAll_Fail() throws Exception {

        willThrow(new RuntimeException("Reindex failed")).given(bookReindexService).reindexAll();

        mockMvc.perform(post("/admin/search/reindex"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("[실패] RabbitMQ 메시지 전송 실패")
    void manualReindexCategory_Fail() throws Exception {
        Long categoryId = 123L;

        willThrow(new RuntimeException("MQ Connection failed"))
                .given(rabbitTemplate).convertAndSend(anyString(), anyString(), any(SearchSyncMessage.class));

        mockMvc.perform(post("/admin/search/reindex/category/{categoryId}", categoryId))
                .andExpect(status().is5xxServerError());
    }
}