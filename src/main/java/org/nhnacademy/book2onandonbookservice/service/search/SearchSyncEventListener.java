package org.nhnacademy.book2onandonbookservice.service.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.event.CategoryUpdatedEvent;
import org.nhnacademy.book2onandonbookservice.event.TagUpdatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchSyncEventListener {

    private final BookSearchSyncService bookSearchSyncService;

    /**
     * 카테고리명 변경 후 해당 카테고리에 속한 도서들만 재인덱싱
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCategoryNameUpdated(CategoryUpdatedEvent event) {
        long count = bookSearchSyncService.reindexByCategoryId(event.categoryId());
        log.info("[ES Sync] Category updated => categoryId={}, oldName='{}', newName='{}', reindexed={}",
                event.categoryId(), event.oldName(), event.newName(), count);
    }

    /**
     * 태그명 변경 후 해당 태그를 가진 도서들만 재인덱싱
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTagNameUpdated(TagUpdatedEvent event) {
        long count = bookSearchSyncService.reindexByTagId(event.tagId());
        log.info("[ES Sync] Tag updated => tagId={}, oldName='{}', newName='{}', reindexed={}",
                event.tagId(), event.oldName(), event.newName(), count);
    }
}
