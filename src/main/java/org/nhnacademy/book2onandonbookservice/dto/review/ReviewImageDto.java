package org.nhnacademy.book2onandonbookservice.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewImageDto {
    private Long id;
    private String imagePath;
}