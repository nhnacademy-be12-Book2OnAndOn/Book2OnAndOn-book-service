package org.nhnacademy.book2onandonbookservice.scheduler;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.domain.EnrichmentStatus;
import org.nhnacademy.book2onandonbookservice.entity.BookEnrichmentTask;
import org.nhnacademy.book2onandonbookservice.repository.BookEnrichmentTaskRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.BookEnrichmentService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookEnrichmentScheduler {

    private final BookEnrichmentTaskRepository taskRepository;
    private final BookEnrichmentService enrichmentService;
    private final BookRepository bookRepository;

    // 앱 시작 시 한 번만 실행되거나, 관리자가 호출할 메서드 (작업 테이블 초기화)
    @Transactional
    public void initMigration() {
        log.info("보강 작업 테이블 초기화 시작...");
        taskRepository.initTasksFromBook();
        log.info("보강 작업 테이블 초기화 완료!");
    }

    // 5초마다 실행 (API 호출 간격 조절 역할)
    @Scheduled(fixedDelay = 10000)
    @SchedulerLock(name = "enrichment_batch", lockAtLeastFor = "1s", lockAtMostFor = "50s")
    public void processEnrichmentBatch() {
        // 1. 할 일 10개 가져오기 (API Quota 고려하여 소량씩)
        List<BookEnrichmentTask> tasks = taskRepository.findTasksToProcess(PageRequest.of(0, 10));

        if (tasks.isEmpty()) {
            // 할 일이 없으면 종료 (로그 너무 많이 찍히면 주석 처리)
            // log.info("보강할 작업이 없습니다.");
            return;
        }

        log.info("이번 배치 작업 대상: {}건", tasks.size());

        for (BookEnrichmentTask task : tasks) {
            try {
                // 2. 실제 보강 로직 호출 (BookEnrichmentService)
                // 주의: enrichBookData 메서드는 내부에서 트랜잭션을 새로 열어서 처리해야 함 (@Transactional(Propagation.REQUIRES_NEW))
                enrichmentService.enrichBookData(task.getBookId());
                
                // 3. 성공 마킹
                task.markDone();
                
            } catch (Exception e) {
                log.error("보강 실패 (BookId: {}) : {}", task.getBookId(), e.getMessage());
                // 4. 실패 마킹 (실패 사유 저장 + 재시도 카운트 증가)
                task.markFailed(e.getMessage());

                if(task.getRetryCount() >= 3 && !isLimitError(e.getMessage())){
                    handlePermanentFailure(task.getBookId());
                }
            }
            
            // API 속도 조절을 위해 건당 약간의 딜레이를 줄 수도 있음
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {}
        }

        // 5. 상태 업데이트 저장
        taskRepository.saveAll(tasks);
    }
    
    // 작업 완료 후 테이블 정리 메서드
    public void cleanupTable() {
        long remaining = taskRepository.countByStatusNot(EnrichmentStatus.DONE);
        if (remaining == 0) {
            log.info("모든 작업 완료! 임시 테이블을 정리합니다.");
            taskRepository.deleteAll();
        } else {
            log.warn("아직 처리되지 않은 작업이 {}건 남아있어 정리할 수 없습니다.", remaining);
        }
    }

    // 매일 자정 실행
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void reviveQuotaVictims() {
        log.info("자정입니다. API 한도 초과로 실패했던 작업들만 선별하여 부활시킵니다.");

        // 억울하게 죽은(한도초과) 애들만 살려냄.
        // 데이터가 이상해서 죽은 애들은 살리지 않음 -> 언젠가 작업 큐가 0이 됨.
        taskRepository.resetQuotaFailedTasks();
    }

    private boolean isLimitError(String message){
        if(message==null) return false;
        String msg = message.toLowerCase();

        return msg.contains("limit") || msg.contains("quota") || msg.contains("429") || msg.contains("제한");
    }

    private void handlePermanentFailure(Long bookId){
        try{
            bookRepository.findById(bookId).ifPresent(book -> {
                book.setStatus(BookStatus.BOOK_DELETED);
                bookRepository.save(book);
                log.info("[영구 실패 처리] 책 ID: {}", bookId);
            });
        } catch (Exception e) {
            log.error("영구 실패 처리 중 DB 오류 (BookId: {})", bookId, e);
        }
    }

}