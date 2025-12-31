package org.nhnacademy.book2onandonbookservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.nhnacademy.book2onandonbookservice.domain.EnrichmentStatus;
import org.nhnacademy.book2onandonbookservice.entity.BookEnrichmentTask;
import org.nhnacademy.book2onandonbookservice.repository.BookEnrichmentTaskRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.enrichment.BookEnrichmentService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookEnrichmentScheduler {

    private final BookEnrichmentTaskRepository taskRepository;
    private final BookEnrichmentService enrichmentService;

    // 앱 시작 시 한 번만 실행되거나, 관리자가 호출할 메서드 (작업 테이블 초기화)
    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void initMigration() {
        if(taskRepository.count()>0){
            log.info("보강 작업 테이블이 이미 존재합니다. initMigration 스킵");
            return;
        }
        log.info("보강 작업 테이블 초기화 시작...");
        taskRepository.initTasksFromBook();
        log.info("보강 작업 테이블 초기화 완료!");
    }

    // 10초마다 실행 (API 호출 간격 조절 역할)
    @Scheduled(fixedDelay = 10000)
    @SchedulerLock(name = "enrichment_batch", lockAtLeastFor = "1s", lockAtMostFor = "50s")
    public void processEnrichmentBatch() {
        // 1. 할 일 10개 가져오기 (API Quota 고려하여 소량씩)
        List<BookEnrichmentTask> tasks = taskRepository.findTasksToProcess(PageRequest.of(0, 10));

        if(tasks.isEmpty()){
            return;
        }

        log.info("배치 작업 시작 - 대상: {}", tasks.size());

        for(BookEnrichmentTask task: tasks){

            try{
                enrichmentService.enrichBookDataWithStatusUpdate(task.getBookId());
            } catch (Exception e){
                log.error("치명적 오류 발생 (BookId: {})", task.getBookId(), e);
            }

            try{
                Thread.sleep(5000);
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }
        }

    }


    // 매일 자정 실행
    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(name = "enrichment_revive", lockAtLeastFor = "5s", lockAtMostFor = "3m")
    @Transactional
    public void reviveQuotaVictims() {
        log.info("자정입니다. API 한도 초과로 실패했던 작업들만 선별하여 부활시킵니다.");

        reviveAladinTasks();
        reviveAiTasks();

        log.info("부활작업 완료");
    }

    private void reviveAladinTasks() {
        int quotaRevived = taskRepository.resetAladinQuotaFailedTasks();
        log.info("알라딘 Quota 실패 부활: {}건", quotaRevived);

        List<BookEnrichmentTask> maxRetriedTasks = taskRepository.findAladinMaxRetriedTasks();
        int temporaryRevived = 0;

        for (BookEnrichmentTask task : maxRetriedTasks) {
            String reason = task.getAladinFailReason();
            if (isTemporaryError(reason)) {
                task.resetAladinStatus();
                temporaryRevived++;
                log.debug("알라딘 일시적 오류 부활 (BookId: {}): {}",
                        task.getBookId(), reason);
            } else {
                log.debug("알라딘 영구 실패로 판단 (BookId: {}): {}",
                        task.getBookId(), reason);
            }
        }

        if (temporaryRevived > 0) {
            taskRepository.saveAll(maxRetriedTasks);
            log.info("알라딘 일시적 오류 부활: {}건", temporaryRevived);
        }
    }

    private void reviveAiTasks() {
        // AI는 대부분 TooManyRequests이므로 무조건 부활
        int aiRevived = taskRepository.resetAiQuotaFailedTasks();
        log.info("AI 실패 부활: {}건 (대부분 TooManyRequests)", aiRevived);

        // 추가: 3번 실패한 AI 작업도 모두 부활 (AI는 거의 일시적 오류)
        List<BookEnrichmentTask> maxRetriedAiTasks = taskRepository.findAiMaxRetriedTasks();

        for (BookEnrichmentTask task : maxRetriedAiTasks) {
            task.resetAiStatus();
            log.debug("AI 최대 재시도 초과 작업 부활 (BookId: {})", task.getBookId());
        }

        if (!maxRetriedAiTasks.isEmpty()) {
            taskRepository.saveAll(maxRetriedAiTasks);
            log.info("AI 최대 재시도 작업 부활: {}건", maxRetriedAiTasks.size());
        }
    }

    private boolean isTemporaryError(String reason) {
        if (reason == null) return false;

        String lower = reason.toLowerCase();

        // 일시적 오류 패턴
        return lower.contains("timeout") ||
                lower.contains("timed out") ||
                lower.contains("network") ||
                lower.contains("connection") ||
                lower.contains("503") ||  // Service Unavailable
                lower.contains("502") ||  // Bad Gateway
                lower.contains("500") ||  // Internal Server Error
                lower.contains("504") ||  // Gateway Timeout
                lower.contains("too many") ||
                lower.contains("rate limit") ||
                lower.contains("quota") ||
                lower.contains("429");
    }

}