package org.nhnacademy.book2onandonbookservice.service.search;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 카테고리/태그별 재인덱싱
@Slf4j
@Service
@RequiredArgsConstructor
public class BookSearchSyncService {

    private static final int PAGE_SIZE = 1000;

    private final BookRepository bookRepository;
    private final BookSearchIndexService bookSearchIndexService;
    private final CategoryRepository categoryRepository;

    /**
     * 특정 카테고리에 속한 책들을 모두 재인덱싱
     */
    @Transactional(readOnly = true)
    public long reindexByCategoryId(Long categoryId) {
        log.info("[ES Sync] Start reindexByCategoryId categoryId={}", categoryId);
        List<Long> targetCategoryIds = getAllCategoryIds(categoryId);
        long total = reindexPaged(pageable -> bookRepository.findBooksByCategoryIds(targetCategoryIds, pageable));
        log.info("[ES Sync] Done reindexByCategoryId rootId={} (totalIds={}) totalReindexed={}",
                categoryId, targetCategoryIds.size(), total);
        return total;
    }

    /**
     * 특정 태그를 가진 책들을 모두 재인덱싱
     */
    @Transactional(readOnly = true)
    public long reindexByTagId(Long tagId) {
        log.info("[ES Sync] Start reindexByTagId tagId={}", tagId);
        long total = reindexPaged(pageable -> bookRepository.findByTagId(tagId, pageable));
        log.info("[ES Sync] Done reindexByTagId tagId={} totalReindexed={}", tagId, total);
        return total;
    }

    /**
     * 공통 페이지네이션 재인덱싱 로직
     */
    private long reindexPaged(java.util.function.Function<Pageable, Page<Book>> pageSupplier) {
        long totalReindexed = 0L;
        Pageable pageable = PageRequest.of(0, PAGE_SIZE);

        while (true) {
            Page<Book> page = pageSupplier.apply(pageable);

            log.info("[ES Sync] Reindexing page={} size={} totalElements={}",
                    page.getNumber(), page.getSize(), page.getTotalElements());

            if (page.isEmpty()) {
                // 더 이상 인덱싱할 데이터가 없음
                break;
            }

            for (Book book : page.getContent()) {
                bookSearchIndexService.index(book);
                totalReindexed++;
            }

            if (!page.hasNext()) {
                break;
            }
            pageable = page.nextPageable();
        }

        return totalReindexed;
    }

    private List<Long> getAllCategoryIds(Long rootId) {
        Category root = categoryRepository.findById(rootId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + rootId));

        List<Long> ids = new ArrayList<>();
        ids.add(root.getId());
        collectChildIds(root, ids);
        return ids;
    }

    private void collectChildIds(Category parent, List<Long> ids) {
        if (parent.getChildren() == null || parent.getChildren().isEmpty()) {
            return;
        }
        for (Category child : parent.getChildren()) {
            ids.add(child.getId());
            collectChildIds(child, ids);
        }
    }
}