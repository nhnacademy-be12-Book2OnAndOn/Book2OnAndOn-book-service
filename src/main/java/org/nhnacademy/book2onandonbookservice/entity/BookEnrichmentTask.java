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
import org.springframework.cache.annotation.CacheEvict;

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
    @Column(nullable = false)
    private EnrichmentStatus status;

    @Column(name="retry_count")
    private int retryCount;

    @Column(name="fail_reason", columnDefinition = "TEXT")
    private String failReason;


    /// 성공 처리 메서드

    public void markDone(){
        this.status=EnrichmentStatus.DONE;
        this.failReason=null;
    }

    public void markFailed(String reason){
        this.status=EnrichmentStatus.FAILED;
        this.retryCount++;
        this.failReason=reason;
    }

}
