package org.nhnacademy.book2onandonbookservice.service.enrichment;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.enrichment.BookEnrichmentTxService.ApplyResult;
import org.nhnacademy.book2onandonbookservice.service.enrichment.BookEnrichmentTxService.FetchResult;
import org.nhnacademy.book2onandonbookservice.service.enrichment.BookEnrichmentTxService.Outcome;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;

@ExtendWith(MockitoExtension.class)
class BookEnrichmentServiceTest {

    @Mock
    private BookEnrichmentTxService txService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ImageUploadService imageUploadService;

    @Mock
    private BookSearchIndexService bookSearchIndexService;

    @InjectMocks
    private BookEnrichmentService enrichmentService;

    @Test
    @DisplayName("정상 동작 - 이전 썸네일 삭제 및 ES 재색인")
    void enrichBookDataWithStatusUpdate_Success() {
        Long bookId = 1L;
        String oldThumbnail = "old.jpg";
        String newThumbnail = "new.jpg";

        FetchResult fetchResult = new FetchResult(
                bookId, false, Outcome.DONE, null, null,
                Outcome.DONE, null, new BookContentDto(null, null), newThumbnail
        );
        ApplyResult applyResult = new ApplyResult(true, true, oldThumbnail);
        Book book = Book.builder().id(bookId).build();

        when(txService.fetchOutsideTx(bookId)).thenReturn(fetchResult);
        when(txService.applyInShortTx(fetchResult)).thenReturn(applyResult);
        when(bookRepository.findByIdWithRelations(bookId)).thenReturn(Optional.of(book));

        enrichmentService.enrichBookDataWithStatusUpdate(bookId);

        verify(imageUploadService).remove(oldThumbnail);
        verify(bookSearchIndexService).index(book);
    }

    @Test
    @DisplayName("업로드했으나 사용되지 않은 새 썸네일 정리 (롤백 시나리오)")
    void enrichBookDataWithStatusUpdate_CleanupUnusedThumbnail() {
        Long bookId = 1L;
        String newThumbnail = "new.jpg";

        FetchResult fetchResult = new FetchResult(
                bookId, false, Outcome.FAILED, "error", null,
                Outcome.SKIPPED, null, null, newThumbnail
        );
        ApplyResult applyResult = new ApplyResult(false, false, null);

        when(txService.fetchOutsideTx(bookId)).thenReturn(fetchResult);
        when(txService.applyInShortTx(fetchResult)).thenReturn(applyResult);

        enrichmentService.enrichBookDataWithStatusUpdate(bookId);

        verify(imageUploadService).remove(newThumbnail);
        verify(bookSearchIndexService, never()).index(any());
    }

    @Test
    @DisplayName("이미지 삭제 중 예외 발생 시 로그만 찍고 진행")
    void enrichBookDataWithStatusUpdate_ImageRemoveException() {
        Long bookId = 1L;
        String oldThumbnail = "old.jpg";

        FetchResult fetchResult = new FetchResult(bookId, false, Outcome.DONE, null, null, Outcome.SKIPPED, null, null, null);
        ApplyResult applyResult = new ApplyResult(false, false, oldThumbnail);

        when(txService.fetchOutsideTx(bookId)).thenReturn(fetchResult);
        when(txService.applyInShortTx(fetchResult)).thenReturn(applyResult);
        doThrow(new RuntimeException("S3 Error")).when(imageUploadService).remove(oldThumbnail);

        enrichmentService.enrichBookDataWithStatusUpdate(bookId);

        verify(imageUploadService).remove(oldThumbnail);
    }

    @Test
    @DisplayName("ES 재색인 중 예외 발생 시 로그만 찍고 진행")
    void enrichBookDataWithStatusUpdate_ESIndexException() {
        Long bookId = 1L;

        FetchResult fetchResult = new FetchResult(bookId, false, Outcome.DONE, null, null, Outcome.SKIPPED, null, null, null);
        ApplyResult applyResult = new ApplyResult(true, false, null);
        Book book = Book.builder().id(bookId).build();

        when(txService.fetchOutsideTx(bookId)).thenReturn(fetchResult);
        when(txService.applyInShortTx(fetchResult)).thenReturn(applyResult);
        when(bookRepository.findByIdWithRelations(bookId)).thenReturn(Optional.of(book));
        doThrow(new RuntimeException("ES Error")).when(bookSearchIndexService).index(book);

        enrichmentService.enrichBookDataWithStatusUpdate(bookId);

        verify(bookSearchIndexService).index(book);
    }
}