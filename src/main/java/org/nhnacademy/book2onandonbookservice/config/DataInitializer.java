package org.nhnacademy.book2onandonbookservice.config;

import com.opencsv.CSVReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.repository.BatchInsertRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.ContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final BookRepository bookRepository;
    private final PublisherRepository publisherRepository;
    private final ContributorRepository contributorRepository;
    private final BatchInsertRepository batchInsertRepository;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private final Map<String, Publisher> publisherCache = new ConcurrentHashMap<>();
    private final Map<String, Contributor> contributorCache = new ConcurrentHashMap<>();
    /*
    캐시를 Redis로 바꾸려 했는데 대량 등록 Batch 작업시엔 로컬 메모리 즉, Map을 쓰는게 압도적으로 빠르다고합니다.
     */
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[,;/&|]");

    // 이름 (비탐욕)
    // 구분: 괄호() 또는 띄어쓰기 후 역할명
    // 예: "홍길동(지은이)", "홍길동 지음", "홍길동 편", "홍길동 그림"
    private static final Pattern ROLE_PATTERN = Pattern.compile(
            "^(.*?)(?:\\s*\\((.*?)\\)|\\s+(지음|옮김|그림|글|엮음|편|저|공저|감수|사진|기획))\\s*$");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (bookRepository.count() > 0) {
            log.info("데이터가 이미 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        log.info("대용량 CSV 데이터 초기화 시작");
        long startTime = System.currentTimeMillis();

        // 캐시 : 이미 DB에 있는 출판사/작가를 메모리에 올림 (중복 Insert 방지 및 속도 향상)
        preloadCaches();

        Resource[] resources = resolver.getResources("classpath:/data/*.csv");
        if (resources.length == 0) {
            log.warn("classpath:/data 경로에 CSV 파일이 없습니다.");
            return;
        }

        for (Resource resource : resources) {
            processCsvFile(resource);
        }

        long endTime = System.currentTimeMillis();
        log.info("전체 초기화 완료! 소요 시간: {}초", (endTime - startTime) / 1000);
    }

    private void preloadCaches() {
        log.info("캐시 워밍업 중 (기존 데이터 로드)...");
        publisherRepository.findAll().forEach(p -> publisherCache.put(p.getPublisherName(), p));
        contributorRepository.findAll().forEach(c -> contributorCache.put(c.getContributorName(), c));
        log.info("캐시 로드 완료 (Publisher: {}, Contributor: {})", publisherCache.size(), contributorCache.size());
    }

    @Transactional
    public void processCsvFile(Resource resource) {
        try (CSVReader csvReader = new CSVReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            log.info("파일 읽기 시작: {}", resource.getFilename());

            //readAll()로 한 번에 메모리에 로드 (I/O 최소화)
            List<String[]> allRows = csvReader.readAll();
            if (allRows.isEmpty()) {
                return;
            }

            String[] headers = allRows.get(0);
            Map<String, Integer> headerMap = createHeaderMap(headers);

            List<String[]> dataRows = allRows.subList(1, allRows.size());
            log.info("총 {}건의 데이터 처리를 시작합니다.", dataRows.size());

            // 배치 처리를 위한 리스트 (1000건씩 묶어서 저장)
            List<Book> bookBatch = new ArrayList<>(1000);

            for (int i = 0; i < dataRows.size(); i++) {
                String[] row = dataRows.get(i);

                try {
                    // DTO 변환 없이 바로 Entity 생성
                    Book book = convertToBook(row, headerMap);
                    if (book != null) {
                        bookBatch.add(book);
                    }
                } catch (Exception e) {
                    // 개별 라인 에러는 전체 중단을 막기 위해 로그만 찍고 넘어감
                    log.debug("라인 {} 파싱 스킵: {}", i, e.getMessage());
                }

                // 1000개가 모이면 DB로
                if (bookBatch.size() >= 1000) {
                    saveBatch(bookBatch);
                    bookBatch.clear();
                    if (i % 10000 == 0) {
                        log.info("{} 건 처리 완료...", i);
                    }
                }
            }

            // 남은 데이터 처리
            if (!bookBatch.isEmpty()) {
                saveBatch(bookBatch);
            }

        } catch (Exception e) {
            log.error("파일 처리 중 치명적 오류 발생: {}", resource.getFilename(), e);
        }
    }

    private void saveBatch(List<Book> books) {
        if (books.isEmpty()) {
            return;
        }

        batchInsertRepository.saveAllBooks(books);

        List<String> isbns = books.stream().map(Book::getIsbn).collect(Collectors.toList());
        List<BookRepository.BookIdAndIsbn> savedIds = bookRepository.findByIsbnIn(isbns);

        Map<String, Long> isbnIdMap = savedIds.stream()
                .collect(Collectors.toMap(BookRepository.BookIdAndIsbn::getIsbn, BookRepository.BookIdAndIsbn::getId,
                        (b1, b2) -> b1));

        List<BookContributor> allContributors = new ArrayList<>();
        List<BookPublisher> allPublishers = new ArrayList<>();
        List<BookImage> allImages = new ArrayList<>();

        for (Book originalBook : books) {
            Long bookId = isbnIdMap.get(originalBook.getIsbn());
            Book proxyBook = Book.builder().id(bookId).build();
            if (bookId == null) {
                continue;
            }

            for (BookContributor bc : originalBook.getBookContributors()) {
                bc.setBook(proxyBook);
                allContributors.add(bc);
            }

            // BookPublisher 처리
            for (BookPublisher bp : originalBook.getBookPublishers()) {
                bp.setBook(proxyBook);
                allPublishers.add(bp);
            }

            for (BookImage bi : originalBook.getImages()) {
                bi.setBook(proxyBook);
                allImages.add(bi);
            }
        }
        batchInsertRepository.saveBookImages(allImages);
        batchInsertRepository.saveBookRelations(allContributors, allPublishers);
    }

    private Book convertToBook(String[] row, Map<String, Integer> h) {
        // 안전하게 필수값 가져오기
        String isbn = safeGet(row, h, "ISBN_THIRTEEN_NO", "ISBN_NO");
        String title = safeGet(row, h, "TITLE_NM");

        // 필수값이 없으면 스킵
        if (!StringUtils.hasText(isbn) || !StringUtils.hasText(title)) {
            return null;
        }

        // 출판사 처리 (캐시 조회 -> 없으면 저장 후 캐시 등록)
        String pubName = safeGet(row, h, "PUBLISHER_NM");
        if (!StringUtils.hasText(pubName)) {
            pubName = "Unknown";
        }

        Publisher publisher = publisherCache.computeIfAbsent(pubName, name ->
                publisherRepository.save(Publisher.builder().publisherName(name).build())
        );

        // Book Entity 생성
        Book book = Book.builder()
                .isbn(truncate(isbn, 20))
                .title(truncate(title, 255))
                .description(safeGet(row, h, "BOOK_INTRCN_CN")) // 책 소개
                .chapter(null) // 목차는 CSV에 없으면 null
                .priceStandard(parsePrice(safeGet(row, h, "PRC_VALUE")))
                .publishDate(parseDate(safeGet(row, h, "TWO_PBLICTE_DE")))
                .stockCount(100) // 기본 재고
                .isWrapped(true) // 포장 가능 여부
                .status(BookStatus.ON_SALE)
                .build();

        //연관관계 설정: 출판사
        book.getBookPublishers().add(BookPublisher.builder()
                .book(book)
                .publisher(publisher)
                .build());

        // 연관관계 설정: 작가/역자 등 (Contributor 파싱)
        String rawAuthorStr = safeGet(row, h, "AUTHR_NM");
        if (StringUtils.hasText(rawAuthorStr)) {
            parseAndAddContributors(book, rawAuthorStr);
        }
        String imageUrl = safeGet(row, h, "IMAGE_URL");
        if (StringUtils.hasText(imageUrl)) {
            book.getImages().add(BookImage.builder()
                    .book(book)
                    .imagePath(imageUrl)
                    .build());
        }

        return book;
    }

    /**
     * 복잡한 작가 문자열을 파싱하여 BookContributor로 연결하는 로직 예: "홍길동(지은이), 김철수(옮긴이); 이영희 그림" -> 각각 분리하여 저장
     */
    private void parseAndAddContributors(Book book, String rawAuthorStr) {
        // 전처리: "by ", "illustrated by" 등 제거
        String cleanedStr = rawAuthorStr.replaceAll("(?i)\\s*by\\s*", "")
                .replaceAll("(?i)\\s*illustrated\\s*", "");

        // 구분자로 토큰 분리
        String[] tokens = SPLIT_PATTERN.split(cleanedStr);
        Set<String> addedKeys = new HashSet<>();

        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty() || token.equals("외")) {
                continue;
            }

            // "홍길동 외 2명" 처리
            if (token.contains(" 외")) {
                token = token.split(" 외")[0].trim();
            }

            // 이름과 역할 추출
            ContributorData cData = extractNameAndRole(token);
            if (!StringUtils.hasText(cData.name)) {
                continue;
            }

            // Contributor 가져오기 (캐시 활용)
            Contributor contributor = contributorCache.computeIfAbsent(cData.name, n ->
                    contributorRepository.save(Contributor.builder().contributorName(n).build())
            );

            String uniqueKey = contributor.getId() + "_" + cData.role;

            if (addedKeys.contains(uniqueKey)) {
                continue;
            }

            addedKeys.add(uniqueKey);

            // BookContributor 연결
            book.getBookContributors().add(BookContributor.builder()
                    .book(book)
                    .contributor(contributor)
                    .roleType(cData.role) // 파싱된 역할(지은이, 옮긴이 등) 저장
                    .build());
        }
    }

    // 내부 헬퍼 클래스
    private static class ContributorData {
        String name;
        String role;

        public ContributorData(String name, String role) {
            this.name = name;
            this.role = role;
        }
    }

    // 정규식을 이용해 "이름"과 "역할"을 분리
    private ContributorData extractNameAndRole(String token) {
        Matcher matcher = ROLE_PATTERN.matcher(token);

        String name = token;
        String role = "지은이"; // 기본값

        if (matcher.find()) {
            name = matcher.group(1).trim(); // 그룹1: 이름
            String foundRole = matcher.group(2); // 그룹2: 괄호 안 역할

            if (foundRole == null) {
                foundRole = matcher.group(3); // 그룹3: 접미사 역할
            }

            if (foundRole != null) {
                role = normalizeRole(foundRole);
            }
        }
        return new ContributorData(name, role);
    }

    // 역할 명칭 통일
    private String normalizeRole(String rawRole) {
        rawRole = rawRole.trim();
        if (rawRole.equals("지음") || rawRole.equals("저") || rawRole.equals("공저")) {
            return "지은이";
        }
        if (rawRole.equals("옮김") || rawRole.equals("역")) {
            return "옮긴이";
        }
        if (rawRole.equals("편") || rawRole.equals("엮음")) {
            return "엮은이";
        }
        if (rawRole.equals("글")) {
            return "글";
        }
        if (rawRole.equals("그림")) {
            return "그림";
        }

        if (rawRole.length() > 50) {
            return rawRole.substring(0, 50);
        }
        return rawRole;
    }

    // --- 유틸리티 메서드 ---

    private String safeGet(String[] row, Map<String, Integer> headerMap, String... keys) {
        for (String key : keys) {
            Integer idx = headerMap.get(key);
            if (idx != null && idx >= 0 && idx < row.length) {
                String val = row[idx];
                if (val != null && !val.equalsIgnoreCase("NaN") && !val.trim().isEmpty()) {
                    return val.trim();
                }
            }
        }
        return "";
    }

    private long parsePrice(String price) {
        try {
            return (long) Double.parseDouble(price);
        } catch (Exception e) {
            return 0L;
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private String truncate(String val, int len) {
        if (val == null) {
            return null;
        }
        return val.length() > len ? val.substring(0, len) : val;
    }

    private Map<String, Integer> createHeaderMap(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].trim(), i);
        }
        return map;
    }
}