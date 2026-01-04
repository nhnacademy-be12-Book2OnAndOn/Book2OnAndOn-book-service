package org.nhnacademy.book2onandonbookservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.lang.reflect.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.*;
import org.nhnacademy.book2onandonbookservice.repository.*;
import org.nhnacademy.book2onandonbookservice.service.BookBatchService;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock private BookRepository bookRepository;
    @Mock private ImageUploadService imageUploadService;
    @Mock private BookEnrichmentTaskRepository taskRepository;
    @Mock private PublisherRepository publisherRepository;
    @Mock private ContributorRepository contributorRepository;
    @Mock private BookBatchService bookBatchService;
    @Mock private ApplicationArguments applicationArguments;
    @Mock private Resource resource;

    @InjectMocks private DataInitializer dataInitializer;

    private Contributor testContributor;

    @BeforeEach
    void setUp() {
        testContributor = Contributor.builder().id(1L).contributorName("홍길동").build();
    }

    @Test
    @DisplayName("run - 데이터 존재 시 스킵")
    void run_skipWhenDataExists() throws Exception {
        when(bookRepository.count()).thenReturn(100L);
        dataInitializer.run(applicationArguments);
        verify(bookRepository).count();
        verify(taskRepository).initTasksFromBook();
    }

    @Test
    @DisplayName("run - 데이터 없을 때 초기화")
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
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", " ", ""})
    @DisplayName("safeGet - 유효하지 않은 입력 처리 통합")
    void safeGet_invalidValues(String input) throws Exception {
        String[] row = {input, "value2"};
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("col1", 0);
        String result = invokeSafeGet(row, headerMap, "col1");
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {"invalid-date", "''", "null"}, nullValues = "null")
    @DisplayName("parseDate - 실패 시 현재 날짜 반환 통합")
    void parseDate_fallbackToNow(String input) throws Exception {
        LocalDate result = invokeParseDate(input);
        assertThat(result).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("processCsvFile - IOException 발생 시 처리")
    void processCsvFile_ioException() throws IOException {
        when(resource.getInputStream()).thenThrow(new IOException("File read error"));
        when(resource.getFilename()).thenReturn("error.csv");
        dataInitializer.processCsvFile(resource);
        verify(bookBatchService, never()).saveBooksInBatch(anyList());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "'홍길동(지은이)', 1",
            "'홍길동 외', 1",
            "'홍길동 외 2명', 1",
            "'', 0"
    })
    @DisplayName("parseAndAddContributors - 작가 파싱 통합")
    void parseAndAddContributors_parameterized(String authorStr, int expectedSize) throws Exception {
        Book book = Book.builder().bookContributors(new HashSet<>()).build();
        lenient().when(contributorRepository.save(any(Contributor.class))).thenReturn(testContributor);
        invokeParseAndAddContributors(book, authorStr);
        assertThat(book.getBookContributors()).hasSize(expectedSize);
    }

    @Test
    @DisplayName("convertToBook - 필수값 누락 시 null")
    void convertToBook_nullWhenMissingRequired() throws Exception {
        String[] row = {"", "", "출판사"};
        Map<String, Integer> headerMap = createHeaderMap();
        Book result = invokeConvertToBook(row, headerMap);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("saveBatchSafe - 예외 발생 시 로그 출력 및 계속 진행")
    void saveBatchSafe_generalException() throws Exception {
        List<Book> batch = List.of(Book.builder().build());
        doThrow(new RuntimeException("Save failed")).when(bookBatchService).saveBooksInBatch(anyList());
        Method method = DataInitializer.class.getDeclaredMethod("saveBatchSafe", List.class);
        method.setAccessible(true);
        method.invoke(dataInitializer, batch);
        verify(bookBatchService).saveBooksInBatch(batch);
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

    private String invokeSafeGet(String[] row, Map<String, Integer> headerMap, String... keys) throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("safeGet", String[].class, Map.class, String[].class);
        method.setAccessible(true);
        return (String) method.invoke(dataInitializer, row, headerMap, keys);
    }

    private LocalDate invokeParseDate(String dateStr) throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("parseDate", String.class);
        method.setAccessible(true);
        return (LocalDate) method.invoke(dataInitializer, dateStr);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}