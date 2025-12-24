package org.nhnacademy.book2onandonbookservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookSearchControllerTest {

    @Mock
    private BookSearchService bookSearchService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private BookSearchController bookSearchController;

    @Test
    @DisplayName("성공: 도서 검색 요청 시 결과 페이지 반환")
    void searchBooks_Success() {
        BookSearchCondition condition = new BookSearchCondition();
        condition.setKeyword("Java");
        condition.setUseAiSearch(false);
        condition.setCategoryName("IT");

        Pageable pageable = PageRequest.of(0, 10);
        Page<BookListResponse> emptyPage = new PageImpl<>(Collections.emptyList());

        when(bookSearchService.search(any(BookSearchCondition.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        ResponseEntity<Page<BookListResponse>> response = bookSearchController.searchBooks(condition, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(emptyPage);
        verify(bookSearchService).search(condition, pageable);
    }

    @Test
    @DisplayName("성공: AI 결과 조회 - 캐시 히트 (카테고리 ID 포함)")
    void getAiResult_Hit_WithCategory() {
        String keyword = "Spring";
        Long categoryId = 1L;
        String expectedKey = "ai:result:Spring:1";
        String cachedJson = "{\"result\":\"exist\"}";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(cachedJson);

        ResponseEntity<String> response = bookSearchController.getAiResult(keyword, categoryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(cachedJson);
    }

    @Test
    @DisplayName("성공: AI 결과 조회 - 캐시 히트 (카테고리 ID 없음 -> all)")
    void getAiResult_Hit_NoCategory() {
        String keyword = "Java";
        Long categoryId = null;
        String expectedKey = "ai:result:Java:all";
        String cachedJson = "{\"result\":\"all\"}";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(cachedJson);

        ResponseEntity<String> response = bookSearchController.getAiResult(keyword, categoryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(cachedJson);
    }

    @Test
    @DisplayName("성공: AI 결과 조회 - 캐시 미스 (No Content 반환)")
    void getAiResult_Miss() {
        String keyword = "Unknown";
        Long categoryId = 99L;
        String expectedKey = "ai:result:Unknown:99";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(null);

        ResponseEntity<String> response = bookSearchController.getAiResult(keyword, categoryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }
}