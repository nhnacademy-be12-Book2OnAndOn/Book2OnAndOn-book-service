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

        if(tasks.isEmpty()){
            return;
        }

        log.info("배치 작업 시작 - 대상: {}", tasks.size());

        for(BookEnrichmentTask task: tasks){
            try{
                enrichmentService.enrichBookData(task);
            }catch (Exception e){
                log.error("치명적 오류 발생", e);
            }finally {
                try{
                    taskRepository.save(task);
                }catch (Exception saveEx){
                    log.error("Task 상태 저장 실패", saveEx);
                }
            }

            try{
                Thread.sleep(500);
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }
        }

    }
    


    // 매일 자정 실행
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void reviveQuotaVictims() {
        log.info("자정입니다. API 한도 초과로 실패했던 작업들만 선별하여 부활시킵니다.");

        // 억울하게 죽은(한도초과) 애들만 살려냄.
        // 데이터가 이상해서 죽은 애들은 살리지 않음 -> 언젠가 작업 큐가 0이 됨.
        taskRepository.resetAladinQuotaFailedTasks();
        taskRepository.resetAiQuotaFailedTasks();

        log.info("부활작업 완료");
    }
    

}