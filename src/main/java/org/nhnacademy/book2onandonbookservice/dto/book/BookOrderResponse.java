package org.nhnacademy.book2onandonbookservice.dto.book;

import lombok.Builder;
import lombok.Getter;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.springframework.util.StringUtils;

@Getter
@Builder
public class BookOrderResponse {
    private Long bookId;
    private String title;
    private Long priceSales;
    private String imageUrl;
    private boolean isWrapped;
    private Integer stockCount;
    private BookStatus status;
    private Long categoryId;


    public static BookOrderResponse from(Book book) {
        String resolvedImage = book.getThumbnail();

        if (!StringUtils.hasText(resolvedImage)) {
            // 썸네일 컬럼이 비어있다면, 기존처럼 이미지 리스트에서 찾음
            resolvedImage = book.getImages().stream()
                    .findFirst()
                    .map(BookImage::getImagePath)
                    .orElse("/images/no-image.png");
        }


        return BookOrderResponse.builder()
                .bookId(book.getId())
                .title(book.getTitle())
                .priceSales(book.getPriceSales())
                .imageUrl(resolvedImage)
                .isWrapped(book.getIsWrapped())
                .stockCount(book.getStockCount())
                .status(book.getStatus())
                .categoryId(book.getCategory().getId())
                .build();
    }
}
