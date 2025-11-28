package org.nhnacademy.book2onandonbookservice.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewUpdateRequest {

    @NotNull(message = "리뷰 제목은 필수입니다.")
    private String title;   // 리뷰 제목

    @NotNull(message = "리뷰 내용은 필수입니다.")
    @Length(min = 10, max = 500, message = "리뷰는 10자 이상 500자 이하로 작성해주세요")
    private String content; // 리뷰 내용

    @NotNull(message = "별점은 필수 입니다.")
    @Min(value = 1, message = "별점은 최소 1점입니다.")
    @Max(value = 5, message = "별점은 최대 5점입니다.")
    private Integer score;  // 평가 점수

    private List<Long> deleteImageIds;
}