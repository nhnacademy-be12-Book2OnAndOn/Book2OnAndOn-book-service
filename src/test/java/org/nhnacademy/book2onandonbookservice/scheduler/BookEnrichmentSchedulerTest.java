package org.nhnacademy.book2onandonbookservice.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class BookEnrichmentSchedulerTest {

    @InjectMocks
    private BookEnrichmentScheduler scheduler;

    @Mock
    private BookEnrichmentTaskRepository taskRepository;

    @Mock
    private BookEnrichmentService enrichmentService;


    @Test
    void initMigration() {
        scheduler.initMigration();

        verify(taskRepository).initTasksFromBook();
    }

    @Test
    void processEnrichmentBatch_NoTasks() {
        given(taskRepository.findTasksToProcess(any(PageRequest.class)))
                .willReturn(Collections.emptyList());

        scheduler.processEnrichmentBatch();

        verify(enrichmentService, never()).enrichBookData(any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void processEnrichmentBatch_Success() {
        BookEnrichmentTask task = BookEnrichmentTask.builder().bookId(1L).build();
        List<BookEnrichmentTask> tasks = List.of(task);

        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(tasks);

        scheduler.processEnrichmentBatch();

        verify(enrichmentService).enrichBookData(task);
        verify(taskRepository).save(task);
    }

    @Test
    void processEnrichmentBatch_ServiceException() {
        BookEnrichmentTask task = BookEnrichmentTask.builder().bookId(1L).build();
        List<BookEnrichmentTask> tasks = List.of(task);

        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(tasks);
        willThrow(new RuntimeException("Service Error"))
                .given(enrichmentService).enrichBookData(task);

        scheduler.processEnrichmentBatch();

        verify(enrichmentService).enrichBookData(task);
        verify(taskRepository).save(task);
    }

    @Test
    void processEnrichmentBatch_SaveException() {
        BookEnrichmentTask task = BookEnrichmentTask.builder().bookId(1L).build();
        List<BookEnrichmentTask> tasks = List.of(task);

        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(tasks);
        doThrow(new RuntimeException("DB Error"))
                .when(taskRepository).save(task);

        scheduler.processEnrichmentBatch();

        verify(enrichmentService).enrichBookData(task);
        verify(taskRepository).save(task);
    }

    @Test
    void processEnrichmentBatch_MultipleTasks_WithDelay() {
        BookEnrichmentTask task1 = BookEnrichmentTask.builder().bookId(1L).build();
        BookEnrichmentTask task2 = BookEnrichmentTask.builder().bookId(2L).build();
        List<BookEnrichmentTask> tasks = List.of(task1, task2);

        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(tasks);

        scheduler.processEnrichmentBatch();

        verify(enrichmentService, times(2)).enrichBookData(any());
        verify(taskRepository, times(2)).save(any());
    }

    @Test
    void reviveQuotaVictims() {
        scheduler.reviveQuotaVictims();

        verify(taskRepository).resetAladinQuotaFailedTasks();
        verify(taskRepository).resetAiQuotaFailedTasks();
    }
}