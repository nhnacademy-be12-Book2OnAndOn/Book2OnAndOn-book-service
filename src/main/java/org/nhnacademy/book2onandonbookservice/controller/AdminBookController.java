package org.nhnacademy.book2onandonbookservice.controller;

import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.service.TagGenerator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/books")
@RequiredArgsConstructor
public class AdminBookController {

    private final TagGenerator tagGenerator;

    // 호출 방법: POST http://localhost:8083/admin/books/generate-tags?limit=10
    @PostMapping("/generate-tags")
    public String generateTagsManual(@RequestParam(defaultValue = "10") int limit) {
        // 비동기로 돌리면 더 좋지만, 일단 확인을 위해 동기로 실행
        tagGenerator.generateTagsForBooks(limit);
        return limit + "권의 책에 대해 태그 생성을 시도했습니다. 로그를 확인하세요.";
    }
}