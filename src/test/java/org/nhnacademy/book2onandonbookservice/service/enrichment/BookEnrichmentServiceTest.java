package org.nhnacademy.book2onandonbookservice.service.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.domain.EnrichmentStatus;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookEnrichmentTask;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.repository.BookContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookEnrichmentTaskRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookPublisherRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.ContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookEnrichmentServiceTest {

    @InjectMocks
    private BookEnrichmentService bookEnrichmentService;

    @Mock private BookRepository bookRepository;
    @Mock private AladinApiClient aladinApiClient;
    @Mock private CategoryEnrichmentService categoryService;
    @Mock private TagEnrichmentService tagService;
    @Mock private BookEnrichmentTaskRepository taskRepository;
    @Mock private PublisherRepository publisherRepository;
    @Mock private ContributorRepository contributorRepository;
    @Mock private BookPublisherRepository bookPublisherRepository;
    @Mock private BookContributorRepository bookContributorRepository;
    @Mock private BookSearchIndexService bookSearchIndexService;
    @Mock private ImageUploadService imageUploadService;

    private BookEnrichmentTask createTask() {
        try {
            Constructor<BookEnrichmentTask> constructor = BookEnrichmentTask.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("BookEnrichmentTask 생성 실패", e);
        }
    }

    @Test
    @DisplayName("enrichBookData: 책을 찾을 수 없는 경우 예외 발생")
    void enrichBookData_BookNotFound() {
        BookEnrichmentTask task = createTask();
        ReflectionTestUtils.setField(task, "bookId", 1L);

        when(bookRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookEnrichmentService.enrichBookData(task))
                .isInstanceOf(NotFoundBookException.class);
    }

    @Test
    @DisplayName("enrichBookData: 삭제된 책은 처리하지 않고 종료")
    void enrichBookData_DeletedBook_ReturnsEarly() {
        BookEnrichmentTask task = createTask();
        ReflectionTestUtils.setField(task, "bookId", 1L);

        Book book = Book.builder().build();
        book.setStatus(BookStatus.BOOK_DELETED);

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        bookEnrichmentService.enrichBookData(task);

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("enrichBookData: 알라딘/AI 성공 시 상태 업데이트 및 재색인 수행")
    void enrichBookData_Success() throws JsonProcessingException {
        // given
        BookEnrichmentTask task = createTask();
        ReflectionTestUtils.setField(task, "bookId", 1L);
        ReflectionTestUtils.setField(task, "aladinStatus", EnrichmentStatus.PENDING);
        ReflectionTestUtils.setField(task, "aiStatus", EnrichmentStatus.PENDING);

        Book book = Book.builder().build();
        ReflectionTestUtils.setField(book, "id", 1L);
        book.setIsbn("1234567890123");
        book.setBookPublishers(new HashSet<>());
        book.setBookContributors(new HashSet<>());
        book.setImages(new HashSet<>());

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        // Aladin Mock
        AladinApiResponse.Item aladinItem = new AladinApiResponse.Item();
        ReflectionTestUtils.setField(aladinItem, "title", "New Long Title");
        ReflectionTestUtils.setField(aladinItem, "priceStandard", 20000L);
        ReflectionTestUtils.setField(aladinItem, "publisher", "Test Publisher");
        ReflectionTestUtils.setField(aladinItem, "author", "Author (Role)");
        ReflectionTestUtils.setField(aladinItem, "cover", "http://image.url");

        when(aladinApiClient.searchByIsbn(anyString())).thenReturn(aladinItem);

        // Publisher Mock
        when(publisherRepository.findByPublisherName("Test Publisher"))
                .thenReturn(Optional.empty());
        when(publisherRepository.save(any(Publisher.class))).thenAnswer(i -> i.getArgument(0));
        when(bookPublisherRepository.existsByBookAndPublisher(any(), any())).thenReturn(false);

        // Contributor Mock
        when(contributorRepository.findByContributorName("Author"))
                .thenReturn(Optional.empty());
        when(contributorRepository.save(any(Contributor.class))).thenAnswer(i -> i.getArgument(0));
        when(bookContributorRepository.existsByBookAndContributorAndRoleType(any(), any(), anyString()))
                .thenReturn(false);

        // Image Mock
        when(imageUploadService.uploadImageFromUrl(anyString())).thenReturn("s3/path.jpg");

        bookEnrichmentService.enrichBookData(task);

        assertThat(task.getAladinStatus()).isEqualTo(EnrichmentStatus.DONE);
        assertThat(task.getAiStatus()).isEqualTo(EnrichmentStatus.DONE);

        assertThat(book.getTitle()).isEqualTo("New Long Title");
        assertThat(book.getPriceSales()).isEqualTo(18000L); // 20000 * 0.9 = 18000
        assertThat(book.getThumbnail()).isEqualTo("s3/path.jpg");

        verify(categoryService).enrich(eq(book), eq(aladinItem));
        verify(tagService).enrich(eq(book), anyString(), any(), anyString());
        verify(bookSearchIndexService).index(book);
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("enrichBookData: 알라딘 정보 없음 (NOT_FOUND)")
    void enrichBookData_AladinNotFound() throws JsonProcessingException {
        BookEnrichmentTask task = createTask();
        ReflectionTestUtils.setField(task, "bookId", 1L);
        ReflectionTestUtils.setField(task, "aladinStatus", EnrichmentStatus.PENDING);
        ReflectionTestUtils.setField(task, "aiStatus", EnrichmentStatus.DONE);

        Book book = Book.builder().build();
        book.setIsbn("123");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(aladinApiClient.searchByIsbn("123")).thenReturn(null);

        bookEnrichmentService.enrichBookData(task);

        assertThat(task.getAladinStatus()).isEqualTo(EnrichmentStatus.NOT_FOUND);
        verify(bookSearchIndexService).index(book); // NOT_FOUND도 재색인 대상
    }

    @Test
    @DisplayName("enrichBookData: 알라딘 API 오류 (FAILED)")
    void enrichBookData_AladinFailed() throws JsonProcessingException {
        BookEnrichmentTask task = createTask();
        ReflectionTestUtils.setField(task, "bookId", 1L);
        ReflectionTestUtils.setField(task, "aladinStatus", EnrichmentStatus.PENDING);
        ReflectionTestUtils.setField(task, "aiStatus", EnrichmentStatus.DONE);

        Book book = Book.builder().build();
        book.setIsbn("123");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        doThrow(new RuntimeException("API Error")).when(aladinApiClient).searchByIsbn("123");

        bookEnrichmentService.enrichBookData(task);

        assertThat(task.getAladinStatus()).isEqualTo(EnrichmentStatus.FAILED);
        verify(bookSearchIndexService, never()).index(book);
    }

    @Test
    @DisplayName("enrichBookData: AI 생성 실패 (FAILED)")
    void enrichBookData_AiFailed() {
        BookEnrichmentTask task = createTask();
        ReflectionTestUtils.setField(task, "bookId", 1L);
        ReflectionTestUtils.setField(task, "aladinStatus", EnrichmentStatus.DONE);
        ReflectionTestUtils.setField(task, "aiStatus", EnrichmentStatus.PENDING);

        Book book = Book.builder().build();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        doThrow(new RuntimeException("AI Error")).when(tagService).enrich(any(), any(), any(), any());

        bookEnrichmentService.enrichBookData(task);

        assertThat(task.getAiStatus()).isEqualTo(EnrichmentStatus.FAILED);
    }

    @Test
    @DisplayName("shouldProcess: 재시도 횟수 제한 테스트")
    void shouldProcess_RetryLogic() throws JsonProcessingException {
        BookEnrichmentTask task = createTask();
        ReflectionTestUtils.setField(task, "bookId", 1L);
        ReflectionTestUtils.setField(task, "aladinStatus", EnrichmentStatus.FAILED);
        ReflectionTestUtils.setField(task, "aladinRetryCount", 2);
        ReflectionTestUtils.setField(task, "aiStatus", EnrichmentStatus.DONE);

        Book book = Book.builder().build();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(aladinApiClient.searchByIsbn(any())).thenReturn(null);

        bookEnrichmentService.enrichBookData(task);
        verify(aladinApiClient).searchByIsbn(any());

        ReflectionTestUtils.setField(task, "aladinRetryCount", 3);
        bookEnrichmentService.enrichBookData(task);
        verify(aladinApiClient, times(1)).searchByIsbn(any());
    }

    @Test
    @DisplayName("enrichBasicInfo: 제목이 기존보다 짧으면 업데이트 안 함 & 날짜 파싱 오류 처리")
    void enrichBasicInfo_Logic() throws JsonProcessingException {
        BookEnrichmentTask task = createTask();
        ReflectionTestUtils.setField(task, "bookId", 1L);
        ReflectionTestUtils.setField(task, "aladinStatus", EnrichmentStatus.PENDING);
        ReflectionTestUtils.setField(task, "aiStatus", EnrichmentStatus.DONE);

        Book book = Book.builder().build();
        book.setTitle("Very Long Existing Title");
        book.setBookPublishers(new HashSet<>());
        book.setBookContributors(new HashSet<>());
        book.setImages(new HashSet<>());

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        AladinApiResponse.Item item = new AladinApiResponse.Item();
        ReflectionTestUtils.setField(item, "title", "Short");
        ReflectionTestUtils.setField(item, "pubDate", "Invalid Date");

        when(aladinApiClient.searchByIsbn(any())).thenReturn(item);

        bookEnrichmentService.enrichBookData(task);

        assertThat(book.getTitle()).isEqualTo("Very Long Existing Title");
        assertThat(book.getPublishDate()).isNull();
    }

    @Test
    @DisplayName("enrichContributors: 다양한 형식의 저자 문자열 파싱")
    void enrichContributors_Parsing() throws JsonProcessingException {
        BookEnrichmentTask task = createTask();
        ReflectionTestUtils.setField(task, "bookId", 1L);
        ReflectionTestUtils.setField(task, "aladinStatus", EnrichmentStatus.PENDING);
        ReflectionTestUtils.setField(task, "aiStatus", EnrichmentStatus.DONE);

        Book book = Book.builder().build();
        book.setBookPublishers(new HashSet<>());
        book.setBookContributors(new HashSet<>());
        book.setImages(new HashSet<>());

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        AladinApiResponse.Item item = new AladinApiResponse.Item();
        ReflectionTestUtils.setField(item, "author", "Kim (Author), Lee");

        when(aladinApiClient.searchByIsbn(any())).thenReturn(item);

        when(contributorRepository.findByContributorName(anyString())).thenReturn(Optional.empty());
        when(contributorRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        bookEnrichmentService.enrichBookData(task);

        verify(bookContributorRepository, times(2)).save(any(BookContributor.class));
    }

    @Test
    @DisplayName("enrichThumbnail: 이미 썸네일이 있으면 업데이트 안 함")
    void enrichThumbnail_ExistingThumbnail() throws JsonProcessingException {
        BookEnrichmentTask task = createTask();
        ReflectionTestUtils.setField(task, "bookId", 1L);
        ReflectionTestUtils.setField(task, "aladinStatus", EnrichmentStatus.PENDING);
        ReflectionTestUtils.setField(task, "aiStatus", EnrichmentStatus.DONE);

        Book book = Book.builder().build();
        book.setThumbnail("existing.jpg");
        book.setBookPublishers(new HashSet<>());
        book.setBookContributors(new HashSet<>());

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        AladinApiResponse.Item item = new AladinApiResponse.Item();
        ReflectionTestUtils.setField(item, "cover", "http://new.jpg");

        when(aladinApiClient.searchByIsbn(any())).thenReturn(item);

        bookEnrichmentService.enrichBookData(task);

        verify(imageUploadService, never()).uploadImageFromUrl(anyString());
    }

    @Test
    @DisplayName("재색인 실패 시 로그만 남기고 진행")
    void reindex_Fail_LogOnly() throws JsonProcessingException {
        BookEnrichmentTask task = createTask();
        ReflectionTestUtils.setField(task, "bookId", 1L);
        ReflectionTestUtils.setField(task, "aladinStatus", EnrichmentStatus.PENDING);
        ReflectionTestUtils.setField(task, "aiStatus", EnrichmentStatus.DONE);

        Book book = Book.builder().build();
        book.setBookPublishers(new HashSet<>());
        book.setBookContributors(new HashSet<>());
        book.setImages(new HashSet<>());

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(aladinApiClient.searchByIsbn(any())).thenReturn(new AladinApiResponse.Item());

        doThrow(new RuntimeException("Index Fail")).when(bookSearchIndexService).index(book);

        bookEnrichmentService.enrichBookData(task);

        assertThat(task.getAladinStatus()).isEqualTo(EnrichmentStatus.DONE);
    }
}