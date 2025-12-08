package org.nhnacademy.book2onandonbookservice.dto.book;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookImageDto {
    private Long id;
    private String url;
    private boolean isThumbnail;

    public static BookImageDto from(BookImage bookImage) {
        return BookImageDto.builder()
                .id(bookImage.getId())
                .url(bookImage.getImagePath())
                .isThumbnail(bookImage.isThumbnail())
                .build();
    }
}
