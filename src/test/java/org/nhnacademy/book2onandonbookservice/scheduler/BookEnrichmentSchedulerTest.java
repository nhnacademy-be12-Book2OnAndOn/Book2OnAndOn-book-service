package org.nhnacademy.book2onandonbookservice.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.domain.EnrichmentStatus;
import org.nhnacademy.book2onandonbookservice.entity.BookEnrichmentTask;
import org.nhnacademy.book2onandonbookservice.repository.BookEnrichmentTaskRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.BookEnrichmentService;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class BookEnrichmentSchedulerTest {

    @InjectMocks
    BookEnrichmentScheduler scheduler;

    @Mock
    BookEnrichmentTaskRepository taskRepository;

    @Mock
    BookEnrichmentService enrichmentService;

    @Mock
    BookRepository bookRepository;

    @Test
    @DisplayName("initMigration 호출 시 Repository의 초기화 쿼리가 실행된다")
    void initMigration_Success() {
        scheduler.initMigration();

        then(taskRepository).should(times(1)).initTasksFromBook();
    }

    @Test
    @DisplayName("배치 실행 - 처리할 작업이 없을 경우 바로 종료된다")
    void processEnrichmentBatch_Empty() throws JsonProcessingException {
        given(taskRepository.findTasksToProcess(any(PageRequest.class)))
                .willReturn(Collections.emptyList());

        scheduler.processEnrichmentBatch();

        then(enrichmentService).should(never()).enrichBookData(any());
        then(taskRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("배치 실행 - 성공 케이스: 서비스 호출 후 DONE 상태로 변경 및 개별 저장된다")
    void processEnrichmentBatch_Success() throws JsonProcessingException {
        BookEnrichmentTask task = mock(BookEnrichmentTask.class);
        given(task.getBookId()).willReturn(1L);

        List<BookEnrichmentTask> tasks = List.of(task);
        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(tasks);

        scheduler.processEnrichmentBatch();

        then(enrichmentService).should(times(1)).enrichBookData(1L);
        then(task).should(times(1)).markDone();
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("배치 실행 - 실패 케이스: 예외 발생 시 FAILED 상태로 변경 및 저장 (재시도 횟수 미달)")
    void processEnrichmentBatch_Fail_RetryCountLow() throws JsonProcessingException {
        BookEnrichmentTask task = mock(BookEnrichmentTask.class);
        given(task.getBookId()).willReturn(1L);
        // 재시도 횟수가 3회 미만인 경우 시뮬레이션
        given(task.getRetryCount()).willReturn(1);

        List<BookEnrichmentTask> tasks = List.of(task);
        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(tasks);

        willThrow(new RuntimeException("API Error")).given(enrichmentService).enrichBookData(1L);

        scheduler.processEnrichmentBatch();

        then(task).should(times(1)).markFailed("API Error");
        then(taskRepository).should(times(1)).save(task);
        // 영구 실패 처리는 호출되지 않아야 함
        then(bookRepository).should(never()).updateStatusToDeleted(anyLong());
    }

    @Test
    @DisplayName("배치 실행 - 영구 실패 케이스: 재시도 3회 이상이고 일반 에러인 경우 책 상태를 삭제로 변경")
    void processEnrichmentBatch_Fail_PermanentFailure() throws JsonProcessingException {
        BookEnrichmentTask task = mock(BookEnrichmentTask.class);
        given(task.getBookId()).willReturn(1L);
        // 재시도 횟수가 3회 도달
        given(task.getRetryCount()).willReturn(3);

        List<BookEnrichmentTask> tasks = List.of(task);
        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(tasks);

        // 일반 에러 발생
        willThrow(new RuntimeException("Parsing Error")).given(enrichmentService).enrichBookData(1L);

        scheduler.processEnrichmentBatch();

        then(task).should(times(1)).markFailed("Parsing Error");
        then(taskRepository).should(times(1)).save(task);
        // 영구 실패 처리 로직 실행 확인
        then(bookRepository).should(times(1)).updateStatusToDeleted(1L);
    }

    @Test
    @DisplayName("배치 실행 - API 제한 에러 케이스: 재시도 3회 이상이어도 Quota/Limit 에러면 영구 삭제 처리하지 않음")
    void processEnrichmentBatch_Fail_LimitError() throws JsonProcessingException {
        BookEnrichmentTask task = mock(BookEnrichmentTask.class);
        given(task.getBookId()).willReturn(1L);
        given(task.getRetryCount()).willReturn(3);

        List<BookEnrichmentTask> tasks = List.of(task);
        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(tasks);

        // API 제한 관련 에러 발생 (429, limit, quota 등)
        willThrow(new RuntimeException("429 Too Many Requests")).given(enrichmentService).enrichBookData(1L);

        scheduler.processEnrichmentBatch();

        then(task).should(times(1)).markFailed("429 Too Many Requests");
        then(taskRepository).should(times(1)).save(task);
        // Limit 에러이므로 영구 삭제 처리는 호출되지 않아야 함
        then(bookRepository).should(never()).updateStatusToDeleted(anyLong());
    }

    @Test
    @DisplayName("테이블 정리 - 미완료 작업이 남아있으면 삭제하지 않는다")
    void cleanupTable_NotFinished() {
        given(taskRepository.countByStatusNot(EnrichmentStatus.DONE)).willReturn(5L);

        scheduler.cleanupTable();

        then(taskRepository).should(never()).deleteAll();
    }

    @Test
    @DisplayName("테이블 정리 - 모든 작업이 완료되었으면 전체 삭제한다")
    void cleanupTable_Finished() {
        given(taskRepository.countByStatusNot(EnrichmentStatus.DONE)).willReturn(0L);

        scheduler.cleanupTable();

        then(taskRepository).should(times(1)).deleteAll();
    }

    @Test
    @DisplayName("자정 쿼터 부활 - 쿼터 초과로 실패한 작업들을 리셋한다")
    void reviveQuotaVictims_Success() {
        scheduler.reviveQuotaVictims();

        then(taskRepository).should(times(1)).resetQuotaFailedTasks();
    }

    @Test
    @DisplayName("영구 실패 처리 - DB 업데이트 중 예외가 발생해도 로그를 남기고 종료된다")
    void handlePermanentFailure_ExceptionSafe() {
        Long bookId = 1L;
        willThrow(new RuntimeException("DB Connection Error"))
                .given(bookRepository).updateStatusToDeleted(bookId);

        // 예외가 던져지지 않아야 함 (내부 try-catch 확인)
        scheduler.handlePermanentFailure(bookId);

        then(bookRepository).should(times(1)).updateStatusToDeleted(bookId);
    }
}