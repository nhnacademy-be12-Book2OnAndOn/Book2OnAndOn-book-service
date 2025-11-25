package org.nhnacademy.book2onandonbookservice.dto.book;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// BookList - 목록 조회에서 검색 조건용 dto
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BookSearchCondition {
    private String keyword; // 제목/부제목 등 도서 검색용
    private Long categoryId;    // 카테고리 필터
    private String tagName; // 태그 필터
    private String contributorName; // 기여자 검색
    private String publisherName;   // 출판사 검색

    // 가격 범위
    private Long minPrice;
    private Long maxPrice;

    // 정렬 조건: 최근 등록순, 가격순, 인기순 등
    // "RECENT", "PRICE_ASC", "PRICE_DESC", "LIKE_DESC"
    private String sort;
}
