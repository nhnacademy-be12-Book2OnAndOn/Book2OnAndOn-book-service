package org.nhnacademy.book2onandonbookservice.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
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
    @InjectMocks
    private BookSearchSyncService bookSearchSyncService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookSearchIndexService bookSearchIndexService;

    @Mock
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("카테고리 재인덱싱 성공")
    void reindexByCategoryId_Success() {
        Long categoryId = 1L;
        Category category = createCategory(categoryId, "IT", null);

        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(category));

        Book book1 = createBook(1L, "JAVA");
        Book book2 = createBook(2L, "JAVA SpringBoot");
        Page<Book> bookPage = new PageImpl<>(List.of(book1, book2));

        given(bookRepository.findBooksByCategoryIds(eq(List.of(categoryId)), any(Pageable.class)))
                .willReturn(bookPage);

        long result = bookSearchSyncService.reindexByCategoryId(categoryId);

        assertThat(result).isEqualTo(2);
        verify(bookSearchIndexService, times(1)).index(book1);
        verify(bookSearchIndexService, times(1)).index(book2);
    }

    @Test
    @DisplayName("카테고리 재인덱싱 성공 - 하위 카테고리 포함 재귀 조회")
    void reindexByCategoryId_Success_children() {
        Category root = createCategory(1L, "Root", null);
        Category child = createCategory(2L, "child", root);
        Category grandChild = createCategory(3L, "grandChild", child);

        root.setChildren(List.of(child));
        child.setChildren(List.of(grandChild));

        given(categoryRepository.findById(1L)).willReturn(Optional.of(root));

        Page<Book> emptyPage = Page.empty();
        given(bookRepository.findBooksByCategoryIds(anyList(), any(Pageable.class))).willReturn(emptyPage);

        long result = bookSearchSyncService.reindexByCategoryId(1L);

        verify(bookRepository).findBooksByCategoryIds(
                argThat(list -> list.containsAll(List.of(1L, 2L, 3L)) && list.size() == 3),
                any(Pageable.class)
        );

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("카테고리 재인덱싱 - 실패 (카테고리 없음)")
    void reindexByCategoryId_Fail_NotFound() {
        given(categoryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookSearchSyncService.reindexByCategoryId(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    @DisplayName("태그 재인덱싱 - 성공 (페이징 동작 확인)")
    void reindexByTagId_Success_Pagination() {
        Long tagId = 5L;
        Book book1 = createBook(1L, "Book 1");
        Book book2 = createBook(2L, "Book 2");

        PageRequest page0Req = PageRequest.of(0, 1);
        Page<Book> page0 = new PageImpl<>(List.of(book1), page0Req, 2);

        PageRequest page1Req = PageRequest.of(1, 1000); // next page
        Page<Book> page1 = new PageImpl<>(List.of(book2), page1Req, 2);

        given(bookRepository.findByTagId(eq(tagId), any(Pageable.class)))
                .willAnswer(invocation -> {
                    Pageable p = invocation.getArgument(1);
                    if (p.getPageNumber() == 0) {
                        return page0;
                    }
                    if (p.getPageNumber() == 1) {
                        return page1;
                    }
                    return Page.empty();
                });

        long result = bookSearchSyncService.reindexByTagId(tagId);

        assertThat(result).isEqualTo(2);
        verify(bookSearchIndexService).index(book1);
        verify(bookSearchIndexService).index(book2);
        verify(bookRepository, times(2)).findByTagId(eq(tagId), any(Pageable.class));
    }

    @Test
    @DisplayName("태그 재인덱싱 - 데이터 없음")
    void reindexByTagId_NoData() {
        Long tagId = 10L;
        given(bookRepository.findByTagId(eq(tagId), any(Pageable.class)))
                .willReturn(Page.empty());

        long result = bookSearchSyncService.reindexByTagId(tagId);

        assertThat(result).isZero();
        verify(bookSearchIndexService, times(0)).index(any());
    }

    private Category createCategory(Long categoryId, String categoryName, Category parent) {

        return Category.builder()
                .id(categoryId)
                .categoryName(categoryName)
                .parent(parent)
                .children(new ArrayList<>())
                .build();
    }

    private Book createBook(Long id, String title) {
        Book book = mock(Book.class);
        lenient().when(book.getId()).thenReturn(id);
        lenient().when(book.getTitle()).thenReturn(title);
        return book;
    }
}