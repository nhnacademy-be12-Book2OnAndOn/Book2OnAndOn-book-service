package org.nhnacademy.book2onandonbookservice.dto.book;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.nhnacademy.book2onandonbookservice.entity.BookLike;

@Getter
@AllArgsConstructor
@Builder
public class MyLikedBookResponse {
    private Long bookLikeId;
    private LocalDateTime createdAt; //좋아요 누른 시간
    private BookListResponse bookInfo;
    private boolean isLiked;

    public static MyLikedBookResponse from(BookLike bookLike) {
        return MyLikedBookResponse.builder()
                .bookLikeId(bookLike.getId())
                .createdAt(bookLike.getCreatedAt())
                .bookInfo(BookListResponse.from(bookLike.getBook()))
                .isLiked(true)
                .build();
    }
}
