package org.nhnacademy.book2onandonbookservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/books")
public class BookSearchController {

    private final BookSearchService bookSearchService;
    private final StringRedisTemplate stringRedisTemplate;

    // ES 기반 도서 검색
    @PostMapping("/search")
    public ResponseEntity<Page<BookListResponse>> searchBooks(
            @RequestBody BookSearchCondition condition,
            @PageableDefault Pageable pageable
    ) {
        log.info("Book Search Request: keyword='{}', aiMode={}, filterCategory='{}'",
                condition.getKeyword(), condition.getUseAiSearch(), condition.getCategoryName());

        Page<BookListResponse> result = bookSearchService.search(condition, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search/ai-result")
    public ResponseEntity<String> getAiResult(@RequestParam String keyword,
                                              @RequestParam(required = false) Long categoryId){
        String cacheKey = "ai:result:"+keyword+":"+ (categoryId != null? categoryId: "all");
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        log.info("aisearch 결과: {}", json);
        if(json==null){
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(json);
    }
}