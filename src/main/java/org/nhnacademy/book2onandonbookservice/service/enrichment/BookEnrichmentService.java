package org.nhnacademy.book2onandonbookservice.service.enrichment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookEnrichmentService {

    private final BookEnrichmentTxService txService;

    private final BookRepository bookRepository;
    private final ImageUploadService imageUploadService;
    private final BookSearchIndexService bookSearchIndexService;

    /**
     * Scheduler가 호출하는 진입점(운영 안정형)
     */
    @Transactional
    public void enrichBookDataWithStatusUpdate(Long bookId) {
        BookEnrichmentTxService.FetchResult fetch = txService.fetchOutsideTx(bookId);
        BookEnrichmentTxService.ApplyResult applied = txService.applyInShortTx(fetch);

        // 커밋 이후 외부 I/O 처리(파일 삭제 / ES 재색인)

        // 1) 이전 썸네일 파일 삭제
        if (StringUtils.hasText(applied.oldThumbnailToDelete())) {
            try {
                imageUploadService.remove(applied.oldThumbnailToDelete());
            } catch (Exception e) {
                log.warn("[이미지 삭제 실패] oldThumbnail={}, bookId={}", applied.oldThumbnailToDelete(), bookId, e);
            }
        }

        // 2) 업로드는 했는데(외부호출 단계) DB 적용을 못한 경우 업로드 파일 정리
        if (StringUtils.hasText(fetch.newThumbnailInternalUrl()) && !applied.usedNewThumbnail()) {
            try {
                imageUploadService.remove(fetch.newThumbnailInternalUrl());
            } catch (Exception e) {
                log.warn("[이미지 정리 실패] newThumbnail={}, bookId={}", fetch.newThumbnailInternalUrl(), bookId, e);
            }
        }

        // 3) ES 재색인
        if (applied.needReindex()) {
            try {
                bookRepository.findByIdWithRelations(bookId).ifPresent(bookSearchIndexService::index);
            } catch (Exception e) {
                log.error("[ES Sync] 재색인 실패 (BookId:{})", bookId, e);
            }
        }
    }
}
