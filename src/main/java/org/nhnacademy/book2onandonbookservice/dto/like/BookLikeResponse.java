package org.nhnacademy.book2onandonbookservice.dto.like;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 좋아요 응답
@AllArgsConstructor
@Getter
@NoArgsConstructor
@Builder
public class BookLikeResponse {
    private Long bookId;
    private Long userId;
    private boolean liked;  // 사용자가 좋아요를 눌렀는지의 여부

}
