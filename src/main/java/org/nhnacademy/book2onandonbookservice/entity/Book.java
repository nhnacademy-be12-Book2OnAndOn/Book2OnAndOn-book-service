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
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
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
    @Column(name = "book_title", nullable = false)
    @Size(min = 1, max = 255)
    private String title;

    // 권 제목
    @Setter
    @Column(name = "book_volume")
    @Size(max = 500)
    private String volume;

    // 목차
    @Setter
    @Lob // 해당 필드가 매우 큰 객체임을 명시하는 어노테이션
    @Column(name = "book_chapter", columnDefinition = "LONGTEXT")
    private String chapter;

    // 책 설명
    @Setter
    @Lob
    @Column(name = "book_description", columnDefinition = "LONGTEXT")
    private String description;

    // 정가
    @Setter
    @Column(name = "price_standard", nullable = false)
    private Long priceStandard;

    //평점
    @Column(name = "book_rating") // DB 컬럼명
    @Builder.Default // 빌더 패턴 사용 시 기본값 적용되도록 함
    private Double rating = 0.0;

    // 포장 여부 ->  해당 책이 포장이 가능한지, 아닌지
    @Setter
    @Column(name = "is_wrapped", nullable = false)
    private Boolean isWrapped;

    // ISBN -> ISBN_no가 null 이면 ISBNThirteen_no는 null이어선 안됨.
    @Setter
    @Column(name = "ISBN", length = 20, nullable = false)
    @Size(min = 1, max = 20)
    private String isbn;

    // 출판 일시
    @Setter
    @Column(name = "book_publish_date", nullable = false)
    private LocalDate publishDate;


    // 판매가
    @Setter
    @Column(name = "price_sales")
    private Long priceSales;

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
    // 도서 이미지 매핑
    @Setter
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<BookImage> images = new HashSet<>();

    // 도서 카테고리 매핑
    @Setter
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<BookCategory> bookCategories = new HashSet<>();

    // 태그 매핑
    @Setter
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<BookTag> bookTags = new HashSet<>();

    // 작가 매핑
    @Setter
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<BookContributor> bookContributors = new HashSet<>();

    // 출판사 매핑
    @Setter
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<BookPublisher> bookPublishers = new HashSet<>();

    // 리뷰
    @Setter
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Review> reviews = new HashSet<>();

    // 좋아요
    @Setter
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<BookLike> likes = new HashSet<>();


    // 헬퍼 추가 -> 중복 출판사 목록 생성 및 unique constraint 오류 방지
    public boolean hasPublisher(Publisher publisher) {
        return bookPublishers.stream()
                .anyMatch(bp -> bp.getPublisher().equals(publisher));
    }

    public void addPublisher(Publisher publisher) {
        bookPublishers.add(
                BookPublisher.builder()
                        .book(this)
                        .publisher(publisher)
                        .build()
        );
    }

    public void updateRating(Double newRating) {
        this.rating = newRating;
    }

}
