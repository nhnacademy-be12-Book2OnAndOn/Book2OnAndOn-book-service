package org.nhnacademy.book2onandonbookservice.service.enrichment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.domain.ContributorRole;
import org.nhnacademy.book2onandonbookservice.domain.EnrichmentStatus;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookEnrichmentTask;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.repository.BookContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookEnrichmentTaskRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookPublisherRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.ContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookEnrichmentTxService {

    private final BookRepository bookRepository;
    private final BookEnrichmentTaskRepository taskRepository;

    private final AladinApiClient aladinApiClient;
    private final CategoryEnrichmentService categoryService;
    private final TagEnrichmentService tagService;
    private final TagGenerationService tagGenerationService;

    private final PublisherRepository publisherRepository;
    private final ContributorRepository contributorRepository;
    private final BookPublisherRepository bookPublisherRepository;
    private final BookContributorRepository bookContributorRepository;

    private final ImageUploadService imageUploadService;

    private static final Pattern CONTRIBUTOR_PATTERN = Pattern.compile("^([^(]*)\\s*\\(([^)]*)\\)$");
    private static final double DEFAULT_DISCOUNT_RATE = 0.1;

    public enum Outcome { DONE, FAILED, NOT_FOUND, SKIPPED }

    public record FetchResult(
            Long bookId,
            boolean bookDeleted,
            Outcome aladinOutcome,
            String aladinFailReason,
            AladinApiResponse.Item aladinItem,
            Outcome aiOutcome,
            String aiFailReason,
            BookContentDto aiContent,
            String newThumbnailInternalUrl
    ) {}

    public record ApplyResult(
            boolean needReindex,
            boolean usedNewThumbnail,
            String oldThumbnailToDelete
    ) {}

    // -------------------------
    // 1) 외부 호출 단계 (트랜잭션/락 없음)
    // -------------------------
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FetchResult fetchOutsideTx(Long bookId) {
        BookEnrichmentTask task = taskRepository.findById(bookId).orElse(null);
        Book book = bookRepository.findById(bookId).orElse(null);

        if (task == null) {
            return skippedResult(bookId);
        }

        if (book == null) {
            return failedResult(bookId, "Book not found");
        }

        if (book.getStatus() == BookStatus.BOOK_DELETED) {
            return deletedResult(bookId);
        }

        AladinFetchResult aladinRes = fetchAladinInfo(book, task);
        AiFetchResult aiRes = fetchAiInfo(book, task);

        return new FetchResult(
                bookId,
                false,
                aladinRes.outcome(), aladinRes.failReason(), aladinRes.item(),
                aiRes.outcome(), aiRes.failReason(), aiRes.content(),
                aladinRes.newThumbnailUrl()
        );
    }

    private record AladinFetchResult(Outcome outcome, String failReason, AladinApiResponse.Item item, String newThumbnailUrl) {}
    private record AiFetchResult(Outcome outcome, String failReason, BookContentDto content) {}

    private FetchResult skippedResult(Long bookId) {
        return new FetchResult(bookId, false, Outcome.SKIPPED, null, null, Outcome.SKIPPED, null, null, null);
    }

    private FetchResult failedResult(Long bookId, String reason) {
        return new FetchResult(bookId, false, Outcome.FAILED, reason, null, Outcome.FAILED, reason, null, null);
    }

    private FetchResult deletedResult(Long bookId) {
        return new FetchResult(bookId, true, Outcome.SKIPPED, null, null, Outcome.SKIPPED, null, null, null);
    }

    private AladinFetchResult fetchAladinInfo(Book book, BookEnrichmentTask task) {
        if (!shouldProcessAladin(task)) {
            return new AladinFetchResult(Outcome.SKIPPED, null, null, null);
        }

        try {
            AladinApiResponse.Item item = aladinApiClient.searchByIsbn(book.getIsbn());
            if (item == null) {
                return new AladinFetchResult(Outcome.NOT_FOUND, null, null, null);
            }

            String newThumbnailUrl = uploadThumbnail(book.getId(), item.getCover());
            return new AladinFetchResult(Outcome.DONE, null, item, newThumbnailUrl);
        } catch (Exception e) {
            return new AladinFetchResult(Outcome.FAILED, e.getMessage(), null, null);
        }
    }

    private String uploadThumbnail(Long bookId, String coverUrl) {
        if (!StringUtils.hasText(coverUrl)) {
            return null;
        }
        try {
            return imageUploadService.uploadImageFromUrl(coverUrl);
        } catch (Exception imgEx) {
            log.warn("[이미지 업로드 실패] bookId={}, cover={}", bookId, coverUrl, imgEx);
            return null;
        }
    }

    private AiFetchResult fetchAiInfo(Book book, BookEnrichmentTask task) {
        if (!shouldProcessAi(task)) {
            return new AiFetchResult(Outcome.SKIPPED, null, null);
        }

        try {
            BookContentDto content = tagGenerationService.generateContent(book.getTitle(), book.getDescription(), book.getIsbn());
            return new AiFetchResult(Outcome.DONE, null, content);
        } catch (Exception e) {
            return new AiFetchResult(Outcome.FAILED, e.getMessage(), null);
        }
    }

    // -------------------------
    // 2) DB 반영 단계 (짧은 트랜잭션 + row lock)
    // -------------------------
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApplyResult applyInShortTx(FetchResult r) {
        Long bookId = r.bookId();

        BookEnrichmentTask lockedTask = taskRepository.findByIdForUpdate(bookId).orElse(null);
        if (lockedTask == null) {
            return new ApplyResult(false, false, null);
        }

        Book book = bookRepository.findByIdWithRelationsForUpdate(bookId).orElse(null);
        if (isProcessingAborted(lockedTask, book, r)) {
            taskRepository.saveAndFlush(lockedTask);
            return new ApplyResult(false, false, null);
        }

        updateTaskStatuses(lockedTask, r);

        ApplyResult aladinRes = applyAladinResult(book, lockedTask, r);
        boolean aiApplied = applyAiResult(book, lockedTask, r);

        taskRepository.saveAndFlush(lockedTask);

        return new ApplyResult(
                aladinRes.needReindex() || aiApplied,
                aladinRes.usedNewThumbnail(),
                aladinRes.oldThumbnailToDelete()
        );
    }

    private boolean isProcessingAborted(BookEnrichmentTask lockedTask, Book book, FetchResult r) {
        if (book == null) {
            lockedTask.markAllFailedBecauseBookMissing();
            return true;
        }

        if (book.getStatus() == BookStatus.BOOK_DELETED || r.bookDeleted()) {
            lockedTask.markAllDoneBecauseBookDeleted();
            return true;
        }

        return false;
    }

    private void updateTaskStatuses(BookEnrichmentTask task, FetchResult r) {
        if (r.aladinOutcome() != null) {
            updateAladinStatus(task, r.aladinOutcome(), r.aladinFailReason());
        }

        if (r.aiOutcome() != null) {
            updateAiStatus(task, r.aiOutcome(), r.aiFailReason());
        }
    }

    private void updateAladinStatus(BookEnrichmentTask task, Outcome outcome, String failReason) {
        switch (outcome) {
            case DONE -> task.markAladinDone();
            case NOT_FOUND -> task.markAladinNotFound();
            case FAILED -> task.markAladinFailed(failReason);
            default -> { /* no-op */ }
        }
    }

    private void updateAiStatus(BookEnrichmentTask task, Outcome outcome, String failReason) {
        switch (outcome) {
            case DONE -> task.markAiDone();
            case FAILED -> task.markAiFailed(failReason);
            default -> { /* no-op */ }
        }
    }

    private ApplyResult applyAladinResult(Book book, BookEnrichmentTask task, FetchResult r) {
        if (r.aladinItem() == null || task.getAladinStatus() != EnrichmentStatus.DONE) {
            return new ApplyResult(false, false, null);
        }

        AladinApiResponse.Item item = r.aladinItem();
        categoryService.enrich(book, item);
        enrichBasicInfo(book, item);
        enrichPublisher(book, item.getPublisher());
        enrichContributors(book, item.getAuthor());

        boolean usedNewThumb = false;
        String oldThumbnailToDelete = null;

        if (StringUtils.hasText(r.newThumbnailInternalUrl())) {
            String before = book.getThumbnail();
            if (StringUtils.hasText(before) && !r.newThumbnailInternalUrl().equals(before)) {
                oldThumbnailToDelete = before;
            }
            applyThumbnailNoIO(book, r.newThumbnailInternalUrl());
            usedNewThumb = true;
        }

        return new ApplyResult(true, usedNewThumb, oldThumbnailToDelete);
    }

    private boolean applyAiResult(Book book, BookEnrichmentTask task, FetchResult r) {
        if (r.aiContent() != null && task.getAiStatus() == EnrichmentStatus.DONE) {
            tagService.applyContent(book, r.aiContent());
            return true;
        }
        return false;
    }

    // -------------------------
    // 내부 로직
    // -------------------------
    private boolean shouldProcessAladin(BookEnrichmentTask task) {
        if (task.getAladinStatus() == EnrichmentStatus.DONE || task.getAladinStatus() == EnrichmentStatus.NOT_FOUND) {
            return false;
        }
        return task.getAladinStatus() == EnrichmentStatus.PENDING
                || (task.getAladinStatus() == EnrichmentStatus.FAILED && task.getAladinRetryCount() < 3);
    }

    private boolean shouldProcessAi(BookEnrichmentTask task) {
        if (task.getAiStatus() == EnrichmentStatus.DONE) return false;
        return task.getAiStatus() == EnrichmentStatus.PENDING
                || (task.getAiStatus() == EnrichmentStatus.FAILED && task.getAiRetryCount() < 3);
    }

    private void enrichBasicInfo(Book book, AladinApiResponse.Item item) {
        if (!StringUtils.hasText(book.getTitle())
                || (item.getTitle() != null && book.getTitle().length() < item.getTitle().length())) {
            book.setTitle(item.getTitle());
        }

        if (!StringUtils.hasText(book.getDescription()) && StringUtils.hasText(item.getDescription())) {
            book.setDescription(item.getDescription());
        }

        if (item.getPriceStandard() != null) {
            long standardPrice = item.getPriceStandard();
            book.setPriceStandard(standardPrice);

            long salesPrice = (long) (standardPrice * (1 - DEFAULT_DISCOUNT_RATE));
            salesPrice = (salesPrice / 10) * 10;
            book.setPriceSales(salesPrice);
        }

        if (book.getPublishDate() == null && StringUtils.hasText(item.getPubDate())) {
            try {
                book.setPublishDate(LocalDate.parse(item.getPubDate(), DateTimeFormatter.ISO_DATE));
            } catch (Exception e) {
                log.warn("날짜 파싱 실패(BookId: {}): {}", book.getId(), item.getPubDate());
            }
        }
    }

    private void enrichPublisher(Book book, String publisherName) {
        if (!StringUtils.hasText(publisherName)) return;

        String cleanName = publisherName.trim();

        Publisher publisher = publisherRepository.findByPublisherName(cleanName)
                .orElseGet(() -> {
                    try {
                        return publisherRepository.saveAndFlush(Publisher.builder().publisherName(cleanName).build());
                    } catch (DataIntegrityViolationException e) {
                        return publisherRepository.findByPublisherName(cleanName)
                                .orElseThrow(() -> new RuntimeException("출판사 저장 및 재조회 실패: " + cleanName));
                    }
                });

        if (!bookPublisherRepository.existsByBookAndPublisher(book, publisher)) {
            try {
                BookPublisher bp = BookPublisher.builder().book(book).publisher(publisher).build();
                bookPublisherRepository.saveAndFlush(bp);
                book.getBookPublishers().add(bp);
            } catch (Exception e) {
                log.debug("[중복 방지] 출판사 관계 중복 (BookId: {}, Publisher: {})", book.getId(), cleanName);
            }
        }
    }

    private void enrichContributors(Book book, String authorString) {
        if (!StringUtils.hasText(authorString)) return;

        String[] authors = authorString.split(",");

        for (String raw : authors) {
            String token = raw.trim();
            if (token.isEmpty()) continue;

            String name;
            String role;

            Matcher matcher = CONTRIBUTOR_PATTERN.matcher(token);
            if (matcher.find()) {
                name = matcher.group(1).trim();
                role = ContributorRole.fromKorean(matcher.group(2).trim()).getCode();
            } else {
                name = token;
                role = ContributorRole.AUTHOR.getCode();
            }

            Contributor contributor = contributorRepository.findTopByContributorName(name)
                    .orElseGet(() -> {
                        try {
                            return contributorRepository.saveAndFlush(Contributor.builder().contributorName(name).build());
                        } catch (DataIntegrityViolationException e) {
                            return contributorRepository.findTopByContributorName(name)
                                    .orElseThrow(() -> new RuntimeException("작가 저장 및 재조회 실패: " + name));
                        }
                    });

            if (!bookContributorRepository.existsByBookAndContributorAndRoleType(book, contributor, role)) {
                try {
                    BookContributor bc = BookContributor.builder()
                            .book(book)
                            .contributor(contributor)
                            .roleType(role)
                            .build();
                    bookContributorRepository.saveAndFlush(bc);
                } catch (DataIntegrityViolationException e) {
                    log.debug("[중복 방지] 작가 관계 중복 (BookId: {}, Contributor: {})", book.getId(), name);
                }
            }
        }
    }

    /**
     * DB 반영만(외부 I/O 없음)
     */
    private void applyThumbnailNoIO(Book book, String newInternalUrl) {
        if (!StringUtils.hasText(newInternalUrl)) return;

        book.setThumbnail(newInternalUrl);

        if (book.getImages() == null) return;

        book.getImages().removeIf(BookImage::isThumbnail);

        BookImage newImg = BookImage.builder()
                .book(book)
                .imagePath(newInternalUrl)
                .isThumbnail(true)
                .build();
        book.getImages().add(newImg);
    }
}
