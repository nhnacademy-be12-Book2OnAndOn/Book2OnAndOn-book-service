package org.nhnacademy.book2onandonbookservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "BookLike",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_book_user",
                        columnNames = {"book_id", "user_id"}
                )}
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@EntityListeners(AuditingEntityListener.class)
public class BookLike {
    // 좋아요 아이디
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_like_id")
    private Long id;

    // 도서 아이디
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    @Setter
    private Book book;

    // 회원 아이디
    @Column(name = "user_id", nullable = false)
    @Setter
    private Long userId;

    @CreatedDate //엔티티가 변경될 때 자동 감지하여 createdAt 설정
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
