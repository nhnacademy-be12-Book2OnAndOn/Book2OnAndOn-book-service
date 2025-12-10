package org.nhnacademy.book2onandonbookservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiApiClient;
import org.nhnacademy.book2onandonbookservice.client.GroqApiClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.dto.api.BookContentDto;
import org.nhnacademy.book2onandonbookservice.entity.*;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.repository.*;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
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
    private final TagRepository tagRepository;
    private final BookTagRepository bookTagRepository;
    private final BookSearchIndexService bookSearchIndexService;

    private final GeminiApiClient geminiApiClient;
    private final AladinApiClient aladinApiClient;
    private final GroqApiClient groqApiClient;

    private final Map<String, Long> categoryIdCache = new ConcurrentHashMap<>();
    private static final Pattern CATEGORY_SPLIT_PATTERN = Pattern.compile("\\s*>\\s*");

    //  동기 방식, 트랜잭션 처리
    @Transactional
    public void enrichBookData(Long bookId) throws JsonProcessingException {

        Book book = bookRepository.findById(bookId).orElseThrow(()-> new NotFoundBookException(bookId));

        if(book.getStatus() == BookStatus.BOOK_DELETED) return;

        log.info("[보강 시작] 책 ID: {}, ISBN: {}", bookId, book.getIsbn());

        AladinApiResponse.Item aladinData = null;
        try {
            aladinData = fetchAladinData(book.getIsbn());
        } catch (Exception e) {
            log.error("알라딘 API 통신 오류 발생 (ID: {})", bookId);
            throw e; // 예외를 던져서 스케줄러가 'FAILED'로 기록하고 나중에 재시도하게 함
        }

        String descriptionForGemini = book.getDescription(); //우선 db에서 설명을 가져옴
        if(!StringUtils.hasText(descriptionForGemini) && aladinData != null && StringUtils.hasText(aladinData.getDescription())){ // db에서 설명이 없고 알라딘에 메모리 상으로 설명이 있다면 그거 가져다가 쓰는거임
            descriptionForGemini = aladinData.getDescription();
        }

        BookContentDto aiContent = null;
        try {
            aiContent = groqApiClient.extractContent(book.getTitle(), descriptionForGemini, book.getIsbn());
            log.info("[Groq 성공] 책 ID: {}", bookId);

        } catch (Exception e) {
            log.warn("Groq 호출 실패 (ID: {}) -> Gemini로 전환 시도. 이유: {}", bookId, e.getMessage());

            try {
                aiContent = geminiApiClient.extractContent(book.getTitle(), descriptionForGemini, book.getIsbn());
                log.info("[Gemini 성공(Backup)] 책 ID: {}", bookId);

            } catch (Exception geminiEx) {
                // Gemini까지 실패하면 진짜 실패 상황

                // 1. Gemini Limit 에러인지 확인 (자정 부활 로직용)
                String msg = geminiEx.getMessage();
                if (msg != null && (msg.contains("Limit") || msg.contains("Quota") || msg.contains("429"))) {
                    log.warn("Gemini API 제한 감지! (ID: {})", bookId);
                    throw geminiEx; // 상위로 던져서 스케줄러가 FAILED 처리 + 자정 부활
                }

                // 2. 그 외 일반 에러
                log.error("AI 보강 최종 실패 (Groq & Gemini) ID: {}", bookId);
                throw geminiEx; // 예외를 던져야 스케줄러가 retryCount를 증가시킴
            }
        }

        // 데이터 업데이트
        updateBookInTransaction(book, aladinData, aiContent);
    }

    private void updateBookInTransaction(Book book, AladinApiResponse.Item aladinData, BookContentDto geminiContent) {

        boolean hasGeminiData = geminiContent != null && !geminiContent.hasNoTags();

        if(aladinData==null && !hasGeminiData){
            if(book.getPriceStandard() > 0){
                return;
            }
            boolean isDeleted = processDeleteIfNoExternalData(book, book.getId(), aladinData);
            if(isDeleted){
                log.info("[보강 실패 -> 도서 상태 삭제로 변경] 책 ID: {}, 이유: 외부데이터 없음", book.getId());
                return;
            }
        }

        boolean isUpdated = false;

        isUpdated |= updateFromAladin(book, aladinData);

        if(geminiContent != null){
            isUpdated |= updateFromGemini(book, geminiContent);
        }

        if(isUpdated || hasGeminiData){
            bookRepository.save(book);
            bookSearchIndexService.index(book);
            log.info("[보강완료] 책 ID: {}", book.getId());
        }
    }

    // --- 아래는 헬퍼 메서드  ---
    private boolean processDeleteIfNoExternalData(Book book, Long bookId, AladinApiResponse.Item aladinData) {
        boolean hasExternalData = (aladinData != null);
        if (!hasExternalData) {
            book.setStatus(BookStatus.BOOK_DELETED);
            bookRepository.save(book);
            bookSearchIndexService.deleteIndex(bookId);
            return true;
        }
        return false;
    }

    private boolean updateFromGemini(Book book, BookContentDto geminiContent){
        boolean updated = false;

        if(!geminiContent.hasNoTags() && book.getBookTags().isEmpty()){
            saveTags(book,geminiContent.getTags());
            updated=true;
        }

        if(!geminiContent.hasNoChapter() && !StringUtils.hasText(book.getChapter())){
            book.setChapter(truncate(geminiContent.getChapter(), 5000));
            updated=true;
            log.info("[목차 추가] 책 ID: {}", book.getId());
        }

        return updated;
    }

    private boolean updateFromAladin(Book book, AladinApiResponse.Item aladinData) {
        if (aladinData == null) return false;
        boolean updated = false;

        if (book.getBookCategories().isEmpty() && StringUtils.hasText(aladinData.getCategoryName())) {
            saveCategories(book, aladinData.getCategoryName());
        }
        if ((book.getPriceStandard() == null || book.getPriceStandard() == 0) && aladinData.getPriceStandard() > 0) {
            long newStandardPrice = aladinData.getPriceStandard();
            book.setPriceStandard(newStandardPrice);
            if (book.getPriceSales() == null || book.getPriceSales() == 0) {
                long defaultDiscountPrice = (long) (newStandardPrice * 0.9);
                book.setPriceSales(defaultDiscountPrice);
            }
            updated = true;
        }
        if (book.getPublishDate() == null && StringUtils.hasText(aladinData.getPubDate())) {
            book.setPublishDate(parseDate(aladinData.getPubDate()));
            updated = true;
        }
        if (!StringUtils.hasText(book.getDescription()) && StringUtils.hasText(aladinData.getDescription())) {
            book.setDescription(aladinData.getDescription());
            updated = true;
        }
        if (book.getImages().isEmpty() && StringUtils.hasText(aladinData.getCover())) {
            BookImage newImage = BookImage.builder().book(book).imagePath(aladinData.getCover()).build();
            book.getImages().add(newImage);
            updated = true;
        }
        return updated;
    }


    private void saveTags(Book book, List<String> tagNames) {
        for (String tagName : tagNames) {
            if (!StringUtils.hasText(tagName)) continue;
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
                    bookTagRepository.save(BookTag.builder().pk(pk).book(book).tag(tag).build());
                }
            } catch (Exception e) {
                log.error("태그 저장 실패 (Book: {}. Tag:{}): {}", book.getId(), safeTagName, e.getMessage());
            }
        }
    }

    private AladinApiResponse.Item fetchAladinData(String isbn) throws JsonProcessingException {
        log.info("알라딘 API 호출시작, ISBN={}", isbn);
        //어차피 AladingApiClient에서 로그찍고 예외 던지도록 만들어서 여기서 굳이 try-catch로 감쌀필요 없을거 같아서 try-catch문 지움
        //service단에서는 그냥 그걸 받아서 스케줄러로 던지기만 하면됨
        return aladinApiClient.searchByIsbn(isbn);
    }

    private LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private void saveCategories(Book book, String categoryPath) {
        if (!StringUtils.hasText(categoryPath)) return;
        String[] parts = CATEGORY_SPLIT_PATTERN.split(categoryPath);
        Category parent = null;
        Category currentCategory = null;
        for (String part : parts) {
            String categoryName = part.trim();
            if (categoryName.isEmpty()) continue;
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
            Category newCategory = Category.builder().categoryName(name).parent(parent).build();
            Category saved = categoryRepository.save(newCategory);
            categoryIdCache.put(cacheKey, saved.getId());
            return saved;
        } catch (Exception e) {
            log.warn("카테고리 동시 생성 충돌, 재조회: {}", name);
            if (parent == null) return categoryRepository.findByCategoryNameAndParentIsNull(name).orElseThrow();
            else return categoryRepository.findByCategoryNameAndParent(name, parent).orElseThrow();
        }
    }

    private void linkBookToCategory(Book book, Category category) {
        try {
            if (!bookCategoryRepository.existsByBookAndCategory(book, category)) {
                bookCategoryRepository.save(BookCategory.builder().book(book).category(category).build());
            }
        } catch (Exception e) {
            log.warn("이미 연결된 카테고리: BookID={}, CategoryId={}", book.getId(), category.getId());
        }
    }

    private String truncate(String str, int len) {
        if (str == null) return null;
        return str.length() > len ? str.substring(0, len) : str;
    }
}