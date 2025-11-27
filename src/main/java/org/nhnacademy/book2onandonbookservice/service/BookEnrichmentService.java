package org.nhnacademy.book2onandonbookservice.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiApiClient;
import org.nhnacademy.book2onandonbookservice.client.GoogleBooksApiClient;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.dto.api.GoogleBooksApiResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookCategory;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.BookTagPK;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.repository.BookCategoryRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookTagRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookEnrichmentService {

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final BookCategoryRepository bookCategoryRepository;
    private final TagRepository tagRepository;
    private final BookTagRepository bookTagRepository;
    private final BookSearchIndexService bookSearchIndexService;

    private final GeminiApiClient geminiApiClient;
    private final AladinApiClient aladinApiClient;
    private final GoogleBooksApiClient googleBooksApiClient;
    private final TransactionTemplate transactionTemplate;
    private final Map<String, Long> categoryIdCache = new ConcurrentHashMap<>();


    @Async("apiExecutor")
    public CompletableFuture<Void> enrichBookData(Long bookId) {

        try {
            TimeUnit.MILLISECONDS.sleep(500 + (long) (Math.random() * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Book book = bookRepository.findById(bookId).orElse(null);
        if (!bookRepository.existsById(bookId)) {
            return CompletableFuture.completedFuture(null);
        }

        Book bookForIsbn = bookRepository.findById(bookId).orElse(null);
        if (bookForIsbn == null) {
            return CompletableFuture.completedFuture(null);
        }

        String isbn = bookForIsbn.getIsbn();
        String title = bookForIsbn.getTitle();
        String description = bookForIsbn.getDescription();

        log.info("[보강 시작] 책 ID: {}, ISBN: {}", bookId, book.getIsbn());

        AladinApiResponse.Item aladinData = null;
        GoogleBooksApiResponse.VolumeInfo googleData = null;

        try {
            aladinData = fetchAladinData(book.getIsbn());
//            googleData = fetchGoogleData(book.getIsbn());
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", e.getMessage());
        }

        List<String> generatedTags = null;
        if (StringUtils.hasText(description)) {
            try {
                Thread.sleep(3000);
                generatedTags = geminiApiClient.extractTags(title, description);
            } catch (Exception e) {
                log.warn("Gemini 태그 생성 실패: {}", e.getMessage());
            }
        }

        if (aladinData != null || googleData != null || (generatedTags != null && !generatedTags.isEmpty())) {
            AladinApiResponse.Item finalAladinData = aladinData;
            GoogleBooksApiResponse.VolumeInfo finalGoogleData = googleData;
            List<String> finalTags = generatedTags;

            // transactionTemplate.execute()를 쓰면 내부 호출 문제 없이 트랜잭션 적용됨
            transactionTemplate.execute(status -> {
                updateBookInTransaction(bookId, finalAladinData, finalGoogleData, finalTags);
                return null;
            });
        }

        return CompletableFuture.completedFuture(null);

    }

    protected void updateBookInTransaction(Long bookId, AladinApiResponse.Item aladinData,
                                           GoogleBooksApiResponse.VolumeInfo googleData, List<String> finalTags) {
        Book book = bookRepository.findById(bookId).orElseThrow();
        boolean isUpdated = false;

        if (aladinData != null) {

            if (book.getBookCategories().isEmpty() && StringUtils.hasText(aladinData.getCategoryName())) {
                log.info("카테고리 발견: {}", aladinData.getCategoryName());
                saveCategories(book, aladinData.getCategoryName());
            }

            if ((book.getPriceStandard() == null || book.getPriceStandard() == 0)
                    && aladinData.getPriceStandard() > 0) {
                book.setPriceStandard(aladinData.getPriceStandard());
                book.setPriceSales(aladinData.getPriceSales());
                isUpdated = true;
            }
            if (book.getPublishDate() == null && StringUtils.hasText(aladinData.getPubDate())) {
                book.setPublishDate(parseDate(aladinData.getPubDate()));
                isUpdated = true;
            }
            if (!StringUtils.hasText(book.getDescription()) && StringUtils.hasText(aladinData.getDescription())) {
                book.setDescription(aladinData.getDescription());
                isUpdated = true;
            }
            if (book.getImages().isEmpty() && StringUtils.hasText(aladinData.getCover())) {
                BookImage newImage = BookImage.builder()
                        .book(book)
                        .imagePath(aladinData.getCover())
                        .build();

                book.getImages().add(newImage);
                isUpdated = true;
            }

//            if (book.getChapter().isEmpty() && StringUtils.hasText(aladinData.getItemList())) {
//                book.setChapter(aladinData.getItemList());
//
//                log.info("챕터!!!!!!!:{}", book.getChapter());
//                isUpdated = true;
//            }

//            log.info("[디버깅] 책: ID: {}, subInfo 존재?: {}", bookId, (aladinData.getSubInfo() != null));
//
//            if (aladinData.getSubInfo() != null && aladinData.getSubInfo().getAuthors() != null
//                    && !aladinData.getSubInfo().getAuthors().isEmpty()) {
//
//                book.getBookContributors().clear();
//
//                for (AladinApiResponse.Author apiAuthor : aladinData.getSubInfo().getAuthors()) {
//                    String authorName = truncate(apiAuthor.getAuthorName(), 50);
//                    String role = truncate(apiAuthor.getAuthorTypeDesc(), 50);
//                    String description = truncate(apiAuthor.getAuthorInfo(), 10000);
//
//                    if (description == null) {
//                        log.warn("description is null!!!!!!!!!!!!!!!!");
//                    }
//
//                    if (!StringUtils.hasText(authorName)) {
//                        continue;
//                    }
//
//                    Contributor contributor = contributorRepository.findByContributorName(authorName)
//                            .orElseGet(() -> contributorRepository.save(
//                                    Contributor.builder().contributorName(authorName).build()
//                            ));
//
//                    if (StringUtils.hasText(description) && !StringUtils.hasText(
//                            contributor.getContributorDescription())) {
//                        contributor.setContributorDescription(description);
//
//                    }
//                    contributorRepository.save(contributor);
//
//                    BookContributor newRelation = BookContributor.builder()
//                            .book(book)
//                            .contributor(contributor)
//                            .roleType(role)
//                            .build();
//
//                    book.getBookContributors().add(newRelation);
//                }
//                log.info("작가 정보 갱신 완료 ({}명)", aladinData.getSubInfo().getAuthors().size());
//                isUpdated = true;
//            }

        }

        if (googleData != null) {
            if (!StringUtils.hasText(book.getDescription()) && StringUtils.hasText(googleData.getDescription())) {
                book.setDescription(googleData.getDescription());
                isUpdated = true;
            }
        }

        if (finalTags != null && !finalTags.isEmpty()) {
            if (book.getBookTags().isEmpty()) {
                saveTags(book, finalTags);
                log.info("태그 저장 완료 ({}개)", finalTags.size());
            }
        }

        if (isUpdated) {
            bookRepository.save(book);
            log.info("[보강 완료] 책 ID: {}", bookId);
            bookSearchIndexService.index(book);

        }

    }

    private void saveTags(Book book, List<String> tagNames) {
        for (String tagName : tagNames) {
            if (!StringUtils.hasText(tagName)) {
                continue;
            }

            String safeTagName = truncate(tagName.trim(), 50);

            try {

                Tag tag = tagRepository.findByTagName(safeTagName)
                        .orElseGet(() -> {
                            try {
                                return tagRepository.saveAndFlush(Tag.builder().tagName(safeTagName).build());
                            } catch (Exception e) {
                                return tagRepository.findByTagName(safeTagName).orElseThrow();
                            }
                        });

                if (!bookTagRepository.existsByBookAndTag(book, tag)) {
                    BookTagPK pk = new BookTagPK(book.getId(), tag.getId());
                    bookTagRepository.save(BookTag.builder()
                            .pk(pk)
                            .book(book)
                            .tag(tag)
                            .build());


                }
            } catch (Exception e) {
                log.error("태그 저장 실패 (Book: {}. Tag:{}): {}", book.getId(), safeTagName, e.getMessage());
            }
        }
    }

    private AladinApiResponse.Item fetchAladinData(String isbn) {
        try {
            return aladinApiClient.searchByIsbn(isbn);
        } catch (Exception e) {
            return null;
        }
    }

    private GoogleBooksApiResponse.VolumeInfo fetchGoogleData(String isbn) {
        try {
            return googleBooksApiClient.searchByIsbn(isbn);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private void saveCategories(Book book, String categoryPath) {
        if (!StringUtils.hasText(categoryPath)) {
            return;
        }

        String[] parts = categoryPath.split(">");
        Category parent = null;
        String currentPathKey = "";

        for (String part : parts) {
            String categoryName = part.trim();
            if (categoryName.isEmpty()) {
                continue;
            }
            categoryName = truncate(categoryName, 100);

            if (currentPathKey.isEmpty()) {
                currentPathKey = categoryName;
            } else {
                currentPathKey = currentPathKey + ">" + categoryName;
            }

            Category currentCategory = null;
            Long cachedId = categoryIdCache.get(currentPathKey);

            if (cachedId != null) {
                currentCategory = categoryRepository.findById(cachedId).orElse(null);
            }

            if (currentCategory == null) {
                synchronized (categoryIdCache) {
                    cachedId = categoryIdCache.get(currentPathKey);
                    if (cachedId != null) {
                        currentCategory = categoryRepository.findById(cachedId).orElse(null);
                    }

                    if (currentCategory == null) {
                        if (parent == null) {
                            currentCategory = categoryRepository.findByCategoryNameAndParentIsNull(categoryName)
                                    .orElse(null);
                        } else {
                            currentCategory = categoryRepository.findByCategoryNameAndParent(categoryName, parent)
                                    .orElse(null);
                        }
                    }

                    if (currentCategory == null) {
                        try {
                            Category newCategory = Category.builder()
                                    .categoryName(categoryName)
                                    .parent(parent)
                                    .build();

                            currentCategory = categoryRepository.saveAndFlush(newCategory);
                        } catch (DataIntegrityViolationException e) {
                            log.info("카테고리 중복 저장 방어: {}", currentPathKey);

                            if (parent == null) {
                                currentCategory = categoryRepository.findByCategoryNameAndParentIsNull(categoryName)
                                        .orElseThrow();
                            } else {
                                currentCategory = categoryRepository.findByCategoryNameAndParent(categoryName, parent)
                                        .orElseThrow();
                            }
                        }
                    }

                    if (currentCategory != null) {
                        categoryIdCache.put(currentPathKey, currentCategory.getId());
                    }
                }
            }
            parent = currentCategory;

            if (currentCategory != null) {
                try {
                    if (!bookCategoryRepository.existsByBookAndCategory(book, currentCategory)) {
                        bookCategoryRepository.save(BookCategory.builder()
                                .book(book).category(currentCategory).build());

                    }
                } catch (Exception e) {
                    // 무시

                    log.error("BookCategory 저장 실패");
                }
            }
        }
    }

    private String truncate(String str, int len) {
        if (str == null) {
            return null;
        }
        return str.length() > len ? str.substring(0, len) : str;
    }
}