package org.nhnacademy.book2onandonbookservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "BookCategory",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_book_category",
                        columnNames = {"book_id", "category_id"}
                )}
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// 도서 - 카테고리 (N:M 조인 테이블)
public class BookCategory {
    // 도서-카테고리 아이디
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_category_id")
    private Long id;

    // 도서 아이디
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    // 카테고리 아이디
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
}
