package org.nhnacademy.book2onandonbookservice.controller;

import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.service.search.BookReindexService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class ReindexController {

    private final BookReindexService bookReindexService;

    @PostMapping("/reindex")
    public ResponseEntity<String> reindex() {
        bookReindexService.reindexAll();
        return ResponseEntity.ok("Reindex completed");
    }
}