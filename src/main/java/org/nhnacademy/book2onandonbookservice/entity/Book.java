package org.nhnacademy.book2onandonbookservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;

@Entity
@Table(name = "Book")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book {
    // 도서 아이디
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long id;

    // 도서 제목
    @Setter
    @Column(name = "book_title", length = 100, nullable = false)
    private String title;

    // 목차
    @Setter
    @Lob // 해당 필드가 매우 큰 객체임을 명시하는 어노테이션
    @Column(name = "book_chapter")
    private String chapter;

    // 책 설명
    @Setter
    @Lob
    @Column(name = "book_description")
    private String description;

    // 정가
    @Setter
    @Column(name = "price_standard", nullable = false)
    private Integer priceStandard;

    // 포장 여부 ->  해당 책이 포장이 가능한지, 아닌지
    @Setter
    @Column(name = "is_packed", nullable = false)
    private Boolean packed;

    // ISBN -> ISBN_no가 null 이면 ISBNThirteen_no는 null이어선 안됨.
    @Setter
    @Column(name = "ISBN", length = 20, nullable = false)
    private String isbn;

    // 출판 일시
    @Setter
    @Column(name = "book_publish_date", nullable = false)
    private java.time.LocalDate publishDate;

    // 책 재고 상태
    @Setter
    @Column(name = "stock_status", length = 50)
    private String stockStatus;

    // 판매가
    @Setter
    @Column(name = "price_sales")
    private Integer priceSales;

    // 책 재고량
    @Setter
    @Column(name = "stock_count")
    private Integer stockCount;

    // 도서 상태
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "book_status", length = 30, nullable = false)
    private BookStatus status;

    /*연관 관계 설정*/
    // 도서 이미지
    @Setter
    @OneToMany(mappedBy = "Book", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<BookImage> images = new ArrayList<>();

    // 도서 카테고리
    @Setter
    @OneToMany(mappedBy = "Book", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookCategory> bookCategories = new ArrayList<>();
}
