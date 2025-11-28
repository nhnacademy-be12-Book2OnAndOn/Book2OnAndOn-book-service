package org.nhnacademy.book2onandonbookservice.dto.book;

import lombok.Builder;
import lombok.Getter;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;

@Getter
@Builder
public class BookOrderResponse {
    private Long bookId;
    private String title;
    private Long priceSales;
    private String imageUrl;
    private boolean isWrapped;
    private Integer stockCount;
    private String stockStatus;


    public static BookOrderResponse from(Book book) {
        String imagePath = book.getImages().stream()
                .findFirst()
                .map(BookImage::getImagePath)
                .orElse("/images/no-image.png");
        return BookOrderResponse.builder()
                .bookId(book.getId())
                .title(book.getTitle())
                .priceSales(book.getPriceSales())
                .imageUrl(imagePath)
                .isWrapped(book.getIsWrapped())
                .stockCount(book.getStockCount())
                .stockStatus(book.getStockStatus())
                .build();
    }
}
