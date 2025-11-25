package org.nhnacademy.book2onandonbookservice.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "BookTag", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_book_tag_id", // 제약조건 이름 (에러 로그 볼 때 편함)
                columnNames = {"tag_id", "book_id"} // 실제 DB의 컬럼명 입력
        )
})
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BookTag {

    @EmbeddedId
    private BookTagPK pk;

    @MapsId("tagId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @MapsId("bookId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;
}
