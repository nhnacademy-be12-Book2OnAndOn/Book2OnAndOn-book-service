package org.nhnacademy.book2onandonbookservice.dto.review;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewEventRequest {
    private Long userId;
    private Long reviewId;
    private boolean hasImage;
}
