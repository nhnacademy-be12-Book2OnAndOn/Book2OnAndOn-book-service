package org.nhnacademy.book2onandonbookservice.repository;

import java.util.List;
import org.nhnacademy.book2onandonbookservice.domain.EnrichmentStatus;
import org.nhnacademy.book2onandonbookservice.entity.BookEnrichmentTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface BookEnrichmentTaskRepository extends JpaRepository<BookEnrichmentTask, Long> {

    //처리해야할 작업 조회 (펜딩 상태이거나, 실패했지만 재시도 횟수가 3회미만일때
    @Query("SELECT t FROM BookEnrichmentTask t "
            + "WHERE t.status= 'PENDING' "
            + "OR (t.status = 'FAILED' AND t.retryCount < 3)")
    List<BookEnrichmentTask> findTasksToProcess(Pageable pageable);

    //초기화용: Book 테이블의 ID를 몽땅 긁어와서 Task 테이블에 넣는 쿼리 (속도를 위해 Native Query 사용)
    @Modifying
    @Transactional
    @Query(value = """
            INSERT IGNORE INTO book_enrichment_task (book_id, status, retry_count)
            SELECT b.book_id, 'PENDING', 0 
            FROM book b
            WHERE b.book_status != 'BOOK_DELETED'
            """, nativeQuery = true)
    void initTasksFromBook();

    @Modifying
    @Transactional
    @Query("UPDATE BookEnrichmentTask t SET t.status = 'PENDING', t.retryCount = 0 "
            + "WHERE t.status='FAILED' "
            + "AND (t.failReason LIKE '%제한%' OR t.failReason LIKE '%limit%')")
    void resetQuotaFailedTasks();

    long countByStatusNot(EnrichmentStatus status);
}
