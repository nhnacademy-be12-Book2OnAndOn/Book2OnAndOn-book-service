package org.nhnacademy.book2onandonbookservice.controller;

import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.service.search.BookReindexService;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class ReindexController {

    private final BookReindexService bookReindexService;
    private final BookSearchSyncService bookSearchSyncService;

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
}