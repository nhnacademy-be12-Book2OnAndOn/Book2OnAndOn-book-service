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

//        private SubInfo subInfo;
//        private SeriesInfo seriesInfo;
    }

//    @JsonIgnoreProperties(ignoreUnknown = true)
//    @Getter
//    @NoArgsConstructor
//    public static class SubInfo {
//        private List<Author> authors;
//
//    }
//
//    @JsonIgnoreProperties(ignoreUnknown = true)
//    @Getter
//    @NoArgsConstructor
//    public static class Author {
//        private int authorId;
//        private String authorName;      // 예: "이동욱"
//        private String authorTypeDesc;  // 예: "지은이", "옮긴이"
//        private String authorType;      // 예: "author", "translator"
//        private String authorInfo;
//    }

//    @JsonIgnoreProperties(ignoreUnknown = true)
//    @Getter
//    @NoArgsConstructor
//    public static class SeriesInfo {
//        private String seriesName;
//    }
}
