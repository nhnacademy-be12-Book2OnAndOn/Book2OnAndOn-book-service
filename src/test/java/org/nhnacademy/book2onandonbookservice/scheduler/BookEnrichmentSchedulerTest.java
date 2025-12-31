package org.nhnacademy.book2onandonbookservice.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.BookEnrichmentTask;
import org.nhnacademy.book2onandonbookservice.repository.BookEnrichmentTaskRepository;
import org.nhnacademy.book2onandonbookservice.service.enrichment.BookEnrichmentService;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BookEnrichmentSchedulerTest {

    @Mock
    private BookEnrichmentTaskRepository taskRepository;

    @Mock
    private BookEnrichmentService enrichmentService;

    @InjectMocks
    private BookEnrichmentScheduler scheduler;

    @Test
    void initMigration_ExecuteWhenEmpty() {
        when(taskRepository.count()).thenReturn(0L);

        scheduler.initMigration();

        verify(taskRepository).initTasksFromBook();
    }

    @Test
    void initMigration_SkipWhenExists() {
        when(taskRepository.count()).thenReturn(10L);

        scheduler.initMigration();

        verify(taskRepository, times(0)).initTasksFromBook();
    }

    @Test
    void processEnrichmentBatch_ProcessTasks() {
        BookEnrichmentTask task1 = BookEnrichmentTask.builder().bookId(1L).build();
        BookEnrichmentTask task2 = BookEnrichmentTask.builder().bookId(2L).build();

        when(taskRepository.findTasksToProcess(any(Pageable.class))).thenReturn(List.of(task1, task2));

        doThrow(new RuntimeException("Processing Error")).when(enrichmentService).enrichBookDataWithStatusUpdate(2L);

        scheduler.processEnrichmentBatch();

        verify(enrichmentService).enrichBookDataWithStatusUpdate(1L);
        verify(enrichmentService).enrichBookDataWithStatusUpdate(2L);
    }

    @Test
    void processEnrichmentBatch_NoTasks() {
        when(taskRepository.findTasksToProcess(any(Pageable.class))).thenReturn(Collections.emptyList());

        scheduler.processEnrichmentBatch();

        verify(enrichmentService, times(0)).enrichBookDataWithStatusUpdate(any());
    }

    @Test
    void reviveQuotaVictims_Success() {
        when(taskRepository.resetAladinQuotaFailedTasks()).thenReturn(5);
        when(taskRepository.resetAiQuotaFailedTasks()).thenReturn(3);

        BookEnrichmentTask tempErrorTask = BookEnrichmentTask.builder()
                .bookId(1L)
                .aladinFailReason("Connection timed out")
                .build();

        BookEnrichmentTask permErrorTask = BookEnrichmentTask.builder()
                .bookId(2L)
                .aladinFailReason("Unknown Error Code 999")
                .build();

        when(taskRepository.findAladinMaxRetriedTasks()).thenReturn(List.of(tempErrorTask, permErrorTask));

        BookEnrichmentTask aiMaxRetryTask = BookEnrichmentTask.builder()
                .bookId(3L)
                .build();
        when(taskRepository.findAiMaxRetriedTasks()).thenReturn(List.of(aiMaxRetryTask));

        scheduler.reviveQuotaVictims();

        verify(taskRepository).resetAladinQuotaFailedTasks();
        verify(taskRepository).resetAiQuotaFailedTasks();
        verify(taskRepository, times(2)).saveAll(anyList());
    }
}