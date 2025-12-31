//package org.nhnacademy.book2onandonbookservice.scheduler;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyLong;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.doThrow;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.times;
//import static org.mockito.Mockito.verify;
//
//import java.util.Collections;
//import java.util.List;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.nhnacademy.book2onandonbookservice.entity.BookEnrichmentTask;
//import org.nhnacademy.book2onandonbookservice.repository.BookEnrichmentTaskRepository;
//import org.nhnacademy.book2onandonbookservice.service.enrichment.BookEnrichmentService;
//import org.springframework.data.domain.PageRequest;
//
//@ExtendWith(MockitoExtension.class)
//class BookEnrichmentSchedulerTest {
//
//    @InjectMocks
//    private BookEnrichmentScheduler scheduler;
//
//    @Mock
//    private BookEnrichmentTaskRepository taskRepository;
//
//    @Mock
//    private BookEnrichmentService enrichmentService;
//
//    @Test
//    @DisplayName("initMigration: 초기화 메서드 호출 확인")
//    void initMigration() {
//        scheduler.initMigration();
//        verify(taskRepository).initTasksFromBook();
//    }
//
//    @Test
//    @DisplayName("processEnrichmentBatch: 처리할 작업이 없을 때 종료")
//    void processEnrichmentBatch_NoTasks() {
//        given(taskRepository.findTasksToProcess(any(PageRequest.class)))
//                .willReturn(Collections.emptyList());
//
//        scheduler.processEnrichmentBatch();
//
//        verify(taskRepository, never()).markAsProcessing(anyLong());
//        verify(enrichmentService, never()).enrichBookDataWithStatusUpdate(any());
//    }
//
//    @Test
//    @DisplayName("processEnrichmentBatch: 작업 선점 실패(updated=0) 시 건너뜀")
//    void processEnrichmentBatch_MarkAsProcessingFailed() {
//        BookEnrichmentTask task = BookEnrichmentTask.builder().bookId(1L).build();
//        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(List.of(task));
//        // 선점 실패 상황 (다른 서버가 이미 가져감)
//        given(taskRepository.markAsProcessing(1L)).willReturn(0);
//
//        scheduler.processEnrichmentBatch();
//
//        verify(enrichmentService, never()).enrichBookDataWithStatusUpdate(any());
//    }
//
//    @Test
//    @DisplayName("processEnrichmentBatch: 정상 수행 및 상태 저장 확인")
//    void processEnrichmentBatch_Success() {
//        BookEnrichmentTask task = BookEnrichmentTask.builder().bookId(1L).build();
//        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(List.of(task));
//        // 선점 성공
//        given(taskRepository.markAsProcessing(1L)).willReturn(1);
//
//        scheduler.processEnrichmentBatch();
//
//        // enrichBookDataWithStatusUpdate 메서드가 내부에서 상태 저장까지 처리
//        verify(enrichmentService).enrichBookDataWithStatusUpdate(task);
//    }
//
//    @Test
//    @DisplayName("processEnrichmentBatch: 서비스 예외 발생 시에도 정상 처리")
//    void processEnrichmentBatch_ServiceException() {
//        BookEnrichmentTask task = BookEnrichmentTask.builder().bookId(1L).build();
//        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(List.of(task));
//        given(taskRepository.markAsProcessing(1L)).willReturn(1);
//
//        // enrichBookDataWithStatusUpdate에서 예외 발생
//        doThrow(new RuntimeException("Service Error"))
//                .when(enrichmentService).enrichBookDataWithStatusUpdate(task);
//
//        scheduler.processEnrichmentBatch();
//
//        // 예외가 발생해도 메서드는 호출됨 (내부에서 finally로 처리)
//        verify(enrichmentService).enrichBookDataWithStatusUpdate(task);
//    }
//
//    @Test
//    @DisplayName("processEnrichmentBatch: 다건 처리 확인")
//    void processEnrichmentBatch_MultipleTasks() {
//        BookEnrichmentTask task1 = BookEnrichmentTask.builder().bookId(1L).build();
//        BookEnrichmentTask task2 = BookEnrichmentTask.builder().bookId(2L).build();
//        given(taskRepository.findTasksToProcess(any(PageRequest.class)))
//                .willReturn(List.of(task1, task2));
//        given(taskRepository.markAsProcessing(anyLong())).willReturn(1);
//
//        scheduler.processEnrichmentBatch();
//
//        // 두 작업 모두 처리
//        verify(enrichmentService, times(2)).enrichBookDataWithStatusUpdate(any());
//    }
//
//    @Test
//    @DisplayName("processEnrichmentBatch: 일부 작업 선점 실패 시 성공한 작업만 처리")
//    void processEnrichmentBatch_PartialSuccess() {
//        BookEnrichmentTask task1 = BookEnrichmentTask.builder().bookId(1L).build();
//        BookEnrichmentTask task2 = BookEnrichmentTask.builder().bookId(2L).build();
//        BookEnrichmentTask task3 = BookEnrichmentTask.builder().bookId(3L).build();
//
//        given(taskRepository.findTasksToProcess(any(PageRequest.class)))
//                .willReturn(List.of(task1, task2, task3));
//
//        // task1: 선점 성공, task2: 선점 실패, task3: 선점 성공
//        given(taskRepository.markAsProcessing(1L)).willReturn(1);
//        given(taskRepository.markAsProcessing(2L)).willReturn(0);
//        given(taskRepository.markAsProcessing(3L)).willReturn(1);
//
//        scheduler.processEnrichmentBatch();
//
//        // task1과 task3만 처리됨
//        verify(enrichmentService, times(2)).enrichBookDataWithStatusUpdate(any());
//    }
//
//    @Test
//    @DisplayName("reviveQuotaVictims: 자정 부활 로직 호출 확인")
//    void reviveQuotaVictims() {
//        scheduler.reviveQuotaVictims();
//
//        verify(taskRepository).resetAladinQuotaFailedTasks();
//        verify(taskRepository).resetAiQuotaFailedTasks();
//    }
//}