package org.nhnacademy.book2onandonbookservice.dto.book;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;

// 도서 검색 시 여러 권을 리스트로 보여주는(조회하는) DTO
@Getter
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
    private String imagePath;   // 도서 이미지

    private LocalDate publisherDate;

    private List<String> contributorNames;  // 기여자 정보
    private List<String> publisherNames;    // 출판사
    private List<String> categoryIds;   // 카테고리
    private List<String> tagNames;  // 태그


    public static BookListResponse from(Book book) {
        String mainImagePath = book.getImages().stream()
                .filter(BookImage::isThumbnail)
                .findFirst()
                .map(BookImage::getImagePath)
                .orElseGet(() -> book.getImages().stream()
                        .findFirst()
                        .map(BookImage::getImagePath)
                        .orElse("/images/no-image.png"));

        return BookListResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .volume(book.getVolume())
                .priceStandard(book.getPriceStandard())
                .priceSales(book.getPriceSales())
                .rating(book.getRating())
                .publisherDate(book.getPublishDate())
                .imagePath(mainImagePath)
                .contributorNames(book.getBookContributors().stream()
                        .map(bc -> bc.getContributor().getContributorName())
                        .collect(Collectors.toList())
                )
                .publisherNames(book.getBookPublishers().stream()
                        .map(bp -> bp.getPublisher().getPublisherName())
                        .collect(Collectors.toList())
                )
                .categoryIds(book.getBookCategories().stream()
                        .map(bc -> bc.getCategory().getCategoryName())
                        .collect(Collectors.toList())
                )
                .tagNames(book.getBookTags().stream()
                        .map(bt -> bt.getTag().getTagName())
                        .collect(Collectors.toList())
                ).build();
    }

}
