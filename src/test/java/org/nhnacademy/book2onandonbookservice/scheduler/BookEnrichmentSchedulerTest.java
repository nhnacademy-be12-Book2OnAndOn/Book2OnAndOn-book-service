package org.nhnacademy.book2onandonbookservice.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    @DisplayName("초기화(initMigration) 호출 시 Repository의 초기화 쿼리가 실행되어야 한다")
    void initMigration_Success() {
        // When
        scheduler.initMigration();

        // Then
        then(taskRepository).should(times(1)).initTasksFromBook();
    }

    @Test
    @DisplayName("배치 실행 - 처리할 작업이 없을 경우(Empty) 바로 종료")
    void processEnrichmentBatch_Empty() throws JsonProcessingException {
        // Given
        given(taskRepository.findTasksToProcess(any(PageRequest.class)))
                .willReturn(Collections.emptyList());

        // When
        scheduler.processEnrichmentBatch();

        // Then
        then(enrichmentService).should(never()).enrichBookData(any());
        then(taskRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("배치 실행 - 성공 케이스: 서비스 호출 후 DONE 상태로 변경 및 저장")
    void processEnrichmentBatch_Success() throws JsonProcessingException {
        // Given
        // Mock 객체 생성 (실제 엔티티 대신 Mock을 쓰거나, Builder로 생성)
        BookEnrichmentTask task = mock(BookEnrichmentTask.class);
        given(task.getBookId()).willReturn(1L);

        List<BookEnrichmentTask> tasks = List.of(task);
        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(tasks);

        // When
        scheduler.processEnrichmentBatch();

        // Then
        then(enrichmentService).should(times(1)).enrichBookData(1L); // 서비스 호출 확인
        then(task).should(times(1)).markDone(); // 성공 마킹 확인
        then(taskRepository).should(times(1)).saveAll(tasks); // 저장 확인
    }

    @Test
    @DisplayName("배치 실행 - 실패 케이스: 서비스 예외 발생 시 FAILED 상태로 변경 및 저장")
    void processEnrichmentBatch_Fail() throws JsonProcessingException {
        // Given
        BookEnrichmentTask task = mock(BookEnrichmentTask.class);
        given(task.getBookId()).willReturn(1L);

        List<BookEnrichmentTask> tasks = List.of(task);
        given(taskRepository.findTasksToProcess(any(PageRequest.class))).willReturn(tasks);

        // 서비스가 예외를 던지도록 설정
        willThrow(new RuntimeException("API Error")).given(enrichmentService).enrichBookData(1L);

        // When
        scheduler.processEnrichmentBatch();

        // Then
        then(enrichmentService).should(times(1)).enrichBookData(1L);
        then(task).should(times(1)).markFailed("API Error"); // 실패 마킹 확인
        then(taskRepository).should(times(1)).saveAll(tasks); // 상태 업데이트 저장 확인
    }

    @Test
    @DisplayName("테이블 정리 - 아직 미완료 작업이 남아있으면 삭제하지 않음")
    void cleanupTable_NotFinished() {
        // Given: 완료되지 않은(DONE이 아닌) 작업이 5개 남음
        given(taskRepository.countByStatusNot(EnrichmentStatus.DONE)).willReturn(5L);

        // When
        scheduler.cleanupTable();

        // Then
        then(taskRepository).should(never()).deleteAll(); // 삭제 호출되면 안 됨
    }

    @Test
    @DisplayName("테이블 정리 - 모든 작업이 완료되었으면 전체 삭제")
    void cleanupTable_Finished() {
        // Given: 남은 작업 0개
        given(taskRepository.countByStatusNot(EnrichmentStatus.DONE)).willReturn(0L);

        // When
        scheduler.cleanupTable();

        // Then
        then(taskRepository).should(times(1)).deleteAll(); // 삭제 호출되어야 함
    }
}