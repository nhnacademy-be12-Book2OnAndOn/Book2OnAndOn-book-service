package org.nhnacademy.book2onandonbookservice.dto.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
public class GoogleBooksApiResponse {
    private int totalItems;
    private List<Item> items;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @NoArgsConstructor
    public static class Item {
        private VolumeInfo volumeInfo;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @NoArgsConstructor
    public static class VolumeInfo {
        private String title;
        private String description; //책 설명
        private List<String> categories; // 카테고리
        private ImageLinks imageLinks; //이미지링크
        private String infoLink; // 상세정보 링크 (목차 대용으로 사용?가능할듯합니다)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @NoArgsConstructor
    public static class ImageLinks {
        private String smallThumbnail;
        private String thumbnail; //사용할 이미지 URL
    }

}
