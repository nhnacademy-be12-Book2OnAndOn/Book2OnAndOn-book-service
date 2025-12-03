package org.nhnacademy.book2onandonbookservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookLikeController {
    private final UserHeaderUtil util;


}
