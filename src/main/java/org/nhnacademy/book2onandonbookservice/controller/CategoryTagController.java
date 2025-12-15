package org.nhnacademy.book2onandonbookservice.controller;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.dto.common.TagDto;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.service.category.CategoryService;
import org.nhnacademy.book2onandonbookservice.service.tag.TagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class CategoryTagController {

    private final CategoryService categoryService;
    private final TagService tagService;

    /**
     * 카테고리나 태그 이름을 수정하면 rabbitMQ 리스너가 듣고 rabbitMq로 보내서 처리 (카테고리나 태그 이름을 수정했을때 뒷단에서 이벤트리스너가 듣고 처리하는 경우)
     */
    // --- 카테고리 관리 ---

    /**
     * 카테고리 이름 수정
     * (트랜잭션 커밋 후 이벤트 리스너가 검색 엔진에 자동 반영함)
     */
    @PutMapping("/categories/{categoryId}")
    public ResponseEntity<CategoryDto> updateCategoryName(
            @PathVariable Long categoryId,
            @RequestBody UpdateRequest request
    ) {
        Category category = categoryService.updateCategoryName(categoryId, request.getNewName());
        CategoryDto responseDto = toCategoryDto(category);
        return ResponseEntity.ok(responseDto);
    }

    // --- 태그 관리 ---

    /**
     * 태그 이름 수정
     * (트랜잭션 커밋 후 이벤트 리스너가 검색 엔진에 자동 반영함)
     */
    @PutMapping("/tags/{tagId}")
    public ResponseEntity<TagDto> updateTagName(
            @PathVariable Long tagId,
            @RequestBody UpdateRequest request
    ) {
        Tag tag = tagService.updateTagName(tagId, request.getNewName());
        TagDto responseDto = toTagDto(tag);
        return ResponseEntity.ok(responseDto);
    }

    // DTO (내부 클래스로 간단히 정의하거나 별도 파일로 분리 가능)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String newName;
    }

    private CategoryDto toCategoryDto(Category category){
        if (category == null) return null;

        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getCategoryName())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .build();
    }

    private TagDto toTagDto(Tag tag) {
        if (tag == null) return null;

        return TagDto.builder()
                .id(tag.getId())
                .name(tag.getTagName())
                .build();
    }
}