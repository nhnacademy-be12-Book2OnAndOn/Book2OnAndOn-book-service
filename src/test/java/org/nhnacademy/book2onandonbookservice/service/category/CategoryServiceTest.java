package org.nhnacademy.book2onandonbookservice.service.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.event.CategoryUpdatedEvent;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundCategoryException;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @InjectMocks
    private CategoryService categoryService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private BookRepository bookRepository;

    @Test
    @DisplayName("카테고리 이름 수정 - 성공 (이벤트 발행 확인)")
    void updateCategoryName_Success() {

        Long categoryId = 1L;
        String oldName = "Old Name";
        String newName = "New Name";

        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", categoryId);
        category.setCategoryName(oldName);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        categoryService.updateCategoryName(categoryId, newName);

        assertThat(category.getCategoryName()).isEqualTo(newName);
        verify(eventPublisher).publishEvent(any(CategoryUpdatedEvent.class));
    }

    @Test
    @DisplayName("카테고리 이름 수정 - 변경 사항 없음 (이벤트 발행 안 함)")
    void updateCategoryName_NoChange() {

        Long categoryId = 1L;
        String name = "Same Name";

        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", categoryId);
        category.setCategoryName(name);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        categoryService.updateCategoryName(categoryId, name);

        assertThat(category.getCategoryName()).isEqualTo(name);
        verify(eventPublisher, never()).publishEvent(any(CategoryUpdatedEvent.class));
    }

    @Test
    @DisplayName("카테고리 이름 수정 - 존재하지 않는 카테고리")
    void updateCategoryName_NotFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategoryName(1L, "New Name"))
                .isInstanceOf(NotFoundCategoryException.class);
    }

    @Test
    @DisplayName("단건 카테고리 조회 - 성공")
    void getCategory_Success() {

        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", 1L);
        category.setCategoryName("Test");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        CategoryDto result = categoryService.getCategory(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test");
    }

    @Test
    @DisplayName("전체 카테고리 조회 - 계층 구조 변환 확인")
    void getCategories_Hierarchy() {

        Category root1 = new Category();
        ReflectionTestUtils.setField(root1, "id", 1L);
        ReflectionTestUtils.setField(root1, "categoryName", "Root1");
        ReflectionTestUtils.setField(root1, "parent", null);

        Category root2 = new Category();
        ReflectionTestUtils.setField(root2, "id", 2L);
        ReflectionTestUtils.setField(root2, "categoryName", "Root2");
        ReflectionTestUtils.setField(root2, "parent", null);

        Category child1 = new Category();
        ReflectionTestUtils.setField(child1, "id", 3L);
        ReflectionTestUtils.setField(child1, "categoryName", "Child1");
        ReflectionTestUtils.setField(child1, "parent", root1);

        when(categoryRepository.findAll()).thenReturn(List.of(root1, root2, child1));

        List<CategoryDto> result = categoryService.getCategories();

        assertThat(result).hasSize(2);

        CategoryDto dto1 = result.stream().filter(d -> d.getId().equals(1L)).findFirst().orElseThrow();
        assertThat(dto1.getChildren()).hasSize(1);
        assertThat(dto1.getChildren().get(0).getId()).isEqualTo(3L);

        CategoryDto dto2 = result.stream().filter(d -> d.getId().equals(2L)).findFirst().orElseThrow();
        assertThat(dto2.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("카테고리별 도서 조회 - 하위 카테고리 포함하여 조회")
    void getBooksByCategory() {

        Long rootId = 1L;
        Long childId = 2L;

        Category root = new Category();
        ReflectionTestUtils.setField(root, "id", rootId);
        root.setChildren(new ArrayList<>());

        Category child = new Category();
        ReflectionTestUtils.setField(child, "id", childId);
        child.setChildren(null);

        root.getChildren().add(child);

        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(root));

        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 100L);
        Page<Book> bookPage = new PageImpl<>(List.of(book));

        when(bookRepository.findByCategory_IdIn(anyList(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    List<Long> ids = invocation.getArgument(0);
                    assertThat(ids).containsExactlyInAnyOrder(rootId, childId);
                    return bookPage;
                });

        Page<BookListResponse> result = categoryService.getBooksByCategory(rootId, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("카테고리별 도서 조회 - 카테고리 없음 예외")
    void getBooksByCategory_NotFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());
        Pageable pageable = PageRequest.of(0, 10);
        assertThatThrownBy(() -> categoryService.getBooksByCategory(1L, pageable))
                .isInstanceOf(NotFoundCategoryException.class);
    }
}