package org.nhnacademy.book2onandonbookservice.dto.book;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

// 도서 검색 시 여러 권을 리스트로 보여주는(조회하는) DTO
@Getter
@Builder
public class BookListResponse {
    private Long id;    // book_id
    private String title; // 도서 제목
    private String volume;  // 도서 권 제목

    private Long priceStandard; // 도서 정가
    private Long priceSales; // 도서 판매가

    private String imagePath;   // 도서 이미지

    private List<String> contributorNames;  // 기여자 정보
    private List<String> publisherNames;    // 출판사
    private List<String> categoryIds;   // 카테고리
    private List<String> tagNames;  // 태그
}
