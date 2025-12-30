package org.nhnacademy.book2onandonbookservice.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.nhnacademy.book2onandonbookservice.entity.BookEnrichmentTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BookEnrichmentTaskRepository extends JpaRepository<BookEnrichmentTask, Long> {

    // 알라딘 혹은 AI 둘 중 하나라도 처리해야 할 게 남아있는 녀석들을 조회
    // 조건: (알라딘이 PENDING이거나 3번 미만 실패) OR (AI가 PENDING이거나 3번 미만 실패)
    // 단, 알라딘이 NOT_FOUND인 경우는 "할 일 없음"으로 간주하므로 조건에서 제외됨(자동)
    @Query("SELECT t FROM BookEnrichmentTask t "
            + "WHERE "
            + "   (t.aladinStatus = 'PENDING' OR (t.aladinStatus = 'FAILED' AND t.aladinRetryCount < 3)) "
            + "OR (t.aiStatus = 'PENDING' OR (t.aiStatus = 'FAILED' AND t.aiRetryCount < 3))")
    List<BookEnrichmentTask> findTasksToProcess(Pageable pageable);

    @Modifying
    @Transactional
    @Query("""
        UPDATE BookEnrichmentTask t 
        SET t.aladinStatus = CASE WHEN t.aladinStatus != 'DONE' THEN 'PROCESSING' ELSE t.aladinStatus END,
            t.aiStatus = CASE WHEN t.aiStatus != 'DONE' THEN 'PROCESSING' ELSE t.aiStatus END
        WHERE t.bookId = :bookId 
        AND (t.aladinStatus IN ('PENDING', 'FAILED') OR t.aiStatus IN ('PENDING', 'FAILED'))
    """)
    int markAsProcessing(@Param("bookId") Long bookId);

    // 초기화용
    @Modifying
    @Transactional
    @Query(value = """
            INSERT IGNORE INTO book_enrichment_task 
            (book_id, aladin_status, aladin_retry_count, ai_status, ai_retry_count)
            SELECT b.book_id, 'PENDING', 0, 'PENDING', 0
            FROM book b
            WHERE b.book_status != 'BOOK_DELETED'
            """, nativeQuery = true)
    void initTasksFromBook();

    // 쿼터(Limit)로 죽은 애들 부활시키는 쿼리도 2개로 나눠서 처리하거나 OR로 묶음
    @Modifying
    @Transactional
    @Query("UPDATE BookEnrichmentTask t "
            + "SET t.aladinStatus = 'PENDING', t.aladinRetryCount = 0 "
            + "WHERE t.aladinStatus = 'FAILED' AND (t.aladinFailReason LIKE '%limit%' OR t.aladinFailReason LIKE '%429%')")
    void resetAladinQuotaFailedTasks();

    @Modifying
    @Transactional
    @Query("UPDATE BookEnrichmentTask t "
            + "SET t.aiStatus = 'PENDING', t.aiRetryCount = 0 "
            + "WHERE t.aiStatus = 'FAILED' AND (t.aiFailReason LIKE '%limit%' OR t.aiFailReason LIKE '%quota%')")
    void resetAiQuotaFailedTasks();
}