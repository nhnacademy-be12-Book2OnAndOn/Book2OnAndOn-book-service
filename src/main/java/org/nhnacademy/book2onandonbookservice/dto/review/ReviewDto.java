package org.nhnacademy.book2onandonbookservice.dto.review;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.nhnacademy.book2onandonbookservice.entity.Review;

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
    private LocalDate createdAt;    // 리뷰 생성 일시


    private List<ReviewImageDto> images;    // 이미지 경로 저장 리스트


    /// 헬퍼 메소드
    public static ReviewDto from(Review review) {

        //리뷰에 달린 이미지 엔티티들을 문자열(URL) 리스트로 변환
        List<ReviewImageDto> images = review.getImages().stream()
                .map(image -> ReviewImageDto.builder()
                        .id(image.getId())
                        .imagePath(image.getImagePath())
                        .build()) // 이미지 객체에서 경로만 쏙 뺌
                .toList();

        // 빌더로 DTO 생성 후 반환
        return ReviewDto.builder()
                .id(review.getId())
                .userId(review.getUserId()) // 닉네임이 필요하면 User-Service 통신 필요 (일단 ID로)
                .title(review.getTitle())
                .content(review.getContent())
                .score(review.getScore())
                .createdAt(review.getCreatedAt().toLocalDate())
                .images(images)
                .build();
    }
}
