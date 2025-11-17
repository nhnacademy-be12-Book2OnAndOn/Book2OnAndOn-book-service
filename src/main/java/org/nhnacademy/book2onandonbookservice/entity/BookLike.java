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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "BookLike")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
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
}
