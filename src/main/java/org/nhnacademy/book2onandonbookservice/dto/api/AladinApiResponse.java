package org.nhnacademy.book2onandonbookservice.dto.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
public class AladinApiResponse {

    private List<Item> item;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @NoArgsConstructor
    public static class Item {
        private String title;
        private String author;
        private String pubDate;
        private long priceStandard;
        private long priceSales;
        private String publisher;
        private String description;
        private String categoryName;
        private String cover; // 이미지 URL
    }
}
