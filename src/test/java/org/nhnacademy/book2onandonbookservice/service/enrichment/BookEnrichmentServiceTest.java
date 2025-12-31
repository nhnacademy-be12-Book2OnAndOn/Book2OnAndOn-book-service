//package org.nhnacademy.book2onandonbookservice.service.enrichment;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.verify;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import java.util.Optional;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
//import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
//import org.nhnacademy.book2onandonbookservice.domain.EnrichmentStatus;
//import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
//import org.nhnacademy.book2onandonbookservice.entity.Book;
//import org.nhnacademy.book2onandonbookservice.entity.BookEnrichmentTask;
//import org.nhnacademy.book2onandonbookservice.entity.Publisher;
//import org.nhnacademy.book2onandonbookservice.repository.BookContributorRepository;
//import org.nhnacademy.book2onandonbookservice.repository.BookEnrichmentTaskRepository;
//import org.nhnacademy.book2onandonbookservice.repository.BookPublisherRepository;
//import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
//import org.nhnacademy.book2onandonbookservice.repository.ContributorRepository;
//import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
//
//import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
//import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
//
//@ExtendWith(MockitoExtension.class)
//class BookEnrichmentServiceTest {
//
//    @InjectMocks
//    private BookEnrichmentService bookEnrichmentService;
//
//    @Mock private BookRepository bookRepository;
//    @Mock private AladinApiClient aladinApiClient;
//    @Mock private CategoryEnrichmentService categoryService;
//    @Mock private TagEnrichmentService tagService;
//    @Mock private ImageUploadService imageUploadService;
//    @Mock private BookEnrichmentTaskRepository taskRepository;
//    @Mock private BookSearchIndexService bookSearchIndexService;
//
//    @Mock private PublisherRepository publisherRepository;
//    @Mock private ContributorRepository contributorRepository;
//    @Mock private BookPublisherRepository bookPublisherRepository;
//    @Mock private BookContributorRepository bookContributorRepository;
//
//    private Book testBook;
//    private BookEnrichmentTask testTask;
//    private AladinApiResponse.Item aladinItem;
//
//    @BeforeEach
//    void setUp() {
//        testBook = Book.builder()
//                .id(1L)
//                .isbn("9788900000000")
//                .title("기존 제목")
//                .description("기존 설명")
//                .status(BookStatus.ON_SALE)
//                .build();
//
//        testTask = BookEnrichmentTask.builder()
//                .bookId(1L)
//                .aladinStatus(EnrichmentStatus.PENDING)
//                .aladinRetryCount(0)
//                .aiStatus(EnrichmentStatus.PENDING)
//                .aiRetryCount(0)
//                .build();
//
//        aladinItem = new AladinApiResponse.Item();
//    }
//
//
//    @Test
//    @DisplayName("책이 존재하지 않으면 Task에 실패 상태 기록")
//    void enrichBookData_NotFound() {
//        given(bookRepository.findById(1L)).willReturn(Optional.empty());
//
//        // 예외를 던지지 않고 내부에서 처리
//        bookEnrichmentService.enrichBookDataWithStatusUpdate(testTask);
//
//        // Task에 실패 상태가 기록되어야 함
//        assertThat(testTask.getAladinStatus()).isEqualTo(EnrichmentStatus.FAILED);
//        assertThat(testTask.getAiStatus()).isEqualTo(EnrichmentStatus.FAILED);
//        assertThat(testTask.getAladinFailReason()).contains("Book not found");
//        assertThat(testTask.getAiFailReason()).contains("Book not found");
//
//        // finally에서 saveAndFlush 호출
//        verify(taskRepository).saveAndFlush(testTask);
//    }
//
//    @Test
//    @DisplayName("삭제된 책(BOOK_DELETED)이면 아무 작업도 안 하고 종료")
//    void enrichBookData_DeletedBook() throws JsonProcessingException {
//        testBook.setStatus(BookStatus.BOOK_DELETED);
//        given(bookRepository.findById(1L)).willReturn(Optional.of(testBook));
//
//        bookEnrichmentService.enrichBookDataWithStatusUpdate(testTask);
//
//        verify(aladinApiClient, never()).searchByIsbn(anyString());
//        verify(tagService, never()).enrich(any(), any(), any(), any());
//        // finally에서 항상 저장
//        verify(taskRepository).saveAndFlush(testTask);
//    }
//
//
//    @Test
//    @DisplayName("알라딘: 성공 시 Task 상태 DONE으로 변경 및 데이터 보강")
//    void processAladin_Success() throws JsonProcessingException {
//        given(bookRepository.findById(1L)).willReturn(Optional.of(testBook));
//
//        aladinItem = mock(AladinApiResponse.Item.class);
//
//        given(aladinItem.getPublisher()).willReturn("테스트 출판사");
//        given(aladinItem.getAuthor()).willReturn("테스트 작가");
//        given(aladinItem.getCover()).willReturn("http://image.url");
//
//        given(aladinApiClient.searchByIsbn(anyString())).willReturn(aladinItem);
//
//        given(publisherRepository.findByPublisherName(any()))
//                .willReturn(Optional.of(Publisher.builder().publisherName("테스트 출판사").build()));
//
//        // bookPublisherRepository.existsByBookAndPublisher가 호출될 때 false 반환
//        given(bookPublisherRepository.existsByBookAndPublisher(any(), any())).willReturn(false);
//
//        bookEnrichmentService.enrichBookDataWithStatusUpdate(testTask);
//
//        assertThat(testTask.getAladinStatus()).isEqualTo(EnrichmentStatus.DONE);
//        assertThat(testTask.getAladinFailReason()).isNull();
//
//        verify(categoryService).enrich(any(Book.class), any(AladinApiResponse.Item.class));
//        verify(publisherRepository).findByPublisherName(any());
//        verify(taskRepository).saveAndFlush(testTask);
//    }
//
//    @Test
//    @DisplayName("알라딘: 검색 결과 없음(Null)이면 Task 상태 NOT_FOUND로 변경")
//    void processAladin_NotFound() throws JsonProcessingException {
//        given(bookRepository.findById(1L)).willReturn(Optional.of(testBook));
//        given(aladinApiClient.searchByIsbn(anyString())).willReturn(null); // 못 찾음
//
//        bookEnrichmentService.enrichBookDataWithStatusUpdate(testTask);
//
//        assertThat(testTask.getAladinStatus()).isEqualTo(EnrichmentStatus.NOT_FOUND);
//        verify(categoryService, never()).enrich(any(), any());
//        verify(taskRepository).saveAndFlush(testTask);
//    }
//
//    @Test
//    @DisplayName("알라딘: API 예외 발생 시 Task 상태 FAILED로 변경 및 재시도 카운트 증가")
//    void processAladin_Exception() throws JsonProcessingException {
//        given(bookRepository.findById(1L)).willReturn(Optional.of(testBook));
//        given(aladinApiClient.searchByIsbn(anyString())).willThrow(new RuntimeException("API Error"));
//
//        bookEnrichmentService.enrichBookDataWithStatusUpdate(testTask);
//
//        assertThat(testTask.getAladinStatus()).isEqualTo(EnrichmentStatus.FAILED);
//        assertThat(testTask.getAladinRetryCount()).isEqualTo(1);
//        assertThat(testTask.getAladinFailReason()).contains("API Error");
//        verify(taskRepository).saveAndFlush(testTask);
//    }
//
//    @Test
//    @DisplayName("알라딘: 이미 DONE 상태면 API 호출 건너뜀")
//    void processAladin_Skip_If_Done() throws JsonProcessingException {
//        testTask = BookEnrichmentTask.builder()
//                .bookId(1L)
//                .aladinStatus(EnrichmentStatus.DONE)
//                .aiStatus(EnrichmentStatus.PENDING)
//                .build();
//
//        given(bookRepository.findById(1L)).willReturn(Optional.of(testBook));
//
//        bookEnrichmentService.enrichBookDataWithStatusUpdate(testTask);
//
//        verify(aladinApiClient, never()).searchByIsbn(anyString());
//        verify(tagService).enrich(any(), any(), any(), any());
//        verify(taskRepository).saveAndFlush(testTask);
//    }
//
//    @Test
//    @DisplayName("알라딘: FAILED 상태지만 재시도 횟수(3회) 초과 시 건너뜀")
//    void processAladin_Skip_If_RetryMax() throws JsonProcessingException {
//        testTask = BookEnrichmentTask.builder()
//                .bookId(1L)
//                .aladinStatus(EnrichmentStatus.FAILED)
//                .aladinRetryCount(3)
//                .aiStatus(EnrichmentStatus.PENDING)
//                .build();
//
//        given(bookRepository.findById(1L)).willReturn(Optional.of(testBook));
//
//        bookEnrichmentService.enrichBookDataWithStatusUpdate(testTask);
//
//        verify(aladinApiClient, never()).searchByIsbn(anyString());
//        verify(taskRepository).saveAndFlush(testTask);
//    }
//
//
//
//    @Test
//    @DisplayName("AI: 성공 시 Task 상태 DONE으로 변경")
//    void processAi_Success() {
//
//        testTask = BookEnrichmentTask.builder()
//                .bookId(1L)
//                .aladinStatus(EnrichmentStatus.DONE)
//                .aiStatus(EnrichmentStatus.PENDING)
//                .build();
//
//        given(bookRepository.findById(1L)).willReturn(Optional.of(testBook));
//
//        bookEnrichmentService.enrichBookDataWithStatusUpdate(testTask);
//
//        verify(tagService).enrich(any(), any(), any(), any());
//        assertThat(testTask.getAiStatus()).isEqualTo(EnrichmentStatus.DONE);
//        verify(taskRepository).saveAndFlush(testTask);
//    }
//
//    @Test
//    @DisplayName("AI: 예외 발생 시 Task 상태 FAILED로 변경")
//    void processAi_Exception(){
//        testTask = BookEnrichmentTask.builder()
//                .bookId(1L)
//                .aladinStatus(EnrichmentStatus.DONE)
//                .aiStatus(EnrichmentStatus.PENDING)
//                .build();
//
//        given(bookRepository.findById(1L)).willReturn(Optional.of(testBook));
//
//        org.mockito.Mockito.doThrow(new RuntimeException("AI Fail"))
//                .when(tagService).enrich(any(), any(), any(), any());
//
//        bookEnrichmentService.enrichBookDataWithStatusUpdate(testTask);
//
//        assertThat(testTask.getAiStatus()).isEqualTo(EnrichmentStatus.FAILED);
//        assertThat(testTask.getAiRetryCount()).isEqualTo(1);
//        assertThat(testTask.getAiFailReason()).contains("AI Fail");
//        verify(taskRepository).saveAndFlush(testTask);
//    }
//
//    @Test
//    @DisplayName("통합: 알라딘은 실패하고 AI는 성공하는 경우 각각 상태 반영 확인")
//    void process_Mixed_Status() throws JsonProcessingException {
//        given(bookRepository.findById(1L)).willReturn(Optional.of(testBook));
//
//        given(aladinApiClient.searchByIsbn(anyString())).willThrow(new RuntimeException("Aladin Fail"));
//
//        bookEnrichmentService.enrichBookDataWithStatusUpdate(testTask);
//
//        assertThat(testTask.getAladinStatus()).isEqualTo(EnrichmentStatus.FAILED);
//        assertThat(testTask.getAiStatus()).isEqualTo(EnrichmentStatus.DONE);
//
//        verify(taskRepository).saveAndFlush(testTask);
//    }
//
//    @Test
//    @DisplayName("DB 조회 중 예외 발생 시에도 상태 저장")
//    void enrichBookData_Exception_StillSaves() {
//        given(bookRepository.findById(1L)).willThrow(new RuntimeException("DB Error"));
//
//        // 예외가 catch되어 더 이상 밖으로 전파되지 않음
//        bookEnrichmentService.enrichBookDataWithStatusUpdate(testTask);
//
//        // finally에서 saveAndFlush 호출
//        verify(taskRepository).saveAndFlush(testTask);
//    }
//}