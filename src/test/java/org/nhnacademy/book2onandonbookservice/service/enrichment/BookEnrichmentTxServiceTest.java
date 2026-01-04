package org.nhnacademy.book2onandonbookservice.service.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.nhnacademy.book2onandonbookservice.entity.*;
import org.nhnacademy.book2onandonbookservice.repository.*;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookEnrichmentTxServiceTest {

    @InjectMocks
    private BookEnrichmentTxService enrichmentTxService;

    @Mock private BookRepository bookRepository;
    @Mock private BookEnrichmentTaskRepository taskRepository;
    @Mock private AladinApiClient aladinApiClient;
    @Mock private CategoryEnrichmentService categoryService;
    @Mock private TagEnrichmentService tagService;
    @Mock private PublisherRepository publisherRepository;
    @Mock private ContributorRepository contributorRepository;
    @Mock private BookPublisherRepository bookPublisherRepository;
    @Mock private BookContributorRepository bookContributorRepository;
    @Mock private ImageUploadService imageUploadService;

    private Book testBook;
    private BookEnrichmentTask testTask;

    @BeforeEach
    void setUp() {
        testBook = Book.builder()
                .isbn("1234567890123")
                .title("Original Title")
                .status(BookStatus.ON_SALE)
                .bookPublishers(new HashSet<>())
                .bookContributors(new HashSet<>())
                .images(new HashSet<>())
                .build();
        ReflectionTestUtils.setField(testBook, "id", 1L);

        testTask = BookEnrichmentTask.builder()
                .bookId(1L)
                .aladinStatus(EnrichmentStatus.PENDING)
                .aiStatus(EnrichmentStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("fetchOutsideTx - Success")
    void fetchOutsideTx_success() throws JsonProcessingException {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        AladinApiResponse.Item aladinItem = new AladinApiResponse.Item();
        aladinItem.setTitle("New Title");
        aladinItem.setCover("http://aladin.com/cover.jpg");
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(aladinItem);
        when(imageUploadService.uploadImageFromUrl(anyString())).thenReturn("http://minio/cover.jpg");

        BookContentDto aiContent = mock(BookContentDto.class);
        when(tagService.generateContent(any(), any(), any())).thenReturn(aiContent);

        BookEnrichmentTxService.FetchResult result = enrichmentTxService.fetchOutsideTx(1L);

        assertThat(result.aladinOutcome()).isEqualTo(BookEnrichmentTxService.Outcome.DONE);
        assertThat(result.aiOutcome()).isEqualTo(BookEnrichmentTxService.Outcome.DONE);
        assertThat(result.newThumbnailInternalUrl()).isEqualTo("http://minio/cover.jpg");
        assertThat(result.aladinItem()).isNotNull();
    }

    @Test
    @DisplayName("fetchOutsideTx - Task Not Found")
    void fetchOutsideTx_noTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.empty());
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        BookEnrichmentTxService.FetchResult result = enrichmentTxService.fetchOutsideTx(1L);

        assertThat(result.aladinOutcome()).isEqualTo(BookEnrichmentTxService.Outcome.SKIPPED);
    }

    @Test
    @DisplayName("fetchOutsideTx - Book Not Found")
    void fetchOutsideTx_noBook() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(bookRepository.findById(1L)).thenReturn(Optional.empty());

        BookEnrichmentTxService.FetchResult result = enrichmentTxService.fetchOutsideTx(1L);

        assertThat(result.aladinOutcome()).isEqualTo(BookEnrichmentTxService.Outcome.FAILED);
        assertThat(result.aladinFailReason()).contains("Book not found");
    }

    @Test
    @DisplayName("fetchOutsideTx - Book Deleted")
    void fetchOutsideTx_deletedBook() throws JsonProcessingException {
        ReflectionTestUtils.setField(testBook, "status", BookStatus.BOOK_DELETED);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        BookEnrichmentTxService.FetchResult result = enrichmentTxService.fetchOutsideTx(1L);

        assertThat(result.bookDeleted()).isTrue();
        assertThat(result.aladinOutcome()).isEqualTo(BookEnrichmentTxService.Outcome.SKIPPED);
        verify(aladinApiClient, never()).searchByIsbn(anyString());
    }

    @Test
    @DisplayName("fetchOutsideTx - Aladin Not Found")
    void fetchOutsideTx_aladinNotFound() throws JsonProcessingException {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(null);

        BookEnrichmentTxService.FetchResult result = enrichmentTxService.fetchOutsideTx(1L);

        assertThat(result.aladinOutcome()).isEqualTo(BookEnrichmentTxService.Outcome.NOT_FOUND);
    }

    @Test
    @DisplayName("fetchOutsideTx - Aladin Exception")
    void fetchOutsideTx_aladinException() throws JsonProcessingException {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(aladinApiClient.searchByIsbn(anyString())).thenThrow(new RuntimeException("API Error"));

        BookEnrichmentTxService.FetchResult result = enrichmentTxService.fetchOutsideTx(1L);

        assertThat(result.aladinOutcome()).isEqualTo(BookEnrichmentTxService.Outcome.FAILED);
        assertThat(result.aladinFailReason()).contains("API Error");
    }

    @Test
    @DisplayName("applyInShortTx - Success")
    void applyInShortTx_success() {
        AladinApiResponse.Item item = new AladinApiResponse.Item();
        String longTitle = "New Title Is Longer Than Original";
        item.setTitle(longTitle);
        item.setPublisher("New Pub");
        item.setAuthor("Author (Writer)");
        item.setPubDate("2024-01-01");
        item.setPriceStandard(10000L);

        BookContentDto aiContent = mock(BookContentDto.class);

        BookEnrichmentTxService.FetchResult fetchResult = new BookEnrichmentTxService.FetchResult(
                1L, false,
                BookEnrichmentTxService.Outcome.DONE, null, item,
                BookEnrichmentTxService.Outcome.DONE, null, aiContent,
                "http://minio/new.jpg"
        );

        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));
        when(bookRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(testBook));

        when(publisherRepository.findByPublisherName("New Pub")).thenReturn(Optional.empty());
        Publisher newPub = Publisher.builder().publisherName("New Pub").build();
        when(publisherRepository.saveAndFlush(any())).thenReturn(newPub);

        when(contributorRepository.findByContributorName("Author")).thenReturn(Optional.empty());
        Contributor newContrib = Contributor.builder().contributorName("Author").build();
        when(contributorRepository.saveAndFlush(any())).thenReturn(newContrib);

        BookEnrichmentTxService.ApplyResult result = enrichmentTxService.applyInShortTx(fetchResult);

        assertThat(result.needReindex()).isTrue();
        assertThat(testTask.getAladinStatus()).isEqualTo(EnrichmentStatus.DONE);
        assertThat(testTask.getAiStatus()).isEqualTo(EnrichmentStatus.DONE);

        assertThat(testBook.getTitle()).isEqualTo("New Title Is Longer Than Original");
        assertThat(testBook.getPriceStandard()).isEqualTo(10000L);
        assertThat(testBook.getPublishDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(testBook.getThumbnail()).isEqualTo("http://minio/new.jpg");

        verify(bookPublisherRepository).saveAndFlush(any(BookPublisher.class));
        verify(bookContributorRepository).saveAndFlush(any(BookContributor.class));
        verify(categoryService).enrich(any(), any());
        verify(tagService).applyContent(any(), any());
    }

    @Test
    @DisplayName("applyInShortTx - Book Missing")
    void applyInShortTx_bookMissing() {
        BookEnrichmentTxService.FetchResult fetchResult = new BookEnrichmentTxService.FetchResult(
                1L, false, BookEnrichmentTxService.Outcome.DONE, null, null, null, null, null, null
        );

        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));
        when(bookRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.empty());

        enrichmentTxService.applyInShortTx(fetchResult);

        assertThat(testTask.getAladinStatus()).isEqualTo(EnrichmentStatus.FAILED);
        assertThat(testTask.getAiStatus()).isEqualTo(EnrichmentStatus.FAILED);
        verify(taskRepository).saveAndFlush(testTask);
    }

    @Test
    @DisplayName("applyInShortTx - Aladin Failed")
    void applyInShortTx_aladinFailed() {
        BookEnrichmentTxService.FetchResult fetchResult = new BookEnrichmentTxService.FetchResult(
                1L, false,
                BookEnrichmentTxService.Outcome.FAILED, "Network Error", null,
                BookEnrichmentTxService.Outcome.SKIPPED, null, null, null
        );

        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));
        when(bookRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(testBook));

        enrichmentTxService.applyInShortTx(fetchResult);

        assertThat(testTask.getAladinStatus()).isEqualTo(EnrichmentStatus.FAILED);
        assertThat(testTask.getAladinFailReason()).isEqualTo("Network Error");
        assertThat(testTask.getAiStatus()).isEqualTo(EnrichmentStatus.PENDING);
    }

    @Test
    @DisplayName("applyInShortTx - Thumbnail Update")
    void applyInShortTx_thumbnailUpdate() {
        testBook.setThumbnail("http://old.jpg");
        BookImage oldImg = BookImage.builder().book(testBook).imagePath("http://old.jpg").isThumbnail(true).build();
        testBook.getImages().add(oldImg);

        AladinApiResponse.Item item = new AladinApiResponse.Item();
        BookEnrichmentTxService.FetchResult fetchResult = new BookEnrichmentTxService.FetchResult(
                1L, false,
                BookEnrichmentTxService.Outcome.DONE, null, item,
                BookEnrichmentTxService.Outcome.SKIPPED, null, null,
                "http://new.jpg"
        );

        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));
        when(bookRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(testBook));

        BookEnrichmentTxService.ApplyResult result = enrichmentTxService.applyInShortTx(fetchResult);

        assertThat(result.usedNewThumbnail()).isTrue();
        assertThat(result.oldThumbnailToDelete()).isEqualTo("http://old.jpg");
        assertThat(testBook.getThumbnail()).isEqualTo("http://new.jpg");

        assertThat(testBook.getImages()).hasSize(1);
        BookImage newImg = testBook.getImages().iterator().next();
        assertThat(newImg.getImagePath()).isEqualTo("http://new.jpg");
        assertThat(newImg.isThumbnail()).isTrue();
    }
}