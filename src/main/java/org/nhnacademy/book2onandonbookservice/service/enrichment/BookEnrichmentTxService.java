package org.nhnacademy.book2onandonbookservice.service.enrichment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
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

    private record AladinStepResult(
            Outcome outcome,
            String failReason,
            AladinApiResponse.Item item,
            String newThumbnailInternalUrl
    ) {}

    private record AiStepResult(
            Outcome outcome,
            String failReason,
            BookContentDto content
    ) {}

    // [Refactoring] 데이터 반영 결과를 반환하기 위한 내부 레코드
    private record EnrichmentChangeLog(
            boolean needReindex,
            boolean usedNewThumbnail,
            String oldThumbnailToDelete
    ) {
        static EnrichmentChangeLog empty() {
            return new EnrichmentChangeLog(false, false, null);
        }
    }

    // -------------------------
    // 1) 외부 호출 단계 (트랜잭션/락 없음)
    // -------------------------
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FetchResult fetchOutsideTx(Long bookId) {
        BookEnrichmentTask task = taskRepository.findById(bookId).orElse(null);
        Book book = bookRepository.findById(bookId).orElse(null);

        // 1. Task 없음 (방어 로직)
        if (task == null) {
            return createSkippedResult(bookId);
        }

        // 2. Book 없음
        if (book == null) {
            return createFailedResult(bookId, "Book not found");
        }

        // 3. 삭제된 도서
        if (book.getStatus() == BookStatus.BOOK_DELETED) {
            return createDeletedResult(bookId);
        }

        // 4. 알라딘 로직 실행
        AladinStepResult aladinResult = processAladinStep(task, book);

        // 5. AI 로직 실행
        AiStepResult aiResult = processAiStep(task, book);

        // 6. 최종 결과 조립 ([Refactoring] 파라미터 개수 감소)
        return createResult(bookId, false, aladinResult, aiResult);
    }

    private FetchResult createResult(Long bookId, boolean bookDeleted, AladinStepResult aladin, AiStepResult ai) {
        return new FetchResult(
                bookId,
                bookDeleted,
                aladin.outcome(), aladin.failReason(), aladin.item(),
                ai.outcome(), ai.failReason(), ai.content(),
                aladin.newThumbnailInternalUrl()
        );
    }

    // 편의 메서드들
    private FetchResult createSkippedResult(Long bookId) {
        return new FetchResult(bookId, false, Outcome.SKIPPED, null, null, Outcome.SKIPPED, null, null, null);
    }

    private FetchResult createFailedResult(Long bookId, String reason) {
        return new FetchResult(bookId, false, Outcome.FAILED, reason, null, Outcome.FAILED, reason, null, null);
    }

    private FetchResult createDeletedResult(Long bookId) {
        return new FetchResult(bookId, true, Outcome.SKIPPED, null, null, Outcome.SKIPPED, null, null, null);
    }


    private AladinStepResult processAladinStep(BookEnrichmentTask task, Book book) {
        if (!shouldProcessAladin(task)) {
            return new AladinStepResult(Outcome.SKIPPED, null, null, null);
        }

        try {
            AladinApiResponse.Item item = aladinApiClient.searchByIsbn(book.getIsbn());
            if (item == null) {
                return new AladinStepResult(Outcome.NOT_FOUND, null, null, null);
            }

            String newThumbUrl = uploadThumbnailSafe(book.getId(), item.getCover());
            return new AladinStepResult(Outcome.DONE, null, item, newThumbUrl);
        } catch (Exception e) {
            return new AladinStepResult(Outcome.FAILED, e.getMessage(), null, null);
        }
    }

    private String uploadThumbnailSafe(Long bookId, String coverUrl) {
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

    private AiStepResult processAiStep(BookEnrichmentTask task, Book book) {
        if (!shouldProcessAi(task)) {
            return new AiStepResult(Outcome.SKIPPED, null, null);
        }

        try {
            BookContentDto aiContent = tagService.generateContent(book.getTitle(), book.getDescription(), book.getIsbn());
            return new AiStepResult(Outcome.DONE, null, aiContent);
        } catch (Exception e) {
            return new AiStepResult(Outcome.FAILED, e.getMessage(), null);
        }
    }

    // -------------------------
    // 2) DB 반영 단계 (짧은 트랜잭션 + row lock)
    // -------------------------
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApplyResult applyInShortTx(FetchResult r) {
        // [Refactoring] Cognitive Complexity Issue 해결 (20 -> 낮음)
        // 로직을 분리하여 메인 메서드의 흐름을 단순화함

        Long bookId = r.bookId();
        BookEnrichmentTask lockedTask = taskRepository.findByIdForUpdate(bookId).orElse(null);
        if (lockedTask == null) {
            return new ApplyResult(false, false, null);
        }

        Book book = bookRepository.findByIdWithRelationsForUpdate(bookId).orElse(null);

        // 예외 상황 처리 (책 없음 or 삭제됨)
        if (isInvalidBookState(book, r, lockedTask)) {
            taskRepository.saveAndFlush(lockedTask);
            return new ApplyResult(false, false, null);
        }

        // 1. 상태 업데이트 (Switch 문 추출)
        updateTaskStatuses(lockedTask, r);

        // 2. 알라딘 데이터 반영 (복잡한 if/로직 추출)
        EnrichmentChangeLog aladinChange = applyAladinData(book, lockedTask, r);

        // 3. AI 데이터 반영 (if 추출)
        boolean aiReindexed = applyAiData(book, lockedTask, r);

        // task 저장(flush 시 book 변경도 함께 flush됨)
        taskRepository.saveAndFlush(lockedTask);

        boolean finalReindex = aladinChange.needReindex() || aiReindexed;
        return new ApplyResult(finalReindex, aladinChange.usedNewThumbnail(), aladinChange.oldThumbnailToDelete());
    }

    // --- [Refactoring] applyInShortTx 내부 로직 분리 ---

    private boolean isInvalidBookState(Book book, FetchResult r, BookEnrichmentTask task) {
        if (book == null) {
            task.markAllFailedBecauseBookMissing();
            return true;
        }
        if (book.getStatus() == BookStatus.BOOK_DELETED || r.bookDeleted()) {
            task.markAllDoneBecauseBookDeleted();
            return true;
        }
        return false;
    }

    private void updateTaskStatuses(BookEnrichmentTask task, FetchResult r) {
        if (r.aladinOutcome() != null) {
            switch (r.aladinOutcome()) {
                case DONE -> task.markAladinDone();
                case NOT_FOUND -> task.markAladinNotFound();
                case FAILED -> task.markAladinFailed(r.aladinFailReason());
                case SKIPPED -> { /* no-op */ }
            }
        }

        if (r.aiOutcome() != null) {
            switch (r.aiOutcome()) {
                case DONE -> task.markAiDone();
                case FAILED -> task.markAiFailed(r.aiFailReason());
                case NOT_FOUND, SKIPPED -> { /* no-op */ }
            }
        }
    }

    private EnrichmentChangeLog applyAladinData(Book book, BookEnrichmentTask task, FetchResult r) {
        if (r.aladinItem() == null || task.getAladinStatus() != EnrichmentStatus.DONE) {
            return EnrichmentChangeLog.empty();
        }

        AladinApiResponse.Item item = r.aladinItem();

        categoryService.enrich(book, item);
        enrichBasicInfo(book, item);
        enrichPublisher(book, item.getPublisher());
        enrichContributors(book, item.getAuthor());

        // 썸네일 처리
        String oldThumbnailToDelete = null;
        boolean usedNewThumb = false;

        if (StringUtils.hasText(r.newThumbnailInternalUrl())) {
            String before = book.getThumbnail();
            if (StringUtils.hasText(before) && !r.newThumbnailInternalUrl().equals(before)) {
                oldThumbnailToDelete = before;
            }
            applyThumbnailNoIO(book, r.newThumbnailInternalUrl());
            usedNewThumb = true;
        }

        return new EnrichmentChangeLog(true, usedNewThumb, oldThumbnailToDelete);
    }

    private boolean applyAiData(Book book, BookEnrichmentTask task, FetchResult r) {
        if (r.aiContent() != null && task.getAiStatus() == EnrichmentStatus.DONE) {
            tagService.applyContent(book, r.aiContent());
            return true;
        }
        return false;
    }

    // -------------------------
    // 내부 로직 (기존 유지)
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
                role = matcher.group(2).trim();
            } else {
                name = token;
                role = "지은이";
            }

            Contributor contributor = contributorRepository.findByContributorName(name)
                    .orElseGet(() -> {
                        try {
                            return contributorRepository.saveAndFlush(Contributor.builder().contributorName(name).build());
                        } catch (DataIntegrityViolationException e) {
                            return contributorRepository.findByContributorName(name)
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
                } catch (Exception e) {
                    log.debug("[중복 방지] 작가 관계 중복 (BookId: {}, Contributor: {})", book.getId(), name);
                }
            }
        }
    }

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