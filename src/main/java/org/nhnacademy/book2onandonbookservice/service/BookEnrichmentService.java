package org.nhnacademy.book2onandonbookservice.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiApiClient;
import org.nhnacademy.book2onandonbookservice.client.GoogleBooksApiClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Gemini 태그 생성 대기 중 인터럽트 발생");
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
        boolean hasExternalData = (aladinData != null) || (googleData != null);
        boolean isUpdated = false;
        if (!hasExternalData) {
            book.setStatus(BookStatus.BOOK_DELETED);
            bookRepository.save(book);
            bookSearchIndexService.deleteIndex(bookId);
            return;
        }
        if (aladinData != null) {

            if (book.getBookCategories().isEmpty() && StringUtils.hasText(aladinData.getCategoryName())) {
                log.info("카테고리 발견: {}", aladinData.getCategoryName());
                saveCategories(book, aladinData.getCategoryName());
            }

            if ((book.getPriceStandard() == null || book.getPriceStandard() == 0)
                    && aladinData.getPriceStandard() > 0) {
                long newStandardPrice = aladinData.getPriceStandard();
                book.setPriceStandard(newStandardPrice);
                if (book.getPriceSales() == null || book.getPriceSales() == 0) {
                    book.setPriceSales(newStandardPrice);
                }
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

        String[] parts = categoryPath.split("\\s*>\\s*");
        Category parent = null;
        Category currentCategory = null;

        for (String part : parts) {
            String categoryName = part.trim();
            if (categoryName.isEmpty()) {
                continue;
            }
            categoryName = truncate(categoryName, 100);

            currentCategory = findOrCreateCategory(categoryName, parent);

            parent = currentCategory;
        }

        if (currentCategory != null) {
            linkBookToCategory(book, currentCategory);
        }
    }

    private Category findOrCreateCategory(String name, Category parent) {
        String parentKey = (parent == null) ? "root" : String.valueOf(parent.getId());
        String cacheKey = parentKey + ":" + name;

        if (categoryIdCache.containsKey(cacheKey)) {
            Long cachedId = categoryIdCache.get(cacheKey);
            return categoryRepository.findById(cachedId).orElseGet(() -> {
                categoryIdCache.remove(cacheKey);
                return createCategorySafely(name, parent, cacheKey);
            });
        }

        Optional<Category> existing;
        if (parent == null) {
            existing = categoryRepository.findByCategoryNameAndParentIsNull(name);
        } else {
            existing = categoryRepository.findByCategoryNameAndParent(name, parent);
        }

        return existing.map(category -> {
            categoryIdCache.put(cacheKey, category.getId());
            return category;
        }).orElseGet(() -> createCategorySafely(name, parent, cacheKey));
    }

    private synchronized Category createCategorySafely(String name, Category parent, String cacheKey) {
        if (categoryIdCache.containsKey(cacheKey)) {
            return categoryRepository.findById(categoryIdCache.get(cacheKey)).orElseThrow();
        }

        Optional<Category> doubleCheck;
        if (parent == null) {
            doubleCheck = categoryRepository.findByCategoryNameAndParentIsNull(name);
        } else {
            doubleCheck = categoryRepository.findByCategoryNameAndParent(name, parent);
        }

        if (doubleCheck.isPresent()) {
            Category found = doubleCheck.get();
            categoryIdCache.put(cacheKey, found.getId());
            return found;
        }

        try {
            Category newCategory = Category.builder()
                    .categoryName(name)
                    .parent(parent)
                    .build();
            Category saved = categoryRepository.save(newCategory);
            categoryIdCache.put(cacheKey, saved.getId());
            return saved;
        } catch (Exception e) {
            log.warn("카테고리 동시 생성 충돌 발생, 재조회 시도: {}", name);
            if (parent == null) {
                return categoryRepository.findByCategoryNameAndParentIsNull(name).orElseThrow();
            } else {
                return categoryRepository.findByCategoryNameAndParent(name, parent).orElseThrow();
            }
        }
    }

    private void linkBookToCategory(Book book, Category category) {
        try {
            if (!bookCategoryRepository.existsByBookAndCategory(book, category)) {
                bookCategoryRepository.save(BookCategory.builder()
                        .book(book)
                        .category(category)
                        .build());
            }
        } catch (Exception e) {
            log.warn("이미 연결된 카테고리 입니다: BookID={}, CategoryId={}", book.getId(), category.getId());
        }
    }

    private String truncate(String str, int len) {
        if (str == null) {
            return null;
        }
        return str.length() > len ? str.substring(0, len) : str;
    }
}