package org.nhnacademy.book2onandonbookservice.dto.review;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 리뷰 응답
@AllArgsConstructor
@Getter
@NoArgsConstructor
@Builder
public class ReviewDto {
    private Long id;    // 리뷰 아이디
    private Long bookId;    // 도서 아이디
    private Long userId;    // 회원 아이디

    private String title;   // 리뷰 제목
    private String content; // 리뷰 내용
    private Integer score;  // 평가 점수
    private LocalDateTime createdAt;    // 리뷰 생성 일시


    private List<ReviewImageDto> images;    // 이미지 경로 저장 리스트
}
