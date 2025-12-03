package org.nhnacademy.book2onandonbookservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookStatusUpdateRequest;
import org.nhnacademy.book2onandonbookservice.service.book.BookService;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(BookAdminController.class)
class BookAdminControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    BookService bookService;

    @MockitoBean
    ImageUploadService imageUploadService;

    @MockitoBean
    UserHeaderUtil util;

    @Test
    @DisplayName("도서 등록 성공 컨트롤러 - 이미지 포함")
    void createBook() throws Exception {
        BookSaveRequest request = BookSaveRequest.builder()
                .title("Test Book")
                .isbn("1234567890123")
                .priceStandard(10000L)
                .build();

        String reqJson = objectMapper.writeValueAsString(request);
        MockMultipartFile bookPart = new MockMultipartFile("book", "", "application/json", reqJson.getBytes(
                StandardCharsets.UTF_8));
        MockMultipartFile imagePart = new MockMultipartFile("image", "test.jpg", "image/jpeg", "dummy".getBytes());
        given(imageUploadService.uploadBookImage(any())).willReturn("http://minio.test.jpg");
        given(bookService.createBook(any(BookSaveRequest.class))).willReturn(1L);

        mockMvc.perform(multipart("/books")
                        .file(bookPart)
                        .file(imagePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
                .andExpect(status().isCreated());
        verify(bookService).createBook(any(BookSaveRequest.class));

    }

    @Test
    @DisplayName("도서 등록 성공 컨트롤러 - 이미지 포함X")
    void createBook_NotImage() throws Exception {
        BookSaveRequest request = BookSaveRequest.builder()
                .title("Test Book")
                .isbn("1234567890123")
                .priceStandard(10000L)
                .build();

        String reqJson = objectMapper.writeValueAsString(request);
        MockMultipartFile bookPart = new MockMultipartFile("book", "", "application/json", reqJson.getBytes(
                StandardCharsets.UTF_8));
        given(bookService.createBook(any(BookSaveRequest.class))).willReturn(1L);

        mockMvc.perform(multipart("/books")
                .file(bookPart)
                .contentType(MediaType.MULTIPART_FORM_DATA)
        ).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("도서 수정 성공 - 이미지포함")
    void updateBook() throws Exception {
        Long bookId = 1L;
        BookSaveRequest request = BookSaveRequest.builder().title("수정한 타이틀").build();
        String reqJson = objectMapper.writeValueAsString(request);

        MockMultipartFile bookPart = new MockMultipartFile("book", "", "application/json", reqJson.getBytes(
                StandardCharsets.UTF_8));
        MockMultipartFile imagePart = new MockMultipartFile("image", "new.jpg", "image/jpeg", "new-dummy".getBytes());
        given(imageUploadService.uploadBookImage(any())).willReturn("http://minio/new.jpg");
        doNothing().when(bookService).updateBook(eq(bookId), any(BookSaveRequest.class));
        mockMvc.perform(multipart("/books/{bookId}", bookId)
                        .file(bookPart)
                        .file(imagePart)
                        .with(request1 -> {
                            request1.setMethod("PUT");
                            return request1;
                        }))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("도서 삭제 성공")
    void deleteBook() throws Exception {
        Long bookId = 1L;
        doNothing().when(bookService).deleteBook(bookId);

        mockMvc.perform(delete("/books/{bookId}", bookId))
                .andExpect(status().isNoContent());
        verify(bookService).deleteBook(bookId);
    }

    @Test
    @DisplayName("도서 상태 변경 성공")
    void updateBookStatus() throws Exception {
        Long bookId = 1L;
        BookStatusUpdateRequest request = new BookStatusUpdateRequest(BookStatus.SOLD_OUT);
        doNothing().when(bookService).updateBookStatus(bookId, BookStatus.SOLD_OUT);

        mockMvc.perform(patch("/books/{bookId}/status", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

    }
}