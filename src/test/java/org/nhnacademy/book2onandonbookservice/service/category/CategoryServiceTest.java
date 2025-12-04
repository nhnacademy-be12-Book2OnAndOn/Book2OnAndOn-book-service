package org.nhnacademy.book2onandonbookservice.service.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.event.CategoryUpdatedEvent;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @InjectMocks
    private CategoryService categoryService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("카테고리 이름 변경 성공")
    void updateCategoryName() {
        Long categoryId = 1L;
        String oldName = "ddd";
        String newName = "ttt";

        Category category1 = Category.builder()
                .id(categoryId)
                .categoryName(oldName)
                .build();

        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(category1));

        Category result = categoryService.updateCategoryName(categoryId, newName);

        assertThat(result.getCategoryName()).isEqualTo(newName);

        verify(eventPublisher).publishEvent(any(CategoryUpdatedEvent.class));
    }

    @Test
    @DisplayName("카테고리 이름 변경 무시")
    void updateCategoryName_ignore() {
        Long categoryId = 1L;
        String name = "ddd";
        Category category = Category.builder()
                .id(categoryId)
                .categoryName(name)
                .build();
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(category));

        Category result = categoryService.updateCategoryName(categoryId, name);

        assertThat(result.getCategoryName()).isEqualTo(name);
        verify(eventPublisher, never()).publishEvent(any());

    }

    @Test
    @DisplayName("카테고리 수정 실패")
    void updateCategoryName_Fail() {
        Long categoryId = 1L;

        given(categoryRepository.findById(categoryId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategoryName(categoryId, "new name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("카테고리를 찾을 수 없습니다.");
        verifyNoMoreInteractions(eventPublisher);
    }
}