package org.nhnacademy.book2onandonbookservice.dto.book;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.springframework.util.StringUtils;

// 도서 검색 시 여러 권을 리스트로 보여주는(조회하는) DTO
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BookListResponse {
    private Long id;    // book_id
    private String title; // 도서 제목
    private String volume;  // 도서 권 제목

    private Long priceStandard; // 도서 정가
    private Long priceSales; // 도서 판매가
    private Double rating; //평점

    private LocalDate publisherDate;
    private List<String> contributorNames;  // 기여자 정보
    private List<String> publisherNames;    // 출판사
    private List<String> tagNames;  // 태그
    private List<String> categoryNames;   // 카테고리

    private String thumbnail;

    private String aiRecommendation;

    public static BookListResponse from(Book book) {
        String resolvedImage = book.getThumbnail();

        if (!StringUtils.hasText(resolvedImage)) {
            // 썸네일 컬럼이 비어있다면, 기존처럼 이미지 리스트에서 찾음
            resolvedImage = book.getImages().stream()
                    .findFirst()
                    .map(BookImage::getImagePath)
                    .orElse("/images/no-image.png");
        }
        List<String> categoryNamesList = new ArrayList<>();
        Category category = book.getCategory();
        while (category != null) {
            categoryNamesList.add(0, category.getCategoryName()); // 앞에 추가 (루트가 먼저)
            category = category.getParent();
        }

        return BookListResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .volume(book.getVolume())
                .priceStandard(book.getPriceStandard())
                .priceSales(book.getPriceSales())
                .rating(book.getRating())
                .publisherDate(book.getPublishDate())
                .thumbnail(resolvedImage)
                .contributorNames(book.getBookContributors().stream()
                        .map(bc -> bc.getContributor().getContributorName())
                        .toList()
                )
                .publisherNames(book.getBookPublishers().stream()
                        .map(bp -> bp.getPublisher().getPublisherName())
                        .toList()
                )
                .categoryNames(categoryNamesList)
                .tagNames(book.getBookTags().stream()
                        .map(bt -> bt.getTag().getTagName())
                        .toList()
                ).build();
    }

}
