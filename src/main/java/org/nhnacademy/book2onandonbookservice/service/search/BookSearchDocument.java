package org.nhnacademy.book2onandonbookservice.service.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
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
@Document(indexName = "book2onandon-books",
        createIndex = false)    // 인덱스 이름
// nori 분석기 설정 (한글 분석기)
@Setting(settingPath = "static/elastic-settings.json")
@JsonIgnoreProperties(ignoreUnknown = true)
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

    //설명
    @Field(type = FieldType.Text, analyzer = "korean_search")
    private String description; // 가중치 50


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



    // 카테고리 id 인덱싱
    @Field(type = FieldType.Keyword)
    private List<String> categoryIds;
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
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    private LocalDate publishDate;

    // 도서 정가
    @Field(type = FieldType.Long)
    private Long priceStandard;

    // 도서 판매가
    @Field(type = FieldType.Long)
    private Long priceSales;

    //썸네일 이미지 경로 (검색에는 사용 X, 저장만함)
    @Field(type = FieldType.Keyword, index = false, docValues = false)
    private String imagePath;

    @Field(type = FieldType.Long)
    private Long popularity; // 인기도

    @Field(type = FieldType.Long)
    private Long reviewCount; // 리뷰 수

    @Field(type = FieldType.Double)
    private Double reviewRating; // 평점

    // --- Ollama (BGE-M3) 1024차원 벡터 ---
    @Field(type = FieldType.Dense_Vector, dims = 1024)
    private List<Float> embedding;
}
