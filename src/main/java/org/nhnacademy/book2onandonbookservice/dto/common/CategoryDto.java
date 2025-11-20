package org.nhnacademy.book2onandonbookservice.dto.common;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    private Long id;    // category_id
    private String name;    // category_name
    private Long parentId;  // parent_id

    // 하위 카테고리
    @Builder.Default
    private List<CategoryDto> children = new ArrayList<>();
}
