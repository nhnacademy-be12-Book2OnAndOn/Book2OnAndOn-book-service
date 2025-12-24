package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BookSearchSyncServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookSearchIndexService bookSearchIndexService;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private BookSearchSyncService bookSearchSyncService;

    @Test
    @DisplayName("성공: 카테고리 재귀 탐색 및 다중 페이지 재인덱싱 (reindexByCategoryId)")
    void reindexByCategoryId_Recursive_MultiPage_Success() {
        Long rootId = 1L;
        Category root = mock(Category.class);
        Category child = mock(Category.class);

        when(root.getId()).thenReturn(rootId);
        when(child.getId()).thenReturn(2L);
        when(root.getChildren()).thenReturn(List.of(child));
        when(child.getChildren()).thenReturn(Collections.emptyList());

        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(root));

        Book book1 = mock(Book.class);
        Book book2 = mock(Book.class);
        Book book3 = mock(Book.class);

        Pageable pageable1 = PageRequest.of(0, 1000);
        Pageable pageable2 = PageRequest.of(1, 1000);

        Page<Book> page1 = new PageImpl<>(List.of(book1, book2), pageable1, 2000);
        Page<Book> page2 = new PageImpl<>(List.of(book3), pageable2, 2000);

        when(bookRepository.findBooksByCategoryIds(anyList(), eq(pageable1))).thenReturn(page1);
        when(bookRepository.findBooksByCategoryIds(anyList(), eq(pageable2))).thenReturn(page2);

        bookSearchSyncService.reindexByCategoryId(rootId);

        verify(bookSearchIndexService).index(book1);
        verify(bookSearchIndexService).index(book2);
        verify(bookSearchIndexService).index(book3);
        verify(bookRepository, times(2)).findBooksByCategoryIds(anyList(), any(Pageable.class));
    }

    @Test
    @DisplayName("성공: 자식 카테고리가 없는 경우 단일 카테고리만 처리")
    void reindexByCategoryId_NoChildren_Success() {
        Long rootId = 10L;
        Category root = mock(Category.class);
        when(root.getId()).thenReturn(rootId);
        when(root.getChildren()).thenReturn(null);

        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(root));

        Book book = mock(Book.class);
        Page<Book> page = new PageImpl<>(List.of(book));
        when(bookRepository.findBooksByCategoryIds(anyList(), any(Pageable.class))).thenReturn(page);

        bookSearchSyncService.reindexByCategoryId(rootId);

        verify(bookSearchIndexService).index(book);
    }

    @Test
    @DisplayName("실패: 존재하지 않는 카테고리 ID 입력 시 예외 발생")
    void reindexByCategoryId_NotFound_Fail() {
        Long invalidId = 999L;
        when(categoryRepository.findById(invalidId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookSearchSyncService.reindexByCategoryId(invalidId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category not found");

        verify(bookRepository, never()).findBooksByCategoryIds(anyList(), any(Pageable.class));
    }

    @Test
    @DisplayName("성공: 태그 ID로 재인덱싱 (reindexByTagId)")
    void reindexByTagId_Success() {
        Long tagId = 5L;
        Book book = mock(Book.class);
        Page<Book> page = new PageImpl<>(List.of(book));

        when(bookRepository.findByTagId(eq(tagId), any(Pageable.class))).thenReturn(page);

        bookSearchSyncService.reindexByTagId(tagId);

        verify(bookSearchIndexService).index(book);
    }

    @Test
    @DisplayName("성공: 페이지가 비어있을 경우 인덱싱 로직 건너뜀 (reindexPaged)")
    void reindexPaged_EmptyPage_Success() {
        Long tagId = 7L;
        Page<Book> emptyPage = Page.empty();

        when(bookRepository.findByTagId(eq(tagId), any(Pageable.class))).thenReturn(emptyPage);

        bookSearchSyncService.reindexByTagId(tagId);

        verify(bookSearchIndexService, never()).index(any(Book.class));
    }
}