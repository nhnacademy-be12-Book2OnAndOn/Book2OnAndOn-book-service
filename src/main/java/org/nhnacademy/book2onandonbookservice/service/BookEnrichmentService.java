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

    private final GeminiApiClient geminiApiClient;
    private final AladinApiClient aladinApiClient;
    private final GoogleBooksApiClient googleBooksApiClient;
    private final TransactionTemplate transactionTemplate;
    private final Map<String, Category> categoryCache = new ConcurrentHashMap<>();


    @Async("apiExecutor")
    public CompletableFuture<Void> enrichBookData(Long bookId) {

        try {
            TimeUnit.MILLISECONDS.sleep(500 + (long) (Math.random() * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null) {
            return CompletableFuture.completedFuture(null);
        }

        log.info("[보강 시작] 책 ID: {}, ISBN: {}", bookId, book.getIsbn());

        AladinApiResponse.Item aladinData = null;
        GoogleBooksApiResponse.VolumeInfo googleData = null;

        try {
            aladinData = fetchAladinData(book.getIsbn());
//            googleData = fetchGoogleData(book.getIsbn());
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", e.getMessage());
        }

        if (aladinData != null || googleData != null) {
            AladinApiResponse.Item finalAladinData = aladinData;
            GoogleBooksApiResponse.VolumeInfo finalGoogleData = googleData;

            // transactionTemplate.execute()를 쓰면 내부 호출 문제 없이 트랜잭션 적용됨
            transactionTemplate.execute(status -> {
                updateBookInTransaction(bookId, finalAladinData, finalGoogleData);
                return null;
            });
        }

        return CompletableFuture.completedFuture(null);

    }

    protected void updateBookInTransaction(Long bookId, AladinApiResponse.Item aladinData,
                                           GoogleBooksApiResponse.VolumeInfo googleData) {
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


        }

        if (googleData != null) {
            if (!StringUtils.hasText(book.getDescription()) && StringUtils.hasText(googleData.getDescription())) {
                if (StringUtils.hasText(googleData.getDescription())) {
                    isUpdated = true;
                }
            }

        }

        if (book.getBookTags().isEmpty() && StringUtils.hasText(book.getDescription())) {
            try {
                Thread.sleep(1600);
                List<String> tags = geminiApiClient.extractTags(book.getTitle(), book.getDescription());
                if (!tags.isEmpty()) {
                    saveTags(book, tags);
                    log.info("태그 생성 완료 ({}개): {}", tags.size(), tags);
                }
            } catch (Exception e) {
                log.error("태그 생성 중 오류: {}", e.getMessage());
            }

        }

        if (isUpdated) {
            bookRepository.save(book);
            log.info("[보강 완료] 책 ID: {}", bookId);
        }

    }

    private void saveTags(Book book, List<String> tagNames) {
        for (String tagName : tagNames) {
            if (tagName == null || tagName.isBlank()) {
                continue;
            }
            String safeTagName = tagName.trim();

            if (safeTagName.length() > 50) {
                safeTagName = safeTagName.substring(0, 50);
            }
            try {

                String finalTagName = safeTagName;
                Tag tag = tagRepository.findByTagName(finalTagName)
                        .orElseGet(() -> {
                            try {
                                return tagRepository.save(Tag.builder().tagName(finalTagName).build());
                            } catch (Exception e) {
                                return tagRepository.findByTagName(finalTagName).orElseThrow();
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

            if (categoryName.length() > 100) {
                categoryName = categoryName.substring(0, 100);
            }
            final String finalCategoryName = categoryName;
            if (currentPathKey.isEmpty()) {
                currentPathKey = categoryName;
            } else {
                currentPathKey = currentPathKey + ">" + categoryName;
            }

            Category cachedCategory = categoryCache.get(currentPathKey);

            if (cachedCategory != null) {
                parent = cachedCategory;
                continue;
            }

            synchronized (categoryCache) {
                cachedCategory = categoryCache.get(currentPathKey);
                if (cachedCategory != null) {
                    parent = cachedCategory;
                    continue;
                }
                try {
                    Category savedCategory;
                    if (parent == null) {
                        savedCategory = categoryRepository.findByCategoryNameAndParentIsNull(finalCategoryName)
                                .orElseGet(() ->
                                        categoryRepository.save(
                                                Category.builder().categoryName(finalCategoryName).build()));
                    } else {
                        Category finalParent = parent;
                        savedCategory = categoryRepository.findByCategoryNameAndParent(finalCategoryName, finalParent)
                                .orElseGet(() ->
                                        categoryRepository.save(
                                                Category.builder().categoryName(finalCategoryName).parent(finalParent)
                                                        .build()));
                    }

                    categoryCache.put(currentPathKey, savedCategory);
                    parent = savedCategory;
                } catch (Exception e) {
                    if (parent == null) {
                        parent = categoryRepository.findByCategoryNameAndParentIsNull(categoryName).orElse(null);
                    } else {
                        parent = categoryRepository.findByCategoryNameAndParent(categoryName, parent).orElse(null);
                    }

                    if (parent != null) {
                        categoryCache.put(currentPathKey, parent);
                    }
                }
                if (parent == null) {
                    log.error("카테고리 연결 실패: {}", currentPathKey);
                    return;
                }
            }

            if (parent != null) {
                try {
                    if (!bookCategoryRepository.existsByBookAndCategory(book, parent)) {
                        bookCategoryRepository.save(BookCategory.builder()
                                .book(book)
                                .category(parent)
                                .build());
                    }
                } catch (Exception e) {
                    log.error("Book-Category 연결 실패 (Book: {}, Category: {}): {}", book.getId(),
                            parent.getCategoryName(),
                            e.getMessage());
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