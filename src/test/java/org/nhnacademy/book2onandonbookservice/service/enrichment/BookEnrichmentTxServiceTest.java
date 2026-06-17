package org.nhnacademy.book2onandonbookservice.service.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.domain.EnrichmentStatus;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookEnrichmentTask;
import org.nhnacademy.book2onandonbookservice.repository.BookContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookEnrichmentTaskRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookPublisherRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.ContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;

@ExtendWith(MockitoExtension.class)
class BookEnrichmentTxServiceTest {

    @InjectMocks
    private BookEnrichmentTxService txService;

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookEnrichmentTaskRepository taskRepository;
    @Mock
    private AladinApiClient aladinApiClient;
    @Mock
    private CategoryEnrichmentService categoryService;
    @Mock
    private TagEnrichmentService tagService;
    @Mock
    private TagGenerationService tagGenerationService;
    @Mock
    private PublisherRepository publisherRepository;
    @Mock
    private ContributorRepository contributorRepository;
    @Mock
    private BookPublisherRepository bookPublisherRepository;
    @Mock
    private BookContributorRepository bookContributorRepository;
    @Mock
    private ImageUploadService imageUploadService;

    @Test
    @DisplayName("fetchOutsideTx - 성공 (알라딘 & AI 모두 처리 대상)")
    void fetchOutsideTx_Success() throws Exception {
        Long bookId = 1L;
        Book book = mock(Book.class);
        when(book.getIsbn()).thenReturn("1234567890");
        when(book.getStatus()).thenReturn(BookStatus.ON_SALE);
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));

        BookEnrichmentTask task = BookEnrichmentTask.builder()
                .bookId(bookId)
                .aladinStatus(EnrichmentStatus.PENDING)
                .aiStatus(EnrichmentStatus.PENDING)
                .build();
        when(taskRepository.findById(bookId)).thenReturn(Optional.of(task));

        AladinApiResponse.Item aladinItem = new AladinApiResponse.Item();
        aladinItem.setCover("http://cover.url");
        when(aladinApiClient.searchByIsbn("1234567890")).thenReturn(aladinItem);
        when(imageUploadService.uploadImageFromUrl("http://cover.url")).thenReturn("internal-url");

        BookEnrichmentTxService.FetchResult result = txService.fetchOutsideTx(bookId);

        assertThat(result.aladinOutcome()).isEqualTo(BookEnrichmentTxService.Outcome.DONE);
        assertThat(result.aiOutcome()).isEqualTo(BookEnrichmentTxService.Outcome.DONE);
        assertThat(result.newThumbnailInternalUrl()).isEqualTo("internal-url");
    }

    @Test
    @DisplayName("fetchOutsideTx - 스킵 (이미 완료된 작업)")
    void fetchOutsideTx_Skipped(){
        Long bookId = 1L;
        Book book = mock(Book.class);
        when(book.getStatus()).thenReturn(BookStatus.ON_SALE);
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));

        BookEnrichmentTask task = BookEnrichmentTask.builder()
                .bookId(bookId)
                .aladinStatus(EnrichmentStatus.DONE)
                .aiStatus(EnrichmentStatus.DONE)
                .build();
        when(taskRepository.findById(bookId)).thenReturn(Optional.of(task));

        BookEnrichmentTxService.FetchResult result = txService.fetchOutsideTx(bookId);

        assertThat(result.aladinOutcome()).isEqualTo(BookEnrichmentTxService.Outcome.SKIPPED);
        assertThat(result.aiOutcome()).isEqualTo(BookEnrichmentTxService.Outcome.SKIPPED);
    }

    @Test
    @DisplayName("applyInShortTx - 성공")
    void applyInShortTx_Success() {
        Long bookId = 1L;
        BookEnrichmentTask task = BookEnrichmentTask.builder()
                .bookId(bookId)
                .aladinStatus(EnrichmentStatus.PENDING)
                .aiStatus(EnrichmentStatus.PENDING)
                .build();
        when(taskRepository.findByIdForUpdate(bookId)).thenReturn(Optional.of(task));

        Book book = mock(Book.class);
        when(book.getStatus()).thenReturn(BookStatus.ON_SALE);
        when(bookRepository.findByIdWithRelationsForUpdate(bookId)).thenReturn(Optional.of(book));

        BookEnrichmentTxService.FetchResult fetchResult = new BookEnrichmentTxService.FetchResult(
                bookId, false,
                BookEnrichmentTxService.Outcome.DONE, null, new AladinApiResponse.Item(),
                BookEnrichmentTxService.Outcome.DONE, null, null,
                "new-thumb"
        );

        BookEnrichmentTxService.ApplyResult result = txService.applyInShortTx(fetchResult);

        assertThat(result.needReindex()).isTrue();
        assertThat(task.getAladinStatus()).isEqualTo(EnrichmentStatus.DONE);
        assertThat(task.getAiStatus()).isEqualTo(EnrichmentStatus.DONE);
        verify(taskRepository).saveAndFlush(task);
    }

    @Test
    @DisplayName("applyInShortTx - 삭제된 도서 처리")
    void applyInShortTx_DeletedBook() {
        Long bookId = 1L;
        BookEnrichmentTask task = BookEnrichmentTask.builder()
                .bookId(bookId)
                .aladinStatus(EnrichmentStatus.PENDING)
                .aiStatus(EnrichmentStatus.PENDING)
                .build();
        when(taskRepository.findByIdForUpdate(bookId)).thenReturn(Optional.of(task));

        Book book = mock(Book.class);
        when(book.getStatus()).thenReturn(BookStatus.BOOK_DELETED);
        when(bookRepository.findByIdWithRelationsForUpdate(bookId)).thenReturn(Optional.of(book));

        BookEnrichmentTxService.FetchResult fetchResult = new BookEnrichmentTxService.FetchResult(
                bookId, false,
                BookEnrichmentTxService.Outcome.DONE, null, null,
                BookEnrichmentTxService.Outcome.DONE, null, null,
                null
        );

        txService.applyInShortTx(fetchResult);

        assertThat(task.getAladinStatus()).isEqualTo(EnrichmentStatus.DONE);
        assertThat(task.getAiStatus()).isEqualTo(EnrichmentStatus.DONE);
    }
}
