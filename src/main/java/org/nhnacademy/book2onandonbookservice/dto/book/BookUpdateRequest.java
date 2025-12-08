package org.nhnacademy.book2onandonbookservice.dto.book;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BookUpdateRequest {
    // ISBN 검색
    private String isbn;

    // 도서 제목 관련
    private String title; // 도서 제목
    private String volume;  // 권 제목

    // 기여자 정보
    private String contributorName; // 기여자 이름

    // 출판사
    private String publisherName;   // 출판사 이름 -> 신규 출판사 생성 등록 시
    private List<Long> publisherIds;    // 출판사 아이디 -> 이미 존재하는 출판사를 선택해서 매핑하는 경우

    // 출판일
    private LocalDate publishDate;

    // 도서 가격
    private Long priceStandard; // 도서 정가
    private Long priceSales; // 도서 판매가

    // 도서 재고 관련
    private Integer stockCount; // 책 재고량
    private BookStatus status; // 책 재고 상태

    // 카테고리 및 태그, 포장 여부
    private List<Long> categoryIds; // 카테고리
    private Set<String> tagNames;  // 태그 리스트
    private Boolean isWrapped;  // 포장 여부

    // 이미지 url(경로)
    @Setter
    private String imagePath;

    // 목차
    private String chapter;

    // -- 설명 - WYSIWYG 편집 후 결과 HTML -> Book.book_description에 그대로 저장
    private String descriptionHtml;

    private List<Long> deleteImageIds;

    private int thumbnailIndex = 0;
}
