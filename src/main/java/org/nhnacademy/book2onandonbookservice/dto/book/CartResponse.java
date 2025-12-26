package org.nhnacademy.book2onandonbookservice.dto.book;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
    private Long bookId;
    private String title;
    private String thumbnailUrl;
    private int originalPrice;
    private int salePrice;
    private int stockCount;
    private boolean saleEnded; // 판매 종료 여부
    private boolean deleted; // 관리자에 의해 삭제된 상품


    public static CartResponse from(Book book) {
        String resolvedImage = book.getThumbnail();

        if (!StringUtils.hasText(resolvedImage)) {
            // 썸네일 컬럼이 비어있다면, 기존처럼 이미지 리스트에서 찾음
            resolvedImage = book.getImages().stream()
                    .findFirst()
                    .map(BookImage::getImagePath)
                    .orElse("/images/no-image.png");
        }

        boolean isSaleEnded = book.getStatus().equals(BookStatus.SOLD_OUT) || book.getStatus().equals(BookStatus.OUT_OF_STOCK);
        boolean isDeleted = book.getStatus().equals(BookStatus.BOOK_DELETED);

        return CartResponse.builder()
                .bookId(book.getId())
                .title(book.getTitle())
                .salePrice(book.getPriceSales().intValue())
                .originalPrice(book.getPriceStandard().intValue())
                .thumbnailUrl(resolvedImage)
                .stockCount(book.getStockCount())
                .saleEnded(isSaleEnded)
                .deleted(isDeleted)
                .build();
    }
}
