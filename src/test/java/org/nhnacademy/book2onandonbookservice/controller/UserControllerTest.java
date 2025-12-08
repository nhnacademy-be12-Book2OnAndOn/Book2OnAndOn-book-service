package org.nhnacademy.book2onandonbookservice.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.dto.api.RestPage;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.MyLikedBookResponse;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewDto;
import org.nhnacademy.book2onandonbookservice.service.book.BookLikeService;
import org.nhnacademy.book2onandonbookservice.service.review.ReviewService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    UserHeaderUtil util;

    @MockitoBean
    BookLikeService bookLikeService;

    @MockitoBean
    ReviewService reviewService;

    @Test
    @DisplayName("특정 유저 리뷰 목록 조회")
    void getUserReviewList() throws Exception {
        Long userId = 10L;
        ReviewDto reviewDto = ReviewDto.builder()
                .id(1L)
                .title("USER REVIEW")
                .content("gggoooodd")
                .score(5)
                .build();

        Page<ReviewDto> page = new PageImpl<>(List.of(reviewDto));

        given(reviewService.getReviewListByUserId(eq(userId), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/internal/users/{userId}/reviews", userId)
                        .param("page", "0")
                        .param("size", "10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(1)))
                .andExpect(jsonPath("$.content[0].title", is("USER REVIEW")));
    }

    @Test
    @DisplayName("GET /books/likes/me - 내가 좋아요한 도서 ID 리스트 조회 API")
    void getMyLikedBooks() throws Exception {
        // given
        Long userId = 10L;

        BookListResponse bookInfo = BookListResponse.builder()
                .id(1L)
                .title("테스트 도서")
                .priceSales(10000L)
                .build();

        MyLikedBookResponse responseDto = MyLikedBookResponse.builder()
                .bookLikeId(100L)
                .createdAt(LocalDateTime.now())
                .bookInfo(bookInfo)
                .build();

        List<MyLikedBookResponse> content = List.of(responseDto);
        PageImpl<MyLikedBookResponse> page = new PageImpl<>(content, PageRequest.of(0, 10), 1);
        RestPage<MyLikedBookResponse> responsePage = new RestPage<>(page);

        given(util.getUserId()).willReturn(userId);
        given(bookLikeService.getMyLikedBookIds(eq(userId), any(Pageable.class)))
                .willReturn(responsePage);

        mockMvc.perform(get("/internal/users/likes/me")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "createdAt,desc")) // 정렬 조건 테스트
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].bookLikeId").value(100L))
                .andExpect(jsonPath("$.content[0].book.id").value(1L))
                .andExpect(jsonPath("$.content[0].book.title").value("테스트 도서"));

    }

}