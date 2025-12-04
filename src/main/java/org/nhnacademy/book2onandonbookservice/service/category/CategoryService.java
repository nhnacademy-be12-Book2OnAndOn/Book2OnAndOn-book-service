package org.nhnacademy.book2onandonbookservice.service.category;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.event.CategoryUpdatedEvent;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 카테고리 업데이트 시 사용 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @CacheEvict(value = "categories", allEntries = true, cacheManager = "RedisCacheManager")
    @Transactional
    public Category updateCategoryName(Long categoryId, String newName) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다. id=" + categoryId));

        String oldName = category.getCategoryName();

        // 이름이 같으면 이벤트 안 보냄
        if (Objects.equals(oldName, newName)) {
            log.info("카테고리 이름이 바뀌지 않았습니다. id={}, name={}" + categoryId, newName);
            return category;
        }

        // 영속 엔티티 변경 -> DB 반영
        category.setCategoryName(newName);

        log.info("카테고리 이름 변경 사항이 업데이트 되었습니다. id={}, oldName={}, newName={}", categoryId, oldName, newName);

        // 변경 감지 이벤트 발생
        eventPublisher.publishEvent(new CategoryUpdatedEvent(categoryId, oldName, newName));

        return category;
    }
}
