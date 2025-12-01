package org.nhnacademy.book2onandonbookservice.controller;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.service.category.CategoryService;
import org.nhnacademy.book2onandonbookservice.service.search.BookReindexService;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchSyncService;
import org.nhnacademy.book2onandonbookservice.service.tag.TagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class ReindexController {

    private final BookReindexService bookReindexService;
    private final BookSearchSyncService bookSearchSyncService;
    private final CategoryService categoryService;
    private final TagService tagService;

    /**
     * 전체 도서 재인덱싱
     */
    @PostMapping("/reindex")
    public ResponseEntity<String> reindexAll() {
        bookReindexService.reindexAll();
        return ResponseEntity.ok("Reindex completed");
    }

    /**
     * 특정 카테고리에 속한 도서만 재인덱싱
     */
    @PostMapping("/reindex/category/{categoryId}")
    public ResponseEntity<String> reindexByCategory(@PathVariable Long categoryId) {
        long count = bookSearchSyncService.reindexByCategoryId(categoryId);
        return ResponseEntity.ok("Reindexed " + count + " books for categoryId=" + categoryId);
    }

    /**
     * 특정 태그를 가진 도서만 재인덱싱
     */
    @PostMapping("/reindex/tag/{tagId}")
    public ResponseEntity<String> reindexByTag(@PathVariable Long tagId) {
        long count = bookSearchSyncService.reindexByTagId(tagId);
        return ResponseEntity.ok("Reindexed " + count + " books for tagId=" + tagId);
    }

    /// 카테고리, 태그 이름 변경
    @PutMapping("/category/{categoryId}")
    public ResponseEntity<Void> updateCategoryName(
            @PathVariable Long categoryId,
            @RequestBody CategoryNameUpdateRequest request
    ) {
        categoryService.updateCategoryName(categoryId, request.getNewName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/tag/{tagId}")
    public ResponseEntity<Void> updateTagName(
            @PathVariable Long tagId,
            @RequestBody TagNameUpdateRequest request
    ) {
        tagService.updateTagName(tagId, request.getNewName());
        return ResponseEntity.noContent().build();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryNameUpdateRequest {
        private String newName;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagNameUpdateRequest {
        private String newName;
    }
}