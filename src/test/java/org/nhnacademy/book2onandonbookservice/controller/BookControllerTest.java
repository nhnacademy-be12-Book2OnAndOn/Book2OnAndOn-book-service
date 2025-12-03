package org.nhnacademy.book2onandonbookservice.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nhnacademy.book2onandonbookservice.dto.book.BookDetailResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.service.book.BookLikeService;
import org.nhnacademy.book2onandonbookservice.service.book.BookLikeService.BookLikeToggleResult;
import org.nhnacademy.book2onandonbookservice.service.book.BookService;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(BookController.class)
class BookControllerTest {
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

    @MockitoBean
    BookLikeService bookLikeService;


    @Test
    @DisplayName("카테고리 목록 조회")
    void getCategory() throws Exception {
        List<CategoryDto> categoryDtos = List.of(CategoryDto.builder().id(1L).name("DDDD").build());
        given(bookService.getCategories()).willReturn(categoryDtos);

        mockMvc.perform(get("/books/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("DDDD"));
    }

    @Test
    @DisplayName("도서 목록 조회")
    void getBooks() throws Exception {
        BookListResponse response = BookListResponse.builder().id(1L).title("Book").build();
        Page<BookListResponse> page = new PageImpl<>(List.of(response));

        given(bookService.getBooks(any(BookSearchCondition.class), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/books")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Book"));
    }

    @Test
    @DisplayName("도서 상세 조회")
    void getBookDetail() throws Exception {
        Long bookId = 1L;
        Long userId = 100L;
        String guestId = "guest-123";
        BookDetailResponse response = BookDetailResponse.builder().id(bookId).title("디테일한 Book").build();

        given(util.getUserId()).willReturn(userId);
        given(util.getGuestId()).willReturn(guestId);
        given(bookService.getBookDetail(bookId, userId, guestId)).willReturn(response);

        mockMvc.perform(get("/books/" + bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("디테일한 Book"));
    }

    @Test
    @DisplayName("베스트 셀러 조회")
    void getBestSellers() throws Exception {
        String period = "DAILY";
        List<BookListResponse> responses = List.of(BookListResponse.builder().id(1L).title("베스트 셀러를 조회해보자!").build());
        given(bookService.getBestsellers(period)).willReturn(responses);

        mockMvc.perform(get("/books/bestsellers").param("period", period))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("베스트 셀러를 조회해보자!"));
    }

    @Test
    @DisplayName("신간 도서 조회")
    void getNewArrivals() throws Exception {
        Page<BookListResponse> page = new PageImpl<>(
                List.of(BookListResponse.builder().id(1L).title("신간 조회를 해부자!").build()));
        given(bookService.getNewArrivals(any(), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/books/new-arrivals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("신간 조회를 해부자!"));
    }

    @Test
    @DisplayName("인기 도서 조회")
    void getPopularBooks() throws Exception {
        Page<BookListResponse> page = new PageImpl<>(
                List.of(BookListResponse.builder().id(1L).title("인기도서를 조회 해보자!").build()));
        given(bookService.getPopularBooks(any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/books/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("인기도서를 조회 해보자!"));
    }

    @Test
    @DisplayName("최근 본 상품 조회 - 로그인 유저 버전")
    void getRecentViews_user() throws Exception {
        Long userId = 1L;
        given(util.getUserId()).willReturn(userId);
        given(util.getGuestId()).willReturn(null);

        List<BookListResponse> responses = List.of(BookListResponse.builder().id(1L).title("최근 본 유저").build());
        given(bookService.getRecentViews(userId, null)).willReturn(responses);

        mockMvc.perform(get("/books/recent-views"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("최근 본 유저"));
    }

    @Test
    @DisplayName("최근 본 상품 조회 - 비회원도 없고 유저도 없고")
    void getRecentViews_NoGuestAndNoUser() throws Exception {
        given(util.getUserId()).willReturn(null);
        given(util.getGuestId()).willReturn(null);

        mockMvc.perform(get("/books/recent-views"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("최근 본 상품 조회 - 비회원")
    void getRecentViews_guest() throws Exception {
        String guestId = "UUid";
        given(util.getUserId()).willReturn(null);
        given(util.getGuestId()).willReturn(guestId);

        List<BookListResponse> responses = List.of(BookListResponse.builder().id(5L).title("guest").build());
        given(bookService.getRecentViews(null, guestId)).willReturn(responses);
        mockMvc.perform(get("/books/recent-views").header("X-Guest-Id", guestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("guest"));
    }

    @Test
    @DisplayName("최근 본 상품 병합")
    void mergeRecentViews() throws Exception {
        Long userId = 1L;
        String guestId = "guest_uuid";
        given(util.getGuestId()).willReturn(guestId);
        given(util.getUserId()).willReturn(userId);

        doNothing().when(bookService).mergeRecentViews(guestId, userId);

        mockMvc.perform(post("/books/recent-views/merge"))
                .andExpect(status().isOk());
        verify(bookService).mergeRecentViews(guestId, userId);
    }

    @Test
    @DisplayName("POST /books/{bookId}/likes - 좋아요 토글 API")
    void toggleLike() throws Exception {
        // given
        Long bookId = 1L;
        Long userId = 10L;
        BookLikeToggleResult result = new BookLikeToggleResult(true, 5L);

        given(util.getUserId()).willReturn(userId);
        given(bookLikeService.toggleLike(bookId, userId)).willReturn(result);

        // when & then
        mockMvc.perform(post("/books/{bookId}/likes", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked", is(true)))
                .andExpect(jsonPath("$.likeCount", is(5)));

    }
}