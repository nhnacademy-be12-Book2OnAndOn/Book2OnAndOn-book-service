package org.nhnacademy.book2onandonbookservice.service.search;

import jakarta.persistence.Id;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

// Elastic Search 도구 이용 코드 -> 검색 시 이용
@Getter
@NoArgsConstructor
@Document(indexName = "books")  // 인덱스 이름
// nori 분석기 설정 (한글 분석기)
@Setting(settingPath = "static/elastic-settings.json")
@Builder
@AllArgsConstructor
public class BookSearchDocument {
    @Id
    private Long id;    // Book.id와 동일

    // isbn 검색
    @Field(type = FieldType.Keyword)
    private String isbn;

    // 도서 제목 검색
    @Field(type = FieldType.Text, analyzer = "korean_nori_analyzer")
    private String title;

    // 도서 권
    @Field(type = FieldType.Text, analyzer = "korean_nori_analyzer")
    private String volume;

    // 기여자 이름
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "korean_nori_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private List<String> contributorNames;

    // 출판사
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "korean_nori_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private List<String> publisherNames;

    // 카테고리명
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "korean_nori_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private List<String> categoryNames;

    // 태그
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "korean_nori_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private List<String> tagNames;

    // 출판일
    @Field(type = FieldType.Date)
    private LocalDate publishDate;

    // 도서 정가
    @Field(type = FieldType.Long)
    private Long priceStandard;

    // 도서 판매가
    @Field(type = FieldType.Long)
    private Long priceSales;
}
