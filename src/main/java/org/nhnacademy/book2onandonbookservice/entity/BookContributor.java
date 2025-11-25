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
@Table(name = "BookContributor",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_book_contributor",
                        columnNames = {"book_id", "contributor_id", "role_type"}
                )
        })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookContributor {
    // 도서-기여자 아이디
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_contributor_id")
    private Long id;

    // 역할
    @Setter
    @Column(name = "role_type", length = 50, nullable = false)
    private String roleType;

    // 기여자 아이디
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contributor_id", nullable = false)
    private Contributor contributor;

    // 도서 아이디
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;


}
