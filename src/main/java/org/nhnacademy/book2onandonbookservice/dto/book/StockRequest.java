package org.nhnacademy.book2onandonbookservice.dto.book;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockRequest {
    private String orderNumber;

    private List<StockItem> bookInfoDtoList;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockItem{
        private Long bookId;
        private Integer quantity;
    }
}
