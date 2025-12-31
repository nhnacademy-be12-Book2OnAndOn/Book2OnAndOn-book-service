package org.nhnacademy.book2onandonbookservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.nhnacademy.book2onandonbookservice.domain.EnrichmentStatus;

@Entity
@Table(name = "book_enrichment_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BookEnrichmentTask {

    @Id
    @Column(name="book_id")
    private Long bookId;

    @Enumerated(EnumType.STRING)
    @Column(name = "aladin_status", nullable = false)
    @Builder.Default
    private EnrichmentStatus aladinStatus = EnrichmentStatus.PENDING;

    @Column(name="aladin_fail_reason", columnDefinition = "TEXT")
    private String aladinFailReason;

    @Column(name="aladin_retry_count")
    @Builder.Default
    private int aladinRetryCount=0;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", nullable = false)
    @Builder.Default
    private EnrichmentStatus aiStatus= EnrichmentStatus.PENDING;

    @Column(name="ai_fail_reason", columnDefinition = "TEXT")
    private String aiFailReason;

    @Column(name="ai_retry_count")
    @Builder.Default
    private int aiRetryCount=0;


    /// 상태 변경 로직

   // --- 알라딘 메서드 ---
    public void markAladinDone(){
        this.aladinStatus = EnrichmentStatus.DONE;
        this.aladinFailReason = null;
    }

    public void markAladinFailed(String reason){
        this.aladinStatus = EnrichmentStatus.FAILED;
        this.aladinRetryCount++;
        this.aladinFailReason = reason;
    }

    public void markAladinNotFound(){
        this.aladinStatus = EnrichmentStatus.NOT_FOUND;
        this.aladinFailReason = "ISBN 조회 결과 없음 (Aladin API)";
    }
    public void resetAladinStatus() {
        this.aladinStatus = EnrichmentStatus.PENDING;
        this.aladinRetryCount = 0;
        this.aladinFailReason = null;
    }

    // --- AI 메서드 ---
    public void markAiDone(){
        this.aiStatus = EnrichmentStatus.DONE;
        this.aiFailReason = null;
    }

    public void markAiFailed(String reason){
        this.aiStatus = EnrichmentStatus.FAILED;
        this.aiRetryCount++;
        this.aiFailReason = reason;
    }

    public void resetAiStatus() {
        this.aiStatus = EnrichmentStatus.PENDING;
        this.aiRetryCount = 0;
        this.aiFailReason = null;
    }


    public void markAllDoneBecauseBookDeleted() {
        this.aladinStatus = EnrichmentStatus.DONE;
        this.aladinFailReason = null;
        this.aiStatus = EnrichmentStatus.DONE;
        this.aiFailReason = null;
    }

    public void markAllFailedBecauseBookMissing() {
        markAladinFailed("Book not found");
        markAiFailed("Book not found");
    }

}
