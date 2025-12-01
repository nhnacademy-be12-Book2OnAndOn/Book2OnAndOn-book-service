package org.nhnacademy.book2onandonbookservice.dto.book;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockRequest {
    private Long bookId;
    private Integer quantity;

}
