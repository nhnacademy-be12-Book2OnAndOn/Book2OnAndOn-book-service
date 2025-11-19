package org.nhnacademy.book2onandonbookservice.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.client.GoogleBooksApiClient;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.dto.api.GoogleBooksApiResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookCategory;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.repository.BookCategoryRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
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
    private final AladinApiClient aladinApiClient;
    private final GoogleBooksApiClient googleBooksApiClient;
    private final TransactionTemplate transactionTemplate;


    @Async("apiExecutor")
    public CompletableFuture<Void> enrichBookData(Long bookId) {

        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null) {
            return CompletableFuture.completedFuture(null);
        }

        log.info("[보강 시작] 책 ID: {}, ISBN: {}", bookId, book.getIsbn());

        AladinApiResponse.Item aladinData = null;
        GoogleBooksApiResponse.VolumeInfo googleData = null;

        try {
            aladinData = fetchAladinData(book.getIsbn());
            googleData = fetchGoogleData(book.getIsbn());
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
            if (!StringUtils.hasText(book.getChapter())) {
                if (StringUtils.hasText(googleData.getInfoLink())) {
                    book.setChapter(googleData.getInfoLink());
                    isUpdated = true;
                }
            }
        }

        if (isUpdated) {
            bookRepository.save(book);
            log.info("[보강 완료] 책 ID: {}", bookId);
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
        String[] parts = categoryPath.split(">");
        Category parent = null;

        for (String part : parts) {
            String categoryName = part.trim();
            if (categoryName.isEmpty()) {
                continue;
            }

            Category finalParent = parent;
            try {
                Category currentCategory = categoryRepository.findByCategoryNameAndParent(categoryName, finalParent)
                        .orElseGet(() -> categoryRepository.save(Category.builder()
                                .categoryName(truncate(categoryName, 20))
                                .parent(finalParent)
                                .build()));
                parent = currentCategory;
            } catch (Exception e) {
                parent = categoryRepository.findByCategoryNameAndParent(categoryName, finalParent).orElse(null);
            }
        }

        if (parent != null) {
            if (!bookCategoryRepository.existsByBookAndCategory(book, parent)) {
                bookCategoryRepository.save(BookCategory.builder()
                        .book(book)
                        .category(parent)
                        .build());
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