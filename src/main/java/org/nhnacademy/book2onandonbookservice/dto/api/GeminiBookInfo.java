package org.nhnacademy.book2onandonbookservice.dto.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * gemini 응답 파싱용 DTO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
public class GeminiBookInfo {
    private String publishDate;
    private String publisher;
    private Long priceStandard;
    private String author;
}
