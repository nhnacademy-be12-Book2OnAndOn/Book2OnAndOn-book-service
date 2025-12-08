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
    private LocalDateTime createdAt;
    private BookListResponse bookInfo;

    public static MyLikedBookResponse from(BookLike bookLike) {
        return MyLikedBookResponse.builder()
                .bookLikeId(bookLike.getId())
                .createdAt(bookLike.getCreatedAt())
                .bookInfo(BookListResponse.from(bookLike.getBook()))
                .build();
    }
}
