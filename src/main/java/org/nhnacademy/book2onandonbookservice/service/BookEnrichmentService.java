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
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.repository.BookCategoryRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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


    @Async("apiExecutor")
    @Transactional
    public CompletableFuture<Void> enrichBookData(Long bookId) {
        return CompletableFuture.runAsync(() -> {
            Book book = bookRepository.findById(bookId).orElse(null);
            if (book == null) {
                return;
            }

            try {
                boolean isUpdated = false;

                AladinApiResponse.Item aladinData = fetchAladinData(book.getIsbn());

                if (aladinData != null) {
                    if (book.getBookCategories().isEmpty() && StringUtils.hasText(aladinData.getCategoryName())) {
                        saveCategories(book, aladinData.getCategoryName());
                    }

                    if ((book.getPriceStandard() == null || book.getPriceStandard() <= 0)
                            && aladinData.getPriceStandard() > 0) {
                        book.setPriceStandard(aladinData.getPriceStandard());
                        book.setPriceSales(aladinData.getPriceSales());
                        isUpdated = true;
                    }

                    if (book.getPublishDate() == null && StringUtils.hasText(aladinData.getPubDate())) {
                        book.setPublishDate(parseDate(aladinData.getPubDate()));
                        isUpdated = true;
                    }

                    if (!StringUtils.hasText(book.getDescription()) && StringUtils.hasText(
                            aladinData.getDescription())) {
                        book.setDescription(aladinData.getDescription());
                        isUpdated = true;
                    }
                }

                GoogleBooksApiResponse.VolumeInfo googleData = fetchGoogleData(book.getIsbn());

                if (googleData != null) {
                    if (!StringUtils.hasText(book.getChapter())) {
                        if (StringUtils.hasText(googleData.getInfoLink())) {
                            book.setChapter(googleData.getInfoLink());
                            isUpdated = true;
                        } else if (StringUtils.hasText(googleData.getDescription())) {
                            String desc = googleData.getDescription();
                            if (desc.length() > 1000) {
                                desc = desc.substring(0, 1000);
                            }
                            book.setChapter(desc);
                            isUpdated = true;
                        }
                    }

                    if (!StringUtils.hasText(book.getDescription()) && StringUtils.hasText(
                            googleData.getDescription())) {
                        book.setDescription(googleData.getDescription());
                        isUpdated = true;
                    }
                }

                if (isUpdated) {
                    bookRepository.save(book);
                }

            } catch (Exception e) {
                log.error("보강 실패 (ID: {}): {}", bookId, e.getMessage());
            }
        });
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
            Category currentCategory = categoryRepository.findByCategoryNameAndParent(categoryName, finalParent)
                    .orElseGet(() -> categoryRepository.save(Category.builder()
                            .categoryName(truncate(categoryName, 20))
                            .parent(finalParent)
                            .build()));

            parent = currentCategory;
        }

        if (parent != null) {
            boolean alreadyLinked = bookCategoryRepository.existsByBookAndCategory(book, parent);
            if (!alreadyLinked) {
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