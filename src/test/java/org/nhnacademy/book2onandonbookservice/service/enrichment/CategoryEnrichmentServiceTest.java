package org.nhnacademy.book2onandonbookservice.service.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.exception.CategoryResolveException;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryEnrichmentServiceTest {

    @InjectMocks
    private CategoryEnrichmentService categoryEnrichmentService;

    @Mock
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("enrich: 카테고리 정보가 null이거나 비어있으면 예외 발생")
    void enrich_NoCategoryInfo() {
        Book book = new Book();
        AladinApiResponse.Item item = new AladinApiResponse.Item();
        
        ReflectionTestUtils.setField(item, "categoryName", "");

        assertThatThrownBy(() -> categoryEnrichmentService.enrich(book, item))
                .isInstanceOf(CategoryResolveException.class);

        ReflectionTestUtils.setField(item, "categoryName", null);
        assertThatThrownBy(() -> categoryEnrichmentService.enrich(book, item))
                .isInstanceOf(CategoryResolveException.class);
    }

    @Test
    @DisplayName("enrich: 기존 카테고리 트리가 존재할 경우 매핑 성공")
    void enrich_ExistingCategoryTree() {
        Book book = new Book();
        AladinApiResponse.Item item = new AladinApiResponse.Item();
        ReflectionTestUtils.setField(item, "categoryName", "Domestic > Novel");

        Category domestic = Category.builder().id(1L).categoryName("Domestic").build();
        Category novel = Category.builder().id(2L).categoryName("Novel").parent(domestic).build();

        when(categoryRepository.findByCategoryNameAndParent("Domestic", null))
                .thenReturn(Optional.of(domestic));
        when(categoryRepository.findByCategoryNameAndParent("Novel", domestic))
                .thenReturn(Optional.of(novel));

        categoryEnrichmentService.enrich(book, item);

        assertThat(book.getCategory()).isEqualTo(novel);
        assertThat(book.getCategory().getParent()).isEqualTo(domestic);
        verify(categoryRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("enrich: 카테고리가 존재하지 않을 경우 새로 생성하여 연결")
    void enrich_CreateNewCategoryTree() {
        Book book = new Book();
        AladinApiResponse.Item item = new AladinApiResponse.Item();
        ReflectionTestUtils.setField(item, "categoryName", "Science > Physics");

        when(categoryRepository.findByCategoryNameAndParent(any(), any()))
                .thenReturn(Optional.empty());

        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        categoryEnrichmentService.enrich(book, item);

        Category leaf = book.getCategory();
        assertThat(leaf.getCategoryName()).isEqualTo("Physics");
        assertThat(leaf.getParent().getCategoryName()).isEqualTo("Science");
        assertThat(leaf.getParent().getParent()).isNull();

        verify(categoryRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("enrich: 경로에 빈 문자열이 포함된 경우 무시하고 진행")
    void enrich_SkipEmptyParts() {
        Book book = new Book();
        AladinApiResponse.Item item = new AladinApiResponse.Item();
        ReflectionTestUtils.setField(item, "categoryName", "Domestic > > Novel");

        Category domestic = Category.builder().id(1L).categoryName("Domestic").build();
        Category novel = Category.builder().id(2L).categoryName("Novel").parent(domestic).build();

        when(categoryRepository.findByCategoryNameAndParent("Domestic", null))
                .thenReturn(Optional.of(domestic));
        when(categoryRepository.findByCategoryNameAndParent("Novel", domestic))
                .thenReturn(Optional.of(novel));

        categoryEnrichmentService.enrich(book, item);

        assertThat(book.getCategory()).isEqualTo(novel);
    }

    @Test
    @DisplayName("enrich: 유효한 카테고리 이름이 하나도 없으면 파싱 실패 예외 발생")
    void enrich_ParseFailure_AllEmpty() {
        Book book = new Book();
        AladinApiResponse.Item item = new AladinApiResponse.Item();
        ReflectionTestUtils.setField(item, "categoryName", "> >   ");

        assertThatThrownBy(() -> categoryEnrichmentService.enrich(book, item))
                .isInstanceOf(CategoryResolveException.class);
    }
}