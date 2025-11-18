package org.nhnacademy.book2onandonbookservice.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiApiClient;
import org.nhnacademy.book2onandonbookservice.client.GoogleBooksApiClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.DataParserDto;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.dto.api.GeminiBookInfo;
import org.nhnacademy.book2onandonbookservice.dto.api.GoogleBooksApiResponse;
import org.nhnacademy.book2onandonbookservice.entity.Author;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookAuthor;
import org.nhnacademy.book2onandonbookservice.entity.BookCategory;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.BookTranslator;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.entity.Translator;
import org.nhnacademy.book2onandonbookservice.exception.DataParserException;
import org.nhnacademy.book2onandonbookservice.parser.DataParser;
import org.nhnacademy.book2onandonbookservice.parser.DataParserResolver;
import org.nhnacademy.book2onandonbookservice.repository.AuthorRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
import org.nhnacademy.book2onandonbookservice.repository.TranslatorRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final DataParserResolver parserResolver;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private final BookRepository bookRepository;
    private final PublisherRepository publisherRepository;
    private final AuthorRepository authorRepository;
    private final TranslatorRepository translatorRepository;

    private final CategoryRepository categoryRepository;

    private final Map<String, Publisher> publisherCache = new HashMap<>();
    private final Map<String, Author> authorCache = new HashMap<>();
    private final Map<String, Translator> translatorCache = new HashMap<>();
    private final Map<String, Category> categoryCache = new HashMap<>();


    private final GoogleBooksApiClient googleBooksApiClient;
    private final AladinApiClient aladinApiClient;
    private final GeminiApiClient geminiApiClient;

    private final ObjectMapper objectMapper;


    private static final DateTimeFormatter ALADIN_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int limit = 500;
        int currentCount = 0;
        if (bookRepository.count() > 0) {
            log.info("데이터가 이미 초기화되어 있으므로, CSV 파일 로딩을 건너뜁니다.");
            return;
        }

        log.info("데이터 초기화 시작");

        Resource[] resources = resolver.getResources("classpath:/data/*.*");

        if (resources.length == 0) {
            log.warn("classpath:/data 폴더에서 파일을 찾을 수 없습니다.");
            return;
        }

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }

            try {
                log.info("파일 처리 중: {}", filename);
                DataParser parser = parserResolver.getDataParser(filename);
                List<DataParserDto> dtoList = (List<DataParserDto>) parser.parsing(resource.getFile());

                int processedCount = 0;

                for (DataParserDto dto : dtoList) {
                    if (currentCount >= limit) {
                        break;//한줄 씩읽으면서 책 한개에 대해 데이터 처리
                    }
                    processSingleBook(dto);
                    processedCount++;
                    currentCount++;

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                log.info("총 {}개의 레코드를 처리했습니다", processedCount);
            } catch (DataParserException | IOException | ClassCastException e) {
                log.error("파일 처리 실패: {}, (원인: {})", filename, e.getMessage(), e.getCause());
            }
        }

        log.info("데이터 초기화 완료");
    }

    /**
     * csv 한줄 씩 읽으면서 1개의 책 처리
     *
     * @param dto
     */
    @Transactional
    public void processSingleBook(DataParserDto dto) {
        if (bookRepository.existsByIsbn(dto.getIsbn())) {
            log.warn("이미 존재하는 ISBN입니다. (ISBN: {}), 저장 건너뜀", dto.getIsbn());
            return;
        }

        GoogleBooksApiResponse.VolumeInfo googleData = googleBooksApiClient.searchByIsbn(dto.getIsbn());
        AladinApiResponse.Item aladinData = aladinApiClient.searchByIsbn(dto.getIsbn());

        //책 설명
        String finalDescription =
                (googleData != null && googleData.getDescription() != null) ? googleData.getDescription()
                        : (aladinData != null && aladinData.getDescription() != null) ? aladinData.getDescription()
                                : dto.getDescription();

        //목차
        String finalChapter =
                (googleData != null && googleData.getInfoLink() != null) ? googleData.getInfoLink()
                        : null; //google 밖에 없음

        //정가
        Long finalPrice = dto.getListPrice();
        if (finalPrice <= 0 && aladinData != null && aladinData.getPriceStandard() > 0) {
            finalPrice = aladinData.getPriceStandard();
        }

        //할인가
        Long finalSalePrice = dto.getSalePrice();
        if (finalSalePrice <= 0 && aladinData != null && aladinData.getPriceSales() > 0) {
            finalSalePrice = aladinData.getPriceSales();
        }

        // 만약 할인가가 정가보다 높다면 정가로 맞춤
        if (finalSalePrice > finalPrice) {
            finalSalePrice = finalPrice;
        }

        //출판일
        LocalDate finalPublishDate = dto.getPublishedAt();
        if (finalPublishDate == null && aladinData != null && aladinData.getPubDate() != null) {
            finalPublishDate = parseAladinDate(aladinData.getPubDate());
        }

        //출판사
        String finalPublisherName = dto.getPublisherName();
        if (isNullOrEmpty(finalPublisherName) && aladinData != null && !isNullOrEmpty(aladinData.getPublisher())) {
            finalPublisherName = aladinData.getPublisher();
        }

        //(저자/역자)
        List<String> finalAuthors = dto.getAuthors();
        List<String> finalTranslators = dto.getTranslators();

        if ((finalAuthors.isEmpty() && finalTranslators.isEmpty()) && aladinData != null && !isNullOrEmpty(
                aladinData.getAuthor())) {
            Map<String, List<String>> parsedAuthors = parseAladinAuthors(aladinData.getAuthor());
            finalAuthors = parsedAuthors.getOrDefault("authors", Collections.emptyList());
            finalTranslators = parsedAuthors.getOrDefault("translators", Collections.emptyList());
        }

        boolean isMissingData = isNullOrEmpty(finalPublisherName) || finalPublishDate == null || finalPrice <= 0
                || finalAuthors.isEmpty();
        if (isMissingData) {
            GeminiBookInfo geminiBookInfo = fillMissingDataWithGemini(dto.getIsbn(), dto.getTitle());
            if (geminiBookInfo != null) {
                if (isNullOrEmpty(finalPublisherName)) {
                    finalPublisherName = geminiBookInfo.getPublisher();
                }
                if (finalPublishDate == null) {
                    finalPublishDate = parseGenericDate(geminiBookInfo.getPublishDate());
                }
                if (finalPrice <= 0 && geminiBookInfo.getPriceStandard() != null) {
                    finalPrice = geminiBookInfo.getPriceStandard();
                }
                if (finalSalePrice <= 0) {
                    finalSalePrice = finalPrice;
                }

                if (finalAuthors.isEmpty() && !isNullOrEmpty(geminiBookInfo.getAuthor())) {
                    Map<String, List<String>> parsed = parseAladinAuthors(geminiBookInfo.getAuthor());
                    finalAuthors = parsed.getOrDefault("authors", Collections.emptyList());
                }
            }
        }
        //카테고리
        String categoryString =
                (googleData != null && googleData.getCategories() != null && !googleData.getCategories().isEmpty())
                        ? googleData.getCategories().get(0)
                        : (aladinData != null && !isNullOrEmpty(aladinData.getCategoryName()))
                                ? aladinData.getCategoryName().replace(">", " / ")
                                : null;
        //이미지
        String imageUrl =
                (googleData != null && googleData.getImageLinks() != null
                        && googleData.getImageLinks().getThumbnail() != null)
                        ? googleData.getImageLinks().getThumbnail().replace("http://", "https://")
                        : (aladinData != null && !isNullOrEmpty(aladinData.getCover()))
                                ? aladinData.getCover().replace("http://", "https://") : dto.getImageUrl();

        //유효성 검증
        if (isNullOrEmpty(finalPublisherName)) {
            log.warn("ISBN: {}: 최종 출판사 정보가 없어 스킵힙니다.", dto.getIsbn());
            return;
        }
        if (finalAuthors == null || finalAuthors.isEmpty()) {
            log.warn("ISBN: {} : 최종 저자 정보가 없어 스킵합니다. ", dto.getIsbn());
            return;
        }
        if (finalPublishDate == null) {
            log.warn("ISBN: {} : 최종 출판일 정보가 없어 스킵합니다.", dto.getIsbn());
            return;
        }
        if (finalPrice <= 0) {
            log.warn("ISBN: {} : 최종 가격 정보가 0 이하라 스킵합니다.", dto.getIsbn());
        }

        saveBookEntity(dto, finalPublisherName, finalPublishDate, finalPrice, finalSalePrice, finalAuthors,
                finalTranslators, finalDescription, finalChapter,
                imageUrl, categoryString);
    }

    //카테고리 부모
    private Category getOrCreateCategoriesFromString(String categoryString) {
        String[] categoryName = categoryString.split("\\s*/\\s*");
        Category parent = null;
        for (String name : categoryName) {
            if (name.length() > 20) {
                name = name.substring(0, 20);
            }
            parent = getOrCreateCategory(name, parent);
        }
        return parent; //마지막 카테고리 반환
    }

    //카테고리 자식
    private Category getOrCreateCategory(String name, Category parent) {
        String cacheKey = (parent == null ? "null" : parent.getId()) + "_" + name;

        return categoryCache.computeIfAbsent(cacheKey, k ->
                categoryRepository.findByCategoryNameAndParent(name, parent)
                        .orElseGet(() -> {
                            Category newCategory = Category.builder()
                                    .categoryName(name)
                                    .parent(parent)
                                    .build();
                            return categoryRepository.save(newCategory);
                        })
        );
    }

    private Publisher getOrCreatePublisher(String name) {
        return publisherCache.computeIfAbsent(name, n -> publisherRepository.findByPublisherName(n)
                .orElseGet(() -> publisherRepository.save(Publisher.builder().publisherName(n).build())));
    }

    private Translator getOrCreateTranslator(String name) {
        return translatorCache.computeIfAbsent(name, n -> translatorRepository.findByTranslatorName(n)
                .orElseGet(() -> translatorRepository.save(Translator.builder().translatorName(n).build())));
    }

    private Author getOrCreateAuthor(String name) {
        return authorCache.computeIfAbsent(name, n -> authorRepository.findByAuthorName(n)
                .orElseGet(() -> authorRepository.save(Author.builder().authorName(n).build())));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        value = value.trim();
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private LocalDate parseAladinDate(String dateStr) {
        if (isNullOrEmpty(dateStr)) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, ALADIN_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("알라딘 날짜 파싱 실패 ({}): {}", dateStr, e.getMessage());
            return null;
        }
    }

    private Map<String, List<String>> parseAladinAuthors(String rawAuthorStr) {
        if (isNullOrEmpty(rawAuthorStr)) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> result = new HashMap<>();
        List<String> participants = Arrays.asList(rawAuthorStr.split("\\s*,\\s*"));
        List<String> authors = participants.stream()
                .filter(s -> s.contains("(지은이)") || !s.contains(")"))
                .map(s -> s.replaceAll("\\s*\\(.*?\\)\\s*", "").trim())
                .collect(Collectors.toList());

        List<String> translators = participants.stream()
                .filter(s -> s.contains("(옮긴이)"))
                .map(s -> s.replaceAll("\\s*\\(.*?\\)\\s*", "").trim())
                .collect(Collectors.toList());

        if (authors.isEmpty() && !translators.isEmpty()) {
            authors = participants.stream()
                    .filter(s -> !s.contains("(옮긴이)"))
                    .map(s -> s.replaceAll("\\s*\\(.*?\\)\\s*", "").trim())
                    .collect(Collectors.toList());
        }

        result.put("authors", authors);
        result.put("translators", translators);
        return result;
    }

    private GeminiBookInfo fillMissingDataWithGemini(String isbn, String title) {
        String prompt = String.format(
                "Provide information for the book (ISBN: %s, Title: %s) in JSON format.\n" +
                        "Required fields: publisher, publishDate (YYYY-MM-DD or YYYY), priceStandard (integer), author (name only).\n"
                        +
                        "If unknown, set null. Do not include markdown formatting.",
                isbn, title
        );

        try {
            String jsonResponse = geminiApiClient.generateContent(prompt);
            if (jsonResponse != null) {
                return objectMapper.readValue(jsonResponse, GeminiBookInfo.class);
            }
        } catch (Exception e) {
            log.warn("Gemini 보강 실패 (ISBN: {}): {}", isbn, e.getMessage());
        }
        return null;
    }

    private void saveBookEntity(DataParserDto dto, String finalPublisherName, LocalDate finalPublishDate,
                                long finalPrice,
                                long finalSalePrice,
                                List<String> finalAuthors, List<String> finalTranslators, String finalDescription,
                                String finalChapter,
                                String imageUrl, String categoryString) {

        String truncatedTitle = truncate(dto.getTitle(), 255);
        String truncatedPublisher = truncate(finalPublisherName, 50);

        Publisher publisher = getOrCreatePublisher(truncatedPublisher);

        //작가
        List<Author> authors = finalAuthors.stream()
                .map(name -> truncate(name, 50))
                .filter(name -> name != null)
                .map(this::getOrCreateAuthor)
                .collect(Collectors.toList());

        //번역가
        List<Translator> translators = finalTranslators.stream()
                .map(name -> truncate(name, 50))
                .filter(name -> name != null)
                .map(this::getOrCreateTranslator)
                .collect(Collectors.toList());
        //book 생성
        Book book = Book.builder()
                .isbn(truncate(dto.getIsbn(), 20))
                .title(truncatedTitle)
                .description(finalDescription)
                .chapter(truncate(finalChapter, 255))
                .publishDate(finalPublishDate)
                .priceStandard(finalPrice)
                .priceSales(finalSalePrice)
                .stockCount(100)
                .stockStatus("Available")
                .packed(true)
                .status(BookStatus.ON_SALE)
                .build();

        // -------조인 테이블들 연결로직 부분 -------
        book.getBookPublishers().add(
                BookPublisher.builder()
                        .book(book)
                        .publisher(publisher)
                        .build()
        );

        for (Author author : authors) {
            book.getBookAuthors().add(
                    BookAuthor.builder()
                            .book(book)
                            .author(author)
                            .build()
            );
        }

        for (Translator translator : translators) {
            book.getBookTranslators().add(
                    BookTranslator.builder()
                            .book(book)
                            .translator(translator)
                            .build()
            );
        }

        if (imageUrl != null && !imageUrl.isBlank()) {
            book.getImages().add(
                    BookImage.builder()
                            .book(book)
                            .imagePath(imageUrl)
                            .build()
            );

        }

        if (categoryString != null) {
            Category deepestCategory = getOrCreateCategoriesFromString(categoryString);
            book.getBookCategories().add(
                    BookCategory.builder()
                            .book(book)
                            .category(deepestCategory)
                            .build()
            );
        }

        bookRepository.save(book);

    }

    private LocalDate parseGenericDate(String dateStr) {
        if (isNullOrEmpty(dateStr)) {
            return null;
        }

        try {
            Date date = DateUtils.parseDate(dateStr.trim(), "yyyy-MM-dd", "yyyyMMdd", "yyyy-MM", "yyyy");
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

}
