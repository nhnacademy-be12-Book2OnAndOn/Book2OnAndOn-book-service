package org.nhnacademy.book2onandonbookservice.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiApiClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
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
    private final TransactionTemplate transactionTemplate;
    private final Map<String, Long> categoryIdCache = new ConcurrentHashMap<>();

    private static final Pattern CATEGORY_SPLIT_PATTERN = Pattern.compile("\\s*>\\s*");

    @Async("apiExecutor")
    public CompletableFuture<Void> enrichBookData(Long bookId) {
        try {
            long jitter = 500L + ThreadLocalRandom.current().nextInt(1000);
            TimeUnit.MILLISECONDS.sleep(jitter);
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

        String title = bookForIsbn.getTitle();
        String description = bookForIsbn.getDescription();

        log.info("[보강 시작] 책 ID: {}, ISBN: {}", bookId, book.getIsbn());

        AladinApiResponse.Item aladinData = null;

        try {
            aladinData = fetchAladinData(book.getIsbn());
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

        if (aladinData != null || (generatedTags != null && !generatedTags.isEmpty())) {
            AladinApiResponse.Item finalAladinData = aladinData;
            List<String> finalTags = generatedTags;

            // transactionTemplate.execute()를 쓰면 내부 호출 문제 없이 트랜잭션 적용됨
            transactionTemplate.execute(status -> {
                updateBookInTransaction(bookId, finalAladinData, finalTags);
                return null;
            });
        }

        return CompletableFuture.completedFuture(null);

    }

    protected void updateBookInTransaction(Long bookId, AladinApiResponse.Item aladinData,
                                           List<String> finalTags) {

        Book book = bookRepository.findById(bookId).orElseThrow();

        // 1. 외부 데이터가 아예 없는 경우 삭제 처리 (처리했다면 종료)
        if (processDeleteIfNoExternalData(book, bookId, aladinData)) {
            return;
        }

        // 2. 데이터 업데이트 진행 (Aladin)
        boolean isUpdated = false;
        isUpdated |= updateFromAladin(book, aladinData);

        // 3. 태그 저장 (태그 저장은 isUpdated 플래그와 별개로 동작하던 기존 로직 유지)
        updateTags(book, finalTags);

        // 4. 변경사항이 있으면 저장 및 인덱싱
        if (isUpdated) {
            bookRepository.save(book);
            log.info("[보강 완료] 책 ID: {}", bookId);
            bookSearchIndexService.index(book);
        }
    }

// --- 아래는 추출한 헬퍼 메서드들입니다 ---

    // 외부 데이터가 없으면 책을 삭제 처리하고 true 반환, 데이터가 있으면 false 반환
    private boolean processDeleteIfNoExternalData(Book book, Long bookId,
                                                  AladinApiResponse.Item aladinData
    ) {
        boolean hasExternalData = (aladinData != null);

        if (!hasExternalData) {
            book.setStatus(BookStatus.BOOK_DELETED);
            bookRepository.save(book);
            bookSearchIndexService.deleteIndex(bookId);
            return true;
        }
        return false;
    }

    // 알라딘 데이터로 책 정보 업데이트
    private boolean updateFromAladin(Book book, AladinApiResponse.Item aladinData) {
        if (aladinData == null) {
            return false;
        }

        boolean updated = false;

        // 카테고리
        if (book.getBookCategories().isEmpty() && StringUtils.hasText(aladinData.getCategoryName())) {
            log.info("카테고리 발견: {}", aladinData.getCategoryName());
            saveCategories(book, aladinData.getCategoryName());
        }

        // 가격
        if ((book.getPriceStandard() == null || book.getPriceStandard() == 0) && aladinData.getPriceStandard() > 0) {
            long newStandardPrice = aladinData.getPriceStandard();
            book.setPriceStandard(newStandardPrice);
            if (book.getPriceSales() == null || book.getPriceSales() == 0) {
                book.setPriceSales(newStandardPrice);
            }
            updated = true;
        }

        // 출판일
        if (book.getPublishDate() == null && StringUtils.hasText(aladinData.getPubDate())) {
            book.setPublishDate(parseDate(aladinData.getPubDate()));
            updated = true;
        }

        // 설명
        if (!StringUtils.hasText(book.getDescription()) && StringUtils.hasText(aladinData.getDescription())) {
            book.setDescription(aladinData.getDescription());
            updated = true;
        }

        // 이미지
        if (book.getImages().isEmpty() && StringUtils.hasText(aladinData.getCover())) {
            BookImage newImage = BookImage.builder()
                    .book(book)
                    .imagePath(aladinData.getCover())
                    .build();
            book.getImages().add(newImage);
            updated = true;
        }

        return updated;
    }


    private void updateTags(Book book, List<String> finalTags) {
        if (finalTags != null && !finalTags.isEmpty() && book.getBookTags().isEmpty()) {

            saveTags(book, finalTags);
            log.info("태그 저장 완료 ({}개)", finalTags.size());

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

        String[] parts = CATEGORY_SPLIT_PATTERN.split(categoryPath);
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