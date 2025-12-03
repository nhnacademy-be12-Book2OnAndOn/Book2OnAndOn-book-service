package org.nhnacademy.book2onandonbookservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.repository.BatchInsertRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.ContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
import org.nhnacademy.book2onandonbookservice.service.BookBatchService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private PublisherRepository publisherRepository;

    @Mock
    private ContributorRepository contributorRepository;

    @Mock
    private BatchInsertRepository batchInsertRepository;

    @Mock
    private BookBatchService bookBatchService;

    @Mock
    private ApplicationArguments applicationArguments;

    @Mock
    private Resource resource;

    @InjectMocks
    private DataInitializer dataInitializer;

    private Publisher testPublisher;
    private Contributor testContributor;

    @BeforeEach
    void setUp() {
        testPublisher = Publisher.builder()
                .id(1L)
                .publisherName("테스트 출판사")
                .build();

        testContributor = Contributor.builder()
                .id(1L)
                .contributorName("홍길동")
                .build();
    }

    @Test
    @DisplayName("데이터가 이미 존재하면 초기화를 건너뜀")
    void run_SkipWhenDataExists() throws Exception {
        when(bookRepository.count()).thenReturn(100L);

        dataInitializer.run(applicationArguments);

        verify(bookRepository).count();
        verify(publisherRepository, never()).findAll();
        verify(contributorRepository, never()).findAll();
    }

//    @Test
//    @DisplayName("데이터가 없으면 초기화 진행")
//    void run_ProcessWhenNoData() throws Exception {
//        // given
//        when(bookRepository.count()).thenReturn(0L);
//        when(publisherRepository.findAll()).thenReturn(new ArrayList<>());
//        when(contributorRepository.findAll()).thenReturn(new ArrayList<>());
//
//        // when
//        dataInitializer.run(applicationArguments);
//
//        // then
//        verify(bookRepository).count();
//        verify(publisherRepository).findAll();
//        verify(contributorRepository).findAll();
//    }

    @Test
    @DisplayName("CSV 파일 처리 - 정상 케이스")
    void processCsvFile_Success() throws Exception {
        String csvContent =
                "ISBN_THIRTEEN_NO,TITLE_NM,PUBLISHER_NM,AUTHR_NM,PRC_VALUE,TWO_PBLICTE_DE,BOOK_INTRCN_CN,VLM_NM,IMAGE_URL\n"
                        +
                        "9788901234567,테스트책,테스트출판사,홍길동(지은이),15000,2024-01-15,책소개,1권,http://image.url\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes("UTF-8"));
        when(resource.getInputStream()).thenReturn(inputStream);
        when(resource.getFilename()).thenReturn("test.csv");

        when(publisherRepository.save(any(Publisher.class))).thenReturn(testPublisher);
        when(contributorRepository.save(any(Contributor.class))).thenReturn(testContributor);

        dataInitializer.processCsvFile(resource);

        verify(bookBatchService).saveBooksInBatch(anyList());
    }

    @Test
    @DisplayName("CSV 파일 처리 - 빈 파일")
    void processCsvFile_EmptyFile() throws Exception {
        String csvContent = "";
        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes("UTF-8"));
        when(resource.getInputStream()).thenReturn(inputStream);
        when(resource.getFilename()).thenReturn("empty.csv");

        dataInitializer.processCsvFile(resource);

        verify(bookBatchService, never()).saveBooksInBatch(anyList());
    }

    @Test
    @DisplayName("CSV 파일 처리 - 헤더만 있는 경우")
    void processCsvFile_HeaderOnly() throws Exception {
        String csvContent = "ISBN_THIRTEEN_NO,TITLE_NM,PUBLISHER_NM\n";
        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes("UTF-8"));
        when(resource.getInputStream()).thenReturn(inputStream);
        when(resource.getFilename()).thenReturn("header.csv");

        dataInitializer.processCsvFile(resource);

        verify(bookBatchService, never()).saveBooksInBatch(anyList());
    }

    @Test
    @DisplayName("convertToBook - 필수값 없으면 null 반환")
    void convertToBook_NullWhenMissingRequired() throws Exception {
        String[] row = {"", "", "출판사"};
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("ISBN_THIRTEEN_NO", 0);
        headerMap.put("TITLE_NM", 1);
        headerMap.put("PUBLISHER_NM", 2);

        Book result = invokeConvertToBook(row, headerMap);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("convertToBook - 정상 변환")
    void convertToBook_Success() throws Exception {
        String[] row = {"9788901234567", "테스트책", "테스트출판사", "홍길동(지은이)",
                "15000", "2024-01-15", "책소개", "1권", "http://image.url"};
        Map<String, Integer> headerMap = createHeaderMap();

        when(publisherRepository.save(any(Publisher.class))).thenReturn(testPublisher);
        when(contributorRepository.save(any(Contributor.class))).thenReturn(testContributor);

        Book result = invokeConvertToBook(row, headerMap);

        assertThat(result).isNotNull();
        assertThat(result.getIsbn()).isEqualTo("9788901234567");
        assertThat(result.getTitle()).isEqualTo("테스트책");
        assertThat(result.getPriceStandard()).isEqualTo(15000L);
        assertThat(result.getStatus()).isEqualTo(BookStatus.ON_SALE);
    }

    @Test
    @DisplayName("convertToBook - 출판사명이 없으면 Unknown")
    void convertToBook_UnknownPublisher() throws Exception {
        String[] row = {"9788901234567", "테스트책", "", "", "", "", "", "", ""};
        Map<String, Integer> headerMap = createHeaderMap();

        Publisher unknownPublisher = Publisher.builder()
                .id(999L)
                .publisherName("Unknown")
                .build();
        when(publisherRepository.save(any(Publisher.class))).thenReturn(unknownPublisher);

        Book result = invokeConvertToBook(row, headerMap);

        assertThat(result).isNotNull();
        assertThat(result.getBookPublishers()).hasSize(1);
    }

    @Test
    @DisplayName("parseAndAddContributors - 단일 작가")
    void parseAndAddContributors_SingleAuthor() throws Exception {
        Book book = Book.builder().build();
        String authorStr = "홍길동(지은이)";

        when(contributorRepository.save(any(Contributor.class))).thenReturn(testContributor);

        invokeParseAndAddContributors(book, authorStr);

        assertThat(book.getBookContributors()).hasSize(1);
    }

    @Test
    @DisplayName("parseAndAddContributors - 복수 작가 쉼표 구분")
    void parseAndAddContributors_MultipleAuthorsComma() throws Exception {
        Book book = Book.builder().build();
        String authorStr = "홍길동(지은이),김철수(옮긴이)";

        Contributor contributor1 = Contributor.builder().id(1L).contributorName("홍길동").build();
        Contributor contributor2 = Contributor.builder().id(2L).contributorName("김철수").build();

        when(contributorRepository.save(any(Contributor.class)))
                .thenReturn(contributor1)
                .thenReturn(contributor2);

        invokeParseAndAddContributors(book, authorStr);

        assertThat(book.getBookContributors()).hasSize(2);
    }

    @Test
    @DisplayName("parseAndAddContributors - 세미콜론 구분")
    void parseAndAddContributors_SemicolonSeparator() throws Exception {
        Book book = Book.builder().build();
        String authorStr = "홍길동(지은이);김철수(옮긴이)";

        Contributor contributor1 = Contributor.builder().id(1L).contributorName("홍길동").build();
        Contributor contributor2 = Contributor.builder().id(2L).contributorName("김철수").build();

        when(contributorRepository.save(any(Contributor.class)))
                .thenReturn(contributor1)
                .thenReturn(contributor2);

        invokeParseAndAddContributors(book, authorStr);

        assertThat(book.getBookContributors()).hasSize(2);
    }

    @Test
    @DisplayName("parseAndAddContributors - 외 처리")
    void parseAndAddContributors_WithEtc() throws Exception {
        Book book = Book.builder().build();
        String authorStr = "홍길동 외";

        when(contributorRepository.save(any(Contributor.class))).thenReturn(testContributor);

        invokeParseAndAddContributors(book, authorStr);

        assertThat(book.getBookContributors()).hasSize(1);
    }

    @Test
    @DisplayName("parseAndAddContributors - 외 N명 처리")
    void parseAndAddContributors_WithEtcNumber() throws Exception {
        Book book = Book.builder().build();
        String authorStr = "홍길동 외 2명";

        when(contributorRepository.save(any(Contributor.class))).thenReturn(testContributor);

        invokeParseAndAddContributors(book, authorStr);

        assertThat(book.getBookContributors()).hasSize(1);
    }

    @Test
    @DisplayName("parseAndAddContributors - by 접두사 제거")
    void parseAndAddContributors_RemoveByPrefix() throws Exception {
        Book book = Book.builder().build();
        String authorStr = "by 홍길동";

        when(contributorRepository.save(any(Contributor.class))).thenReturn(testContributor);

        invokeParseAndAddContributors(book, authorStr);

        assertThat(book.getBookContributors()).hasSize(1);
    }

    @Test
    @DisplayName("parseAndAddContributors - 빈 문자열")
    void parseAndAddContributors_EmptyString() throws Exception {
        Book book = Book.builder().build();
        String authorStr = "";

        invokeParseAndAddContributors(book, authorStr);

        assertThat(book.getBookContributors()).isEmpty();
    }

    @Test
    @DisplayName("extractNameAndRole - 괄호 역할")
    void extractNameAndRole_ParenthesisRole() throws Exception {
        String token = "홍길동(지은이)";

        Object result = invokeExtractNameAndRole(token);
        String name = getField(result, "name");
        String role = getField(result, "role");

        assertThat(name).isEqualTo("홍길동");
        assertThat(role).isEqualTo("지은이");
    }

    @Test
    @DisplayName("extractNameAndRole - 접미사 역할")
    void extractNameAndRole_SuffixRole() throws Exception {
        String token = "홍길동 지음";

        Object result = invokeExtractNameAndRole(token);
        String name = getField(result, "name");
        String role = getField(result, "role");

        assertThat(name).isEqualTo("홍길동");
        assertThat(role).isEqualTo("지은이");
    }

    @Test
    @DisplayName("extractNameAndRole - 역할 없음")
    void extractNameAndRole_NoRole() throws Exception {
        String token = "홍길동";

        Object result = invokeExtractNameAndRole(token);
        String name = getField(result, "name");
        String role = getField(result, "role");

        assertThat(name).isEqualTo("홍길동");
        assertThat(role).isEqualTo("지은이"); // 기본값
    }

    @Test
    @DisplayName("normalizeRole - 다양한 역할 통일")
    void normalizeRole_Various() throws Exception {
        assertThat(invokeNormalizeRole("지음")).isEqualTo("지은이");
        assertThat(invokeNormalizeRole("저")).isEqualTo("지은이");
        assertThat(invokeNormalizeRole("공저")).isEqualTo("지은이");
        assertThat(invokeNormalizeRole("옮김")).isEqualTo("옮긴이");
        assertThat(invokeNormalizeRole("역")).isEqualTo("옮긴이");
        assertThat(invokeNormalizeRole("편")).isEqualTo("엮은이");
        assertThat(invokeNormalizeRole("엮음")).isEqualTo("엮은이");
        assertThat(invokeNormalizeRole("글")).isEqualTo("글");
        assertThat(invokeNormalizeRole("그림")).isEqualTo("그림");
    }

    @Test
    @DisplayName("normalizeRole - 50자 넘으면 자르기")
    void normalizeRole_TruncateLongRole() throws Exception {
        String longRole = "a".repeat(60);

        String result = invokeNormalizeRole(longRole);

        assertThat(result).hasSize(50);
    }

    @Test
    @DisplayName("safeGet - 정상 값 가져오기")
    void safeGet_Success() throws Exception {
        String[] row = {"value1", "value2", "value3"};
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("col1", 0);
        headerMap.put("col2", 1);

        String result = invokeSafeGet(row, headerMap, "col1");

        assertThat(result).isEqualTo("value1");
    }

    @Test
    @DisplayName("safeGet - NaN 처리")
    void safeGet_NaN() throws Exception {
        String[] row = {"NaN", "value2"};
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("col1", 0);

        String result = invokeSafeGet(row, headerMap, "col1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("safeGet - 빈 문자열 처리")
    void safeGet_EmptyString() throws Exception {

        String[] row = {"  ", "value2"};
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("col1", 0);

        String result = invokeSafeGet(row, headerMap, "col1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("safeGet - 여러 키 중 첫 번째 유효한 값")
    void safeGet_MultipleKeys() throws Exception {

        String[] row = {"", "value2", "value3"};
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("key1", 0);
        headerMap.put("key2", 1);
        headerMap.put("key3", 2);

        String result = invokeSafeGet(row, headerMap, "key1", "key2", "key3");

        assertThat(result).isEqualTo("value2");
    }

    @Test
    @DisplayName("parsePrice - 정상 파싱")
    void parsePrice_Success() throws Exception {
        assertThat(invokeParsePrice("15000")).isEqualTo(15000L);
        assertThat(invokeParsePrice("15000.5")).isEqualTo(15000L);
    }

    @Test
    @DisplayName("parsePrice - 파싱 실패시 0")
    void parsePrice_Fail() throws Exception {
        assertThat(invokeParsePrice("invalid")).isEqualTo(0L);
        assertThat(invokeParsePrice("")).isEqualTo(0L);
    }

    @Test
    @DisplayName("parseDate - 정상 파싱")
    void parseDate_Success() throws Exception {
        LocalDate result = invokeParseDate("2024-01-15");
        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("parseDate - 파싱 실패시 현재 날짜")
    void parseDate_Fail() throws Exception {
        LocalDate result = invokeParseDate("invalid-date");
        assertThat(result).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("parseDate - 빈 문자열")
    void parseDate_Empty() throws Exception {
        LocalDate result = invokeParseDate("");
        assertThat(result).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("truncate - 문자열 자르기")
    void truncate_Success() throws Exception {
        String result = invokeTruncate("12345678901234567890", 10);
        assertThat(result).hasSize(10);
        assertThat(result).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("truncate - 길이보다 짧으면 그대로")
    void truncate_Shorter() throws Exception {
        String result = invokeTruncate("123", 10);
        assertThat(result).isEqualTo("123");
    }

    @Test
    @DisplayName("truncate - null 처리")
    void truncate_Null() throws Exception {
        String result = invokeTruncate(null, 10);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("createHeaderMap - 헤더 맵 생성")
    void createHeaderMap_Success() throws Exception {
        // given
        String[] headers = {"col1", "col2", "col3"};

        // when
        Map<String, Integer> result = invokeCreateHeaderMap(headers);

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get("col1")).isEqualTo(0);
        assertThat(result.get("col2")).isEqualTo(1);
        assertThat(result.get("col3")).isEqualTo(2);
    }

    // ===== Private Helper Methods =====

    private Map<String, Integer> createHeaderMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("ISBN_THIRTEEN_NO", 0);
        map.put("TITLE_NM", 1);
        map.put("PUBLISHER_NM", 2);
        map.put("AUTHR_NM", 3);
        map.put("PRC_VALUE", 4);
        map.put("TWO_PBLICTE_DE", 5);
        map.put("BOOK_INTRCN_CN", 6);
        map.put("VLM_NM", 7);
        map.put("IMAGE_URL", 8);
        return map;
    }

    private Book invokeConvertToBook(String[] row, Map<String, Integer> headerMap) throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("convertToBook", String[].class, Map.class);
        method.setAccessible(true);
        return (Book) method.invoke(dataInitializer, row, headerMap);
    }

    private void invokeParseAndAddContributors(Book book, String authorStr) throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("parseAndAddContributors", Book.class, String.class);
        method.setAccessible(true);
        method.invoke(dataInitializer, book, authorStr);
    }

    private Object invokeExtractNameAndRole(String token) throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("extractNameAndRole", String.class);
        method.setAccessible(true);
        return method.invoke(dataInitializer, token);
    }

    private String invokeNormalizeRole(String role) throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("normalizeRole", String.class);
        method.setAccessible(true);
        return (String) method.invoke(dataInitializer, role);
    }

    private String invokeSafeGet(String[] row, Map<String, Integer> headerMap, String... keys) throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("safeGet", String[].class, Map.class, String[].class);
        method.setAccessible(true);
        return (String) method.invoke(dataInitializer, row, headerMap, keys);
    }

    private long invokeParsePrice(String price) throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("parsePrice", String.class);
        method.setAccessible(true);
        return (long) method.invoke(dataInitializer, price);
    }

    private LocalDate invokeParseDate(String dateStr) throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("parseDate", String.class);
        method.setAccessible(true);
        return (LocalDate) method.invoke(dataInitializer, dateStr);
    }

    private String invokeTruncate(String val, int len) throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("truncate", String.class, int.class);
        method.setAccessible(true);
        return (String) method.invoke(dataInitializer, val, len);
    }

    private Map<String, Integer> invokeCreateHeaderMap(String[] headers) throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("createHeaderMap", String[].class);
        method.setAccessible(true);
        return (Map<String, Integer>) method.invoke(dataInitializer, (Object) headers);
    }

    private String getField(Object obj, String fieldName) throws Exception {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(obj);
    }
}