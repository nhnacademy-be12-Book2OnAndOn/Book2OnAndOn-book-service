package org.nhnacademy.book2onandonbookservice.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class SearchWarmupMessage {
    private String keyword;
    private Long categoryId;
    private String categoryName;
    private String tagName;
    private String contributorName;
    private String publisherName;
}
