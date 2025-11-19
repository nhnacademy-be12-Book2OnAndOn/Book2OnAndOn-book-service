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
@Table(name = "BookAuthor",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_book_author",
                        columnNames = {"book_id", "author_id"}
                )
        })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookAuthor {
    // 도서-작가 아이디
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_author_id")
    private Long id;

    // 작가 아이디
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    // 도서 아이디
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;
}
