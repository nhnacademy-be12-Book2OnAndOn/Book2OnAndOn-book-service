package org.nhnacademy.book2onandonbookservice.service.category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.event.CategoryUpdatedEvent;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundCategoryException;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 카테고리 업데이트 시 사용 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final BookRepository bookRepository;


    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public Category updateCategoryName(Long categoryId, String newName) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundCategoryException(categoryId));

        String oldName = category.getCategoryName();

        // 이름이 같으면 이벤트 안 보냄
        if (Objects.equals(oldName, newName)) {
            log.info("카테고리 이름이 바뀌지 않았습니다. id={}, name={}", categoryId, newName);
            return category;
        }

        // 영속 엔티티 변경 -> DB 반영
        category.setCategoryName(newName);

        log.info("카테고리 이름 변경 사항이 업데이트 되었습니다. id={}, oldName={}, newName={}", categoryId, oldName, newName);

        // 변경 감지 이벤트 발생
        eventPublisher.publishEvent(new CategoryUpdatedEvent(categoryId, oldName, newName));

        return category;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "categoryInfo", key = "#categoryId")
    public CategoryDto getCategory(Long categoryId){
        Category category = categoryRepository.findById(categoryId).orElseThrow(()-> new NotFoundCategoryException(categoryId));

        return categoryToDto(category);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "categories", unless = "#result == null || #result.isEmpty()")
    public List<CategoryDto> getCategories() {
        List<Category> entities = categoryRepository.findAll();
        List<CategoryDto> allDtos = entities.stream().map(this::categoryToDto).toList();
        Map<Long, List<CategoryDto>> childrenMap = allDtos.stream()
                .collect(Collectors.groupingBy(dto -> dto.getParentId() != null ? dto.getParentId() : 0L));

        allDtos.forEach(dto -> {
            List<CategoryDto> children = childrenMap.get(dto.getId());
            if (children != null) {
                dto.getChildren().addAll(children);
            }
        });
        return childrenMap.getOrDefault(0L, Collections.emptyList());
    }

    public Page<BookListResponse> getBooksByCategory(Long categoryId, Pageable pageable) {
        Category rootCategory = categoryRepository.findById(categoryId)
                .orElseThrow(()-> new NotFoundCategoryException(categoryId));

        List<Long> allCategoryIds = new ArrayList<>();
        collectSubCategoryIds(rootCategory, allCategoryIds);

        Page<Book> books = bookRepository.findByCategory_IdIn(allCategoryIds, pageable);

        return books.map(BookListResponse::from);
    }


    private CategoryDto categoryToDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getCategoryName())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .build();
    }

    private void collectSubCategoryIds(Category category, List<Long> result) {
        result.add(category.getId());
        if (category.getChildren() != null) {
            for (Category child : category.getChildren()) {
                collectSubCategoryIds(child, result);
            }
        }
    }
}
