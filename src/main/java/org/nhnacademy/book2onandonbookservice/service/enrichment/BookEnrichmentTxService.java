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

    // -------------------------
    // 1) 외부 호출 단계 (트랜잭션/락 없음)
    // -------------------------
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FetchResult fetchOutsideTx(Long bookId) {
        BookEnrichmentTask task = taskRepository.findById(bookId).orElse(null);
        Book book = bookRepository.findById(bookId).orElse(null);

        // task가 없으면 스케줄러 대상이 아니지만, 방어적으로 SKIPPED 반환
        if (task == null) {
            return new FetchResult(bookId, false,
                    Outcome.SKIPPED, null, null,
                    Outcome.SKIPPED, null, null,
                    null);
        }

        if (book == null) {
            return new FetchResult(bookId, false,
                    Outcome.FAILED, "Book not found", null,
                    Outcome.FAILED, "Book not found", null,
                    null);
        }

        if (book.getStatus() == BookStatus.BOOK_DELETED) {
            return new FetchResult(bookId, true,
                    Outcome.SKIPPED, null, null,
                    Outcome.SKIPPED, null, null,
                    null);
        }

        Outcome aladinOutcome = Outcome.SKIPPED;
        String aladinFailReason = null;
        AladinApiResponse.Item aladinItem = null;
        String newThumbInternalUrl = null;

        Outcome aiOutcome = Outcome.SKIPPED;
        String aiFailReason = null;
        BookContentDto aiContent = null;

        // 알라딘 호출
        if (shouldProcessAladin(task)) {
            try {
                AladinApiResponse.Item item = aladinApiClient.searchByIsbn(book.getIsbn());
                if (item == null) {
                    aladinOutcome = Outcome.NOT_FOUND;
                } else {
                    aladinOutcome = Outcome.DONE;
                    aladinItem = item;

                    // 이미지 업로드는 트랜잭션 밖에서
                    if (StringUtils.hasText(item.getCover())) {
                        try {
                            newThumbInternalUrl = imageUploadService.uploadImageFromUrl(item.getCover());
                        } catch (Exception imgEx) {
                            log.warn("[이미지 업로드 실패] bookId={}, cover={}", bookId, item.getCover(), imgEx);
                        }
                    }
                }
            } catch (Exception e) {
                aladinOutcome = Outcome.FAILED;
                aladinFailReason = e.getMessage();
            }
        }

        // AI 호출(생성만)
        if (shouldProcessAi(task)) {
            try {
                String description = book.getDescription();
                aiContent = tagService.generateContent(book.getTitle(), description, book.getIsbn());
                aiOutcome = Outcome.DONE;
            } catch (Exception e) {
                aiOutcome = Outcome.FAILED;
                aiFailReason = e.getMessage();
            }
        }

        return new FetchResult(
                bookId,
                false,
                aladinOutcome, aladinFailReason, aladinItem,
                aiOutcome, aiFailReason, aiContent,
                newThumbInternalUrl
        );
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

        // 책이 없으면 무한 재시도 방지: 둘 다 실패로 종결
        if (book == null) {
            lockedTask.markAllFailedBecauseBookMissing();
            taskRepository.saveAndFlush(lockedTask);
            return new ApplyResult(false, false, null);
        }

        // 삭제 책이면 둘 다 DONE으로 종결(스케줄러에서 다시 안 잡히게)
        if (book.getStatus() == BookStatus.BOOK_DELETED || r.bookDeleted()) {
            lockedTask.markAllDoneBecauseBookDeleted();
            taskRepository.saveAndFlush(lockedTask);
            return new ApplyResult(false, false, null);
        }

        boolean needReindex = false;
        boolean usedNewThumb = false;
        String oldThumbnailToDelete = null;

        // --- 상태 업데이트(원자적) ---
        if (r.aladinOutcome() != null) {
            switch (r.aladinOutcome()) {
                case DONE -> lockedTask.markAladinDone();
                case NOT_FOUND -> lockedTask.markAladinNotFound();
                case FAILED -> lockedTask.markAladinFailed(r.aladinFailReason());
                case SKIPPED -> { /* no-op */ }
            }
        }

        if (r.aiOutcome() != null) {
            switch (r.aiOutcome()) {
                case DONE -> lockedTask.markAiDone();
                case FAILED -> lockedTask.markAiFailed(r.aiFailReason());
                case NOT_FOUND, SKIPPED -> { /* no-op */ }
            }
        }

        // --- 알라딘 결과 반영 ---
        if (r.aladinItem() != null && lockedTask.getAladinStatus() == EnrichmentStatus.DONE) {
            AladinApiResponse.Item item = r.aladinItem();

            categoryService.enrich(book, item);
            enrichBasicInfo(book, item);
            enrichPublisher(book, item.getPublisher());
            enrichContributors(book, item.getAuthor());

            needReindex = true;

            // 썸네일 DB 반영만(삭제는 커밋 후)
            if (StringUtils.hasText(r.newThumbnailInternalUrl())) {
                String before = book.getThumbnail();
                if (StringUtils.hasText(before) && !r.newThumbnailInternalUrl().equals(before)) {
                    oldThumbnailToDelete = before;
                }
                applyThumbnailNoIO(book, r.newThumbnailInternalUrl());
                usedNewThumb = true;
            }
        }

        // --- AI 결과 반영 ---
        if (r.aiContent() != null && lockedTask.getAiStatus() == EnrichmentStatus.DONE) {
            tagService.applyContent(book, r.aiContent());
            needReindex = true;
        }

        // task 저장(flush 시 book 변경도 함께 flush됨)
        taskRepository.saveAndFlush(lockedTask);

        return new ApplyResult(needReindex, usedNewThumb, oldThumbnailToDelete);
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
