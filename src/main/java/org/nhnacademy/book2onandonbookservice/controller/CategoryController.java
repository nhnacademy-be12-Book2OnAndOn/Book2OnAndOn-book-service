package org.nhnacademy.book2onandonbookservice.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.service.category.CategoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    /// 전체 카테고리 목록조회
    @GetMapping
    public ResponseEntity<List<CategoryDto>> getCategory() {
        log.info("전체 카테고리 목록 조회 요청");
        List<CategoryDto> categories = categoryService.getCategories();
        log.info("조회된 카테고리 개수: {}", categories.size());
        return ResponseEntity.ok(categories);
    }

    /// 카테고리별 도서 조회 API (햄버거 메뉴에서 특정 카테고리를 선택했을때 자식 카테고리를 가지고있는 책들까지 전부 리턴)
    @GetMapping("/{categoryId}")
    public ResponseEntity<Page<BookListResponse>> getBooksByCategory(@PathVariable Long categoryId,
                                                                     @PageableDefault(page=0, size=12, sort="publishDate", direction = Direction.DESC) Pageable pageable){
        Page<BookListResponse> result = categoryService.getBooksByCategory(categoryId, pageable);
        return ResponseEntity.ok(result);
    }

    /// 카테고리 이름 반환 (해당 카테고리 이름 가지고 카테고리 정보 검색)
    @GetMapping("/{categoryId}/info")
    public ResponseEntity<CategoryDto> getCategoryInfo(@PathVariable Long categoryId){
        CategoryDto categoryDto = categoryService.getCategory(categoryId);
        return ResponseEntity.ok(categoryDto);
    }
}
