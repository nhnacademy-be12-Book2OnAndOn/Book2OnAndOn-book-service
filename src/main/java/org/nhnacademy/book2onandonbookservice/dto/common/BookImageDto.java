package org.nhnacademy.book2onandonbookservice.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookImageDto {
    private Long id;
    private String imagePath;
}
