package org.nhnacademy.book2onandonbookservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.repository.BookEnrichmentTaskRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.ContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
import org.nhnacademy.book2onandonbookservice.service.BookBatchService;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ImageUploadService imageUploadService;

    @Mock
    private BookEnrichmentTaskRepository taskRepository;

    @Mock
    private PublisherRepository publisherRepository;

    @Mock
    private ContributorRepository contributorRepository;

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
    @DisplayName("run - 데이터가 이미 존재하면 초기화를 건너뜀")
    void run_skipWhenDataExists() throws Exception {
        when(bookRepository.count()).thenReturn(100L);

        dataInitializer.run(applicationArguments);

        verify(bookRepository).count();
        verify(taskRepository).initTasksFromBook();
        verify(publisherRepository, never()).findAll();
        verify(contributorRepository, never()).findAll();
    }

    @Test
    @DisplayName("run - 데이터가 없을 때 초기화 진행")
    void run_initializeWhenNoData() throws Exception {
        when(bookRepository.count()).thenReturn(0L);
        when(publisherRepository.findAll()).thenReturn(new ArrayList<>());
        when(contributorRepository.findAll()).thenReturn(new ArrayList<>());

        PathMatchingResourcePatternResolver mockResolver = mock(PathMatchingResourcePatternResolver.class);
        when(mockResolver.getResources(anyString())).thenReturn(new Resource[0]);

        setPrivateField(dataInitializer, "resolver", mockResolver);

        dataInitializer.run(applicationArguments);

        verify(bookRepository).count();
        verify(publisherRepository).findAll();
        verify(contributorRepository).findAll();
    }

    @Test
    @DisplayName("initializeEnrichmentTasks - 정상 실행")
    void initializeEnrichmentTasks_success() throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("initializeEnrichmentTasks");
        method.setAccessible(true);
        method.invoke(dataInitializer);

        verify(taskRepository).initTasksFromBook();
    }

    @Test
    @DisplayName("initializeEnrichmentTasks - 예외 발생 시 처리")
    void initializeEnrichmentTasks_handleException() throws Exception {
        doThrow(new RuntimeException("DB Error")).when(taskRepository).initTasksFromBook();

        Method method = DataInitializer.class.getDeclaredMethod("initializeEnrichmentTasks");
        method.setAccessible(true);
        method.invoke(dataInitializer);

        verify(taskRepository).initTasksFromBook();
    }

    @Test
    @DisplayName("preloadCaches - 캐시 로드")
    void preloadCaches_success() throws Exception {
        List<Publisher> publishers = List.of(testPublisher);
        List<Contributor> contributors = List.of(testContributor);

        when(publisherRepository.findAll()).thenReturn(publishers);
        when(contributorRepository.findAll()).thenReturn(contributors);

        Method method = DataInitializer.class.getDeclaredMethod("preloadCaches");
        method.setAccessible(true);
        method.invoke(dataInitializer);

        verify(publisherRepository).findAll();
        verify(contributorRepository).findAll();
    }


    @Test
    @DisplayName("processCsvFile - 빈 파일")
    void processCsvFile_emptyFile() throws Exception {
        String csvContent = "";
        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        when(resource.getInputStream()).thenReturn(inputStream);
        when(resource.getFilename()).thenReturn("empty.csv");

        dataInitializer.processCsvFile(resource);

        verify(bookBatchService, never()).saveBooksInBatch(anyList());
    }

    @Test
    @DisplayName("processCsvFile - IOException 발생")
    void processCsvFile_ioException() throws Exception {
        when(resource.getInputStream()).thenThrow(new IOException("File read error"));
        when(resource.getFilename()).thenReturn("error.csv");

        dataInitializer.processCsvFile(resource);

        verify(bookBatchService, never()).saveBooksInBatch(anyList());
    }


    @Test
    @DisplayName("processCsvFile - 헤더만 있는 경우")
    void processCsvFile_headerOnly() throws Exception {
        String csvContent = "ISBN_THIRTEEN_NO,TITLE_NM,PUBLISHER_NM\n";
        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        when(resource.getInputStream()).thenReturn(inputStream);
        when(resource.getFilename()).thenReturn("header.csv");

        dataInitializer.processCsvFile(resource);

        verify(bookBatchService, never()).saveBooksInBatch(anyList());
    }



    @Test
    @DisplayName("saveBatchSafe - 정상 저장")
    void saveBatchSafe_success() throws Exception {
        List<Book> batch = List.of(Book.builder().build());

        Method method = DataInitializer.class.getDeclaredMethod("saveBatchSafe", List.class);
        method.setAccessible(true);
        method.invoke(dataInitializer, batch);

        verify(bookBatchService).saveBooksInBatch(batch);
    }

    @Test
    @DisplayName("saveBatchSafe - 일반 예외 처리")
    void saveBatchSafe_generalException() throws Exception {
        List<Book> batch = List.of(Book.builder().build());
        doThrow(new RuntimeException("Save failed")).when(bookBatchService).saveBooksInBatch(anyList());

        Method method = DataInitializer.class.getDeclaredMethod("saveBatchSafe", List.class);
        method.setAccessible(true);
        method.invoke(dataInitializer, batch);

        verify(bookBatchService).saveBooksInBatch(batch);
    }

    @Test
    @DisplayName("processSingleRow - 정상 처리")
    void processSingleRow_success() throws Exception {
        String[] row = {"9788901234567", "테스트책", "출판사", "작가", "10000", "2024-01-01", "설명", "1권", "http://url"};
        Map<String, Integer> headerMap = createHeaderMap();

        when(publisherRepository.findByPublisherName("출판사"))
                .thenReturn(Optional.empty());
        when(publisherRepository.saveAndFlush(any(Publisher.class)))
                .thenReturn(testPublisher);
        when(contributorRepository.save(any(Contributor.class)))
                .thenReturn(testContributor);

        Method method = DataInitializer.class.getDeclaredMethod("processSingleRow", String[].class, Map.class, int.class);
        method.setAccessible(true);
        Book result = (Book) method.invoke(dataInitializer, row, headerMap, 0);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("테스트책");
    }

    @Test
    @DisplayName("processSingleRow - 예외 발생 시 null 반환")
    void processSingleRow_exceptionReturnsNull() throws Exception {
        String[] row = {"invalid"};
        Map<String, Integer> headerMap = createHeaderMap();

        Method method = DataInitializer.class.getDeclaredMethod("processSingleRow", String[].class, Map.class, int.class);
        method.setAccessible(true);
        Book result = (Book) method.invoke(dataInitializer, row, headerMap, 0);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("convertToBook - 필수값 없으면 null")
    void convertToBook_nullWhenMissingRequired() throws Exception {
        String[] row = {"", "", "출판사"};
        Map<String, Integer> headerMap = createHeaderMap();

        Book result = invokeConvertToBook(row, headerMap);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("convertToBook - 정상 변환")
    void convertToBook_success() throws Exception {
        String[] row = {"9788901234567", "테스트책", "테스트출판사", "홍길동(지은이)",
                "15000", "2024-01-15", "책소개", "1권", "http://image.url"};
        Map<String, Integer> headerMap = createHeaderMap();

        when(publisherRepository.findByPublisherName(anyString())).thenReturn(Optional.empty());
        when(publisherRepository.saveAndFlush(any(Publisher.class))).thenReturn(testPublisher);
        when(contributorRepository.save(any(Contributor.class))).thenReturn(testContributor);

        Book result = invokeConvertToBook(row, headerMap);

        assertThat(result).isNotNull();
        assertThat(result.getIsbn()).isEqualTo("9788901234567");
        assertThat(result.getTitle()).isEqualTo("테스트책");
        assertThat(result.getPriceStandard()).isEqualTo(15000L);
        assertThat(result.getStatus()).isEqualTo(BookStatus.ON_SALE);
        assertThat(result.getThumbnail()).isEqualTo("http://image.url");
    }

    @Test
    @DisplayName("convertToBook - 출판사명이 없으면 Unknown")
    void convertToBook_unknownPublisher() throws Exception {
        String[] row = {"9788901234567", "테스트책", "", "", "", "", "", "", ""};
        Map<String, Integer> headerMap = createHeaderMap();

        Publisher unknownPublisher = Publisher.builder()
                .id(999L)
                .publisherName("Unknown")
                .build();
        when(publisherRepository.findByPublisherName("Unknown")).thenReturn(Optional.empty());
        when(publisherRepository.saveAndFlush(any(Publisher.class))).thenReturn(unknownPublisher);

        Book result = invokeConvertToBook(row, headerMap);

        assertThat(result).isNotNull();
        assertThat(result.getBookPublishers()).hasSize(1);
    }



    @Test
    @DisplayName("convertToBook - 긴 ISBN 자르기")
    void convertToBook_truncateLongIsbn() throws Exception {
        String longIsbn = "1".repeat(30);
        String[] row = {longIsbn, "테스트책", "출판사", "", "10000", "", "", "", ""};
        Map<String, Integer> headerMap = createHeaderMap();
        when(publisherRepository.findByPublisherName(anyString())).thenReturn(Optional.empty());
        when(publisherRepository.saveAndFlush(any(Publisher.class))).thenReturn(testPublisher);

        Book result = invokeConvertToBook(row, headerMap);

        assertThat(result).isNotNull();
        assertThat(result.getIsbn()).hasSize(20);
    }

    @DisplayName("parseAndAddContributors - 다양한 입력 케이스")
    @ParameterizedTest(name = "[{index}] 입력: \"{0}\" -> 예상 작가 수: {1}")
    @CsvSource(value = {
            "'홍길동(지은이)', 1",
            "'홍길동 외', 1",
            "'홍길동 외 2명', 1",
            "'by 홍길동', 1",
            "'illustrated 홍길동', 1",
            "'', 0"
    })
    void parseAndAddContributors_parameterized(String authorStr, int expectedSize) throws Exception {
        Book book = Book.builder().build();

        Contributor contributor1 = Contributor.builder().id(1L).contributorName("홍길동").build();
        Contributor contributor2 = Contributor.builder().id(2L).contributorName("김철수").build();

        lenient().when(contributorRepository.save(any(Contributor.class)))
                .thenAnswer(invocation -> {
                    Contributor c = invocation.getArgument(0);
                    if (c.getContributorName().equals("홍길동")) return contributor1;
                    return contributor2;
                });

        invokeParseAndAddContributors(book, authorStr);

        assertThat(book.getBookContributors()).hasSize(expectedSize);
    }



    @Test
    @DisplayName("parseAndAddContributors - null 입력")
    void parseAndAddContributors_nullInput() throws Exception {
        Book book = Book.builder().build();

        invokeParseAndAddContributors(book, null);

        assertThat(book.getBookContributors()).isEmpty();
    }

    @Test
    @DisplayName("parseAndAddContributors - 중복 제거")
    void parseAndAddContributors_duplicateRemoval() throws Exception {
        Book book = Book.builder().build();
        String authorStr = "홍길동(지은이),홍길동(지은이)";

        Contributor contributor = Contributor.builder()
                .id(1L)
                .contributorName("홍길동")
                .build();

        Map<String, Contributor> contributorCache = new ConcurrentHashMap<>();
        contributorCache.put("홍길동", contributor);
        setPrivateField(dataInitializer, "contributorCache", contributorCache);

        invokeParseAndAddContributors(book, authorStr);

        assertThat(book.getBookContributors()).hasSize(1);
    }

    @DisplayName("extractNameAndRole - 다양한 입력 형식")
    @ParameterizedTest(name = "[{index}] 입력: \"{0}\" -> 이름: {1}, 역할: {2}")
    @CsvSource(value = {
            "홍길동(지은이), 홍길동, 지은이",
            "홍길동 지음, 홍길동, 지은이",
            "홍길동, 홍길동, 지은이",
            "홍길동 옮김, 홍길동, 옮긴이",
            "홍길동 그림, 홍길동, 그림",
            "홍길동 글, 홍길동, 글",
            "홍길동(편), 홍길동, 엮은이"
    })
    void extractNameAndRole_parameterized(String token, String expectedName, String expectedRole) throws Exception {
        Object result = invokeExtractNameAndRole(token);
        String name = getField(result, "name");
        String role = getField(result, "role");

        assertThat(name).isEqualTo(expectedName);
        assertThat(role).isEqualTo(expectedRole);
    }

    @Test
    @DisplayName("normalizeRole - 다양한 역할 통일")
    void normalizeRole_various() throws Exception {
        assertThat(invokeNormalizeRole("지음")).isEqualTo("지은이");
        assertThat(invokeNormalizeRole("저")).isEqualTo("지은이");
        assertThat(invokeNormalizeRole("공저")).isEqualTo("지은이");
        assertThat(invokeNormalizeRole("옮김")).isEqualTo("옮긴이");
        assertThat(invokeNormalizeRole("역")).isEqualTo("옮긴이");
        assertThat(invokeNormalizeRole("편")).isEqualTo("엮은이");
        assertThat(invokeNormalizeRole("엮음")).isEqualTo("엮은이");
        assertThat(invokeNormalizeRole("글")).isEqualTo("글");
        assertThat(invokeNormalizeRole("그림")).isEqualTo("그림");
        assertThat(invokeNormalizeRole("기타")).isEqualTo("기타");
    }

    @Test
    @DisplayName("normalizeRole - 50자 초과 자르기")
    void normalizeRole_truncateLongRole() throws Exception {
        String longRole = "a".repeat(60);

        String result = invokeNormalizeRole(longRole);

        assertThat(result).hasSize(50);
    }

    @Test
    @DisplayName("cleanRawString - by와 illustrated 제거")
    void cleanRawString_removeKeywords() throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("cleanRawString", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(dataInitializer, "by 홍길동 illustrated");

        assertThat(result).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("isValidToken - 유효성 검증")
    void isValidToken_validation() throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("isValidToken", String.class);
        method.setAccessible(true);

        assertThat((boolean) method.invoke(dataInitializer, "홍길동")).isTrue();
        assertThat((boolean) method.invoke(dataInitializer, "외")).isFalse();
        assertThat((boolean) method.invoke(dataInitializer, "")).isFalse();
        assertThat((boolean) method.invoke(dataInitializer, "  ")).isFalse();
    }

    @Test
    @DisplayName("removeEtcSuffix - '외' 접미사 제거")
    void removeEtcSuffix_removeSuffix() throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("removeEtcSuffix", String.class);
        method.setAccessible(true);

        String result1 = (String) method.invoke(dataInitializer, "홍길동 외");
        String result2 = (String) method.invoke(dataInitializer, "홍길동 외 2명");
        String result3 = (String) method.invoke(dataInitializer, "홍길동");

        assertThat(result1).isEqualTo("홍길동");
        assertThat(result2).isEqualTo("홍길동");
        assertThat(result3).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("linkContributorToBook - 기여자 연결")
    void linkContributorToBook_success() throws Exception {
        Book book = Book.builder().build();
        Object cData = createContributorData("홍길동", "지은이");
        Set<String> processedKeys = new java.util.HashSet<>();

        Map<String, Contributor> contributorCache = new ConcurrentHashMap<>();
        contributorCache.put("홍길동", testContributor);
        setPrivateField(dataInitializer, "contributorCache", contributorCache);

        Method method = DataInitializer.class.getDeclaredMethod("linkContributorToBook",
                Book.class, cData.getClass(), Set.class);
        method.setAccessible(true);
        method.invoke(dataInitializer, book, cData, processedKeys);

        assertThat(book.getBookContributors()).hasSize(1);
    }

    @Test
    @DisplayName("getOrCreateContributor - 캐시에서 가져오기")
    void getOrCreateContributor_fromCache() throws Exception {
        Map<String, Contributor> contributorCache = new ConcurrentHashMap<>();
        contributorCache.put("홍길동", testContributor);
        setPrivateField(dataInitializer, "contributorCache", contributorCache);

        Method method = DataInitializer.class.getDeclaredMethod("getOrCreateContributor", String.class);
        method.setAccessible(true);
        Contributor result = (Contributor) method.invoke(dataInitializer, "홍길동");

        assertThat(result).isEqualTo(testContributor);
    }

    @Test
    @DisplayName("getOrCreateContributor - 새로 생성")
    void getOrCreateContributor_createNew() throws Exception {
        when(contributorRepository.save(any(Contributor.class))).thenReturn(testContributor);

        Method method = DataInitializer.class.getDeclaredMethod("getOrCreateContributor", String.class);
        method.setAccessible(true);
        Contributor result = (Contributor) method.invoke(dataInitializer, "새작가");

        assertThat(result).isNotNull();
        verify(contributorRepository).save(any(Contributor.class));
    }



    @Test
    @DisplayName("safeGet - 정상 값 가져오기")
    void safeGet_success() throws Exception {
        String[] row = {"value1", "value2", "value3"};
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("col1", 0);
        headerMap.put("col2", 1);

        String result = invokeSafeGet(row, headerMap, "col1");

        assertThat(result).isEqualTo("value1");
    }

    @Test
    @DisplayName("safeGet - NaN 처리")
    void safeGet_nan() throws Exception {
        String[] row = {"NaN", "value2"};
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("col1", 0);

        String result = invokeSafeGet(row, headerMap, "col1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("safeGet - 빈 문자열 처리")
    void safeGet_emptyString() throws Exception {
        String[] row = {"  ", "value2"};
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("col1", 0);

        String result = invokeSafeGet(row, headerMap, "col1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("safeGet - 여러 키 중 첫 번째 유효한 값")
    void safeGet_multipleKeys() throws Exception {
        String[] row = {"", "value2", "value3"};
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("key1", 0);
        headerMap.put("key2", 1);
        headerMap.put("key3", 2);

        String result = invokeSafeGet(row, headerMap, "key1", "key2", "key3");

        assertThat(result).isEqualTo("value2");
    }

    @Test
    @DisplayName("safeGet - 존재하지 않는 키")
    void safeGet_nonExistentKey() throws Exception {
        String[] row = {"value1"};
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("col1", 0);

        String result = invokeSafeGet(row, headerMap, "nonExistent");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("safeGet - null 값 처리")
    void safeGet_nullValue() throws Exception {
        String[] row = {null, "value2"};
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("col1", 0);

        String result = invokeSafeGet(row, headerMap, "col1");

        assertThat(result).isEmpty();
    }

    @DisplayName("parsePrice - 다양한 입력")
    @ParameterizedTest
    @CsvSource(value = {
            "15000, 15000",
            "15000.5, 15000",
            "15000.9, 15000",
            "0, 0",
            "invalid, 0",
            "'', 0"
    })
    void parsePrice_various(String input, long expected) throws Exception {
        assertThat(invokeParsePrice(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("parseDate - 정상 파싱")
    void parseDate_success() throws Exception {
        LocalDate result = invokeParseDate("2024-01-15");
        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("parseDate - 파싱 실패 시 현재 날짜")
    void parseDate_fail() throws Exception {
        LocalDate result = invokeParseDate("invalid-date");
        assertThat(result).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("parseDate - 빈 문자열")
    void parseDate_empty() throws Exception {
        LocalDate result = invokeParseDate("");
        assertThat(result).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("parseDate - null")
    void parseDate_null() throws Exception {
        LocalDate result = invokeParseDate(null);
        assertThat(result).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("truncate - 문자열 자르기")
    void truncate_success() throws Exception {
        String result = invokeTruncate("12345678901234567890", 10);
        assertThat(result).hasSize(10).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("truncate - 길이보다 짧으면 그대로")
    void truncate_shorter() throws Exception {
        String result = invokeTruncate("123", 10);
        assertThat(result).isEqualTo("123");
    }

    @Test
    @DisplayName("truncate - null 처리")
    void truncate_null() throws Exception {
        String result = invokeTruncate(null, 10);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("createHeaderMap - 헤더 맵 생성")
    void createHeaderMap_success() throws Exception {
        String[] headers = {"col1", "col2", "col3"};

        Map<String, Integer> result = invokeCreateHeaderMap(headers);

        assertThat(result).hasSize(3)
                .containsEntry("col1", 0)
                .containsEntry("col2", 1)
                .containsEntry("col3", 2);
    }

    @Test
    @DisplayName("createHeaderMap - 공백 포함 헤더")
    void createHeaderMap_withSpaces() throws Exception {
        String[] headers = {" col1 ", "col2  ", "  col3"};

        Map<String, Integer> result = invokeCreateHeaderMap(headers);

        assertThat(result)
                .containsEntry("col1", 0)
                .containsEntry("col2", 1)
                .containsEntry("col3", 2);
    }

    @Test
    @DisplayName("getOrCreatePublisherSafe - 캐시에서 가져오기")
    void getOrCreatePublisherSafe_fromCache() throws Exception {
        Map<String, Publisher> publisherCache = new ConcurrentHashMap<>();
        publisherCache.put("테스트 출판사", testPublisher);
        setPrivateField(dataInitializer, "publisherCache", publisherCache);

        Method method = DataInitializer.class.getDeclaredMethod("getOrCreatePublisherSafe", String.class);
        method.setAccessible(true);
        Publisher result = (Publisher) method.invoke(dataInitializer, "테스트 출판사");

        assertThat(result).isEqualTo(testPublisher);
    }

    @Test
    @DisplayName("getOrCreatePublisherSafe - DB에서 조회")
    void getOrCreatePublisherSafe_fromDb() throws Exception {
        when(publisherRepository.findByPublisherName("테스트출판사"))
                .thenReturn(Optional.of(testPublisher));

        Method method = DataInitializer.class.getDeclaredMethod("getOrCreatePublisherSafe", String.class);
        method.setAccessible(true);
        Publisher result = (Publisher) method.invoke(dataInitializer, "테스트출판사");

        assertThat(result).isEqualTo(testPublisher);
    }

    @Test
    @DisplayName("getOrCreatePublisherSafe - 새로 생성")
    void getOrCreatePublisherSafe_createNew() throws Exception {
        when(publisherRepository.findByPublisherName(anyString()))
                .thenReturn(Optional.empty());
        when(publisherRepository.saveAndFlush(any(Publisher.class)))
                .thenReturn(testPublisher);

        Method method = DataInitializer.class.getDeclaredMethod("getOrCreatePublisherSafe", String.class);
        method.setAccessible(true);
        Publisher result = (Publisher) method.invoke(dataInitializer, "새출판사");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("getOrCreatePublisherSafe - 저장 실패 시 재조회")
    void getOrCreatePublisherSafe_retryOnSaveFailure() throws Exception {
        when(publisherRepository.findByPublisherName("새출판사"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(testPublisher));
        when(publisherRepository.saveAndFlush(any(Publisher.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate"));

        Method method = DataInitializer.class.getDeclaredMethod("getOrCreatePublisherSafe", String.class);
        method.setAccessible(true);
        Publisher result = (Publisher) method.invoke(dataInitializer, "새출판사");

        assertThat(result).isEqualTo(testPublisher);
    }

    @Test
    @DisplayName("getOrCreatePublisherSafe - 모든 방법 실패 시 broadSearch 호출")
    void getOrCreatePublisherSafe_fallbackToBroadSearch() throws Exception {
        when(publisherRepository.findByPublisherName(anyString()))
                .thenReturn(Optional.empty());
        when(publisherRepository.saveAndFlush(any(Publisher.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate"));
        when(publisherRepository.findAll()).thenReturn(List.of(testPublisher));

        Method method = DataInitializer.class.getDeclaredMethod("getOrCreatePublisherSafe", String.class);
        method.setAccessible(true);
        Publisher result = (Publisher) method.invoke(dataInitializer, "테스트 출판사");

        assertThat(result).isEqualTo(testPublisher);
    }

    @Test
    @DisplayName("findPublisherByBroadSearch - 캐시에서 찾기")
    void findPublisherByBroadSearch_fromCache() throws Exception {
        Map<String, Publisher> publisherCache = new ConcurrentHashMap<>();
        publisherCache.put("테스트출판사", testPublisher);
        setPrivateField(dataInitializer, "publisherCache", publisherCache);

        Method method = DataInitializer.class.getDeclaredMethod("findPublisherByBroadSearch", String.class);
        method.setAccessible(true);
        Publisher result = (Publisher) method.invoke(dataInitializer, "테스트출판사");

        assertThat(result).isEqualTo(testPublisher);
    }

    @Test
    @DisplayName("findPublisherByBroadSearch - DB에서 대소문자 무시 검색")
    void findPublisherByBroadSearch_caseInsensitiveFromDb() throws Exception {
        Publisher testPub = Publisher.builder()
                .id(1L)
                .publisherName("TestPublisher")
                .build();
        when(publisherRepository.findAll()).thenReturn(List.of(testPub));

        Method method = DataInitializer.class.getDeclaredMethod("findPublisherByBroadSearch", String.class);
        method.setAccessible(true);
        Publisher result = (Publisher) method.invoke(dataInitializer, "testpublisher");

        assertThat(result.getPublisherName()).isEqualTo("TestPublisher");
    }

    @Test
    @DisplayName("findPublisherByBroadSearch - Unknown 반환")
    void findPublisherByBroadSearch_returnUnknown() throws Exception {
        Publisher unknown = Publisher.builder()
                .id(999L)
                .publisherName("Unknown")
                .build();

        Map<String, Publisher> publisherCache = new ConcurrentHashMap<>();
        publisherCache.put("unknown", unknown);
        setPrivateField(dataInitializer, "publisherCache", publisherCache);

        when(publisherRepository.findAll()).thenReturn(new ArrayList<>());

        Method method = DataInitializer.class.getDeclaredMethod("findPublisherByBroadSearch", String.class);
        method.setAccessible(true);
        Publisher result = (Publisher) method.invoke(dataInitializer, "존재하지않는출판사");

        assertThat(result.getPublisherName()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("findPublisherByBroadSearch - Unknown도 없으면 DB 조회 후 예외")
    void findPublisherByBroadSearch_throwsWhenUnknownNotFound() throws Exception {
        when(publisherRepository.findAll()).thenReturn(new ArrayList<>());
        when(publisherRepository.findByPublisherName("Unknown"))
                .thenReturn(Optional.empty());

        Method method = DataInitializer.class.getDeclaredMethod("findPublisherByBroadSearch", String.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> method.invoke(dataInitializer, "존재하지않는출판사"))
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("ensureSpecialValues - Unknown 출판사 생성")
    void ensureSpecialValues_createUnknown() throws Exception {
        Publisher unknown = Publisher.builder()
                .id(999L)
                .publisherName("Unknown")
                .build();

        when(publisherRepository.findByPublisherName("Unknown"))
                .thenReturn(Optional.empty());
        when(publisherRepository.saveAndFlush(any(Publisher.class)))
                .thenReturn(unknown);

        Method method = DataInitializer.class.getDeclaredMethod("ensureSpecialValues");
        method.setAccessible(true);
        method.invoke(dataInitializer);

        verify(publisherRepository).saveAndFlush(any(Publisher.class));
    }

    @Test
    @DisplayName("ensureSpecialValues - Unknown이 이미 존재")
    void ensureSpecialValues_unknownExists() throws Exception {
        Publisher unknown = Publisher.builder()
                .id(999L)
                .publisherName("Unknown")
                .build();

        when(publisherRepository.findByPublisherName("Unknown"))
                .thenReturn(Optional.of(unknown));

        Method method = DataInitializer.class.getDeclaredMethod("ensureSpecialValues");
        method.setAccessible(true);
        method.invoke(dataInitializer);

        verify(publisherRepository, never()).saveAndFlush(any(Publisher.class));
    }

    @Test
    @DisplayName("ensureSpecialValues - 캐시에 이미 존재하면 스킵")
    void ensureSpecialValues_skipWhenInCache() throws Exception {
        Publisher unknown = Publisher.builder()
                .id(999L)
                .publisherName("Unknown")
                .build();

        Map<String, Publisher> publisherCache = new ConcurrentHashMap<>();
        publisherCache.put("unknown", unknown);
        setPrivateField(dataInitializer, "publisherCache", publisherCache);

        Method method = DataInitializer.class.getDeclaredMethod("ensureSpecialValues");
        method.setAccessible(true);
        method.invoke(dataInitializer);

        verify(publisherRepository, never()).findByPublisherName(anyString());
    }

    @Test
    @DisplayName("ensureSpecialValues - 저장 실패 시 재조회")
    void ensureSpecialValues_retryOnSaveFailure() throws Exception {
        Publisher unknown = Publisher.builder()
                .id(999L)
                .publisherName("Unknown")
                .build();

        when(publisherRepository.findByPublisherName("Unknown"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(unknown));
        when(publisherRepository.saveAndFlush(any(Publisher.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate"));

        Method method = DataInitializer.class.getDeclaredMethod("ensureSpecialValues");
        method.setAccessible(true);
        method.invoke(dataInitializer);

        verify(publisherRepository, times(2)).findByPublisherName("Unknown");
    }

    @Test
    @DisplayName("ensureSpecialValues - 예외 발생 시 처리")
    void ensureSpecialValues_handleException() throws Exception {
        when(publisherRepository.findByPublisherName("Unknown"))
                .thenThrow(new RuntimeException("DB Error"));

        Method method = DataInitializer.class.getDeclaredMethod("ensureSpecialValues");
        method.setAccessible(true);
        method.invoke(dataInitializer);

        verify(publisherRepository).findByPublisherName("Unknown");
    }

    @Test
    @DisplayName("normalizeKey - 키 정규화")
    void normalizeKey_normalization() throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("normalizeKey", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(dataInitializer, "TestKey")).isEqualTo("testkey");
        assertThat(method.invoke(dataInitializer, " Test Key ")).isEqualTo("test key");
        assertThat(method.invoke(dataInitializer, (String) null)).isEqualTo("");
    }

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
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(obj);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object createContributorData(String name, String role) throws Exception {
        Class<?> innerClass = Class.forName("org.nhnacademy.book2onandonbookservice.config.DataInitializer$ContributorData");
        return innerClass.getDeclaredConstructor(String.class, String.class).newInstance(name, role);
    }
}