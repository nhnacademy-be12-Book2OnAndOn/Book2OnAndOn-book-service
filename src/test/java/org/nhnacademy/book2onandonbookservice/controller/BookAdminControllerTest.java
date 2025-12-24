package org.nhnacademy.book2onandonbookservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookStatusUpdateRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookUpdateRequest;
import org.nhnacademy.book2onandonbookservice.service.book.AladinService;
import org.nhnacademy.book2onandonbookservice.service.book.BookService;
import org.nhnacademy.book2onandonbookservice.service.book.StockService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookAdminControllerTest {

    @Mock
    private BookService bookService;

    @Mock
    private AladinService aladinService;

    @Mock
    private StockService stockService;

    @InjectMocks
    private BookAdminController bookAdminController;

    @Test
    @DisplayName("성공: 도서 조회 (lookupBook)")
    void lookupBook_Success() {
        String isbn = "1234567890";
        BookSaveRequest mockRequest = mock(BookSaveRequest.class);
        when(aladinService.searchBookInfo(isbn)).thenReturn(mockRequest);

        ResponseEntity<BookSaveRequest> response = bookAdminController.lookupBook(isbn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mockRequest);
        verify(aladinService).searchBookInfo(isbn);
    }

    @Test
    @DisplayName("성공: 도서 등록 (createBook)")
    void createBook_Success() {
        BookSaveRequest request = mock(BookSaveRequest.class);
        when(request.getTitle()).thenReturn("Test Book");
        List<MultipartFile> images = Collections.emptyList();
        Long createdBookId = 1L;

        when(bookService.createBook(request, images)).thenReturn(createdBookId);

        ResponseEntity<Void> response = bookAdminController.createBook(request, images);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/books/" + createdBookId));
        verify(bookService).createBook(request, images);
    }

    @Test
    @DisplayName("성공: 도서 수정 (updateBook)")
    void updateBook_Success() {
        Long bookId = 1L;
        BookUpdateRequest request = mock(BookUpdateRequest.class);
        when(request.getTitle()).thenReturn("Updated Title");
        List<MultipartFile> images = Collections.emptyList();

        ResponseEntity<Void> response = bookAdminController.updateBook(bookId, request, images);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(bookService).updateBook(bookId, request, images);
    }

    @Test
    @DisplayName("성공: 도서 썸네일 설정 (updateThumbnail)")
    void updateThumbnail_Success() {
        Long bookId = 1L;
        Long imageId = 10L;

        ResponseEntity<Void> response = bookAdminController.updateThumbnail(bookId, imageId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(bookService).updateThumbnail(bookId, imageId);
    }

    @Test
    @DisplayName("성공: 도서 삭제 (deleteBook)")
    void deleteBook_Success() {
        Long bookId = 1L;

        ResponseEntity<Void> response = bookAdminController.deleteBook(bookId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(bookService).deleteBook(bookId);
    }

    @Test
    @DisplayName("성공: 도서 상태 변경 (updateBookStatus)")
    void updateBookStatus_Success() {
        Long bookId = 1L;
        BookStatusUpdateRequest request = mock(BookStatusUpdateRequest.class);
        when(request.getStatus()).thenReturn(BookStatus.ON_SALE);

        ResponseEntity<Void> response = bookAdminController.updateBookStatus(bookId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(bookService).updateBookStatus(bookId, BookStatus.ON_SALE);
    }

    @Test
    @DisplayName("성공: 도서 개수 반환 (getBookCount)")
    void getBookCount_Success() {
        long count = 100L;
        when(bookService.getBookCount()).thenReturn(count);

        ResponseEntity<Long> response = bookAdminController.getBookCount();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(count);
        verify(bookService).getBookCount();
    }

    @Test
    @DisplayName("성공: 재고 동기화 (syncStock)")
    void syncStock_Success() {
        Long bookId = 1L;

        ResponseEntity<String> response = bookAdminController.syncStock(bookId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("동기화 완료");
        verify(stockService).synchronizeStock(bookId);
    }

    @Test
    @DisplayName("실패: 서비스 계층에서 예외 발생 시 전파 (createBook)")
    void createBook_Fail_ServiceException() {
        BookSaveRequest request = mock(BookSaveRequest.class);
        List<MultipartFile> images = Collections.emptyList();

        when(bookService.createBook(any(), any())).thenThrow(new RuntimeException("Service Error"));

        assertThatThrownBy(() -> bookAdminController.createBook(request, images))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Service Error");
    }
}