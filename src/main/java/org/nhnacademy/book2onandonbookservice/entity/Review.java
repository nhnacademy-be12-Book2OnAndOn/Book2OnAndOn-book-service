package org.nhnacademy.book2onandonbookservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Review")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {
    // 리뷰 아이디
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    // 리뷰 제목
    @Column(name = "review_title", length = 200, nullable = false)
    @Size(min = 1, max = 200)
    @Setter
    private String title;

    // 리뷰 내용
    @Lob
    @Column(name = "review_content", nullable = false)
    @Setter
    private String content;

    // 평가 점수
    @Column(name = "review_score", nullable = false)
    @Setter
    private Integer score;

    // 리뷰 작성일
    @Column(name = "review_date", nullable = false)
    @Setter
    private LocalDateTime createdAt;

    // 도서 아이디
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    @Setter
    private Book book;

    // 회원 아이디
    @Column(name = "user_id", nullable = false)
    @Setter
    private Long userId;

    // 리뷰 이미지 매핑
    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @Setter
    private List<ReviewImage> images = new ArrayList<>();
}
