package org.nhnacademy.book2onandonbookservice.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.nhnacademy.book2onandonbookservice.service.book.BookLikeService;
import org.nhnacademy.book2onandonbookservice.service.book.BookLikeService.BookLikeToggleResult;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = BookLikeController.class)
class BookLikeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BookLikeService bookLikeService;

    @Test
    @DisplayName("POST /books/{bookId}/likes - 좋아요 토글 API")
    void toggleLike() throws Exception {
        // given
        Long bookId = 1L;
        Long userId = 10L;
        BookLikeToggleResult result = new BookLikeToggleResult(true, 5L);

        given(bookLikeService.toggleLike(bookId, userId)).willReturn(result);

        try (MockedStatic<UserHeaderUtil> mockedStatic = mockStatic(UserHeaderUtil.class)) {
            mockedStatic.when(UserHeaderUtil::getUserId).thenReturn(userId);

            // when & then
            mockMvc.perform(post("/books/{bookId}/likes", bookId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked", is(true)))
                    .andExpect(jsonPath("$.likeCount", is(5)));
        }
    }

    @Test
    @DisplayName("GET /books/likes/me - 내가 좋아요한 도서 ID 리스트 조회 API")
    void getMyLikedBooks() throws Exception {
        // given
        Long userId = 10L;
        List<Long> ids = List.of(1L, 2L, 3L);

        given(bookLikeService.getMyLikedBookIds(userId)).willReturn(ids);

        try (MockedStatic<UserHeaderUtil> mockedStatic = mockStatic(UserHeaderUtil.class)) {
            mockedStatic.when(UserHeaderUtil::getUserId).thenReturn(userId);

            // when & then
            mockMvc.perform(get("/books/likes/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]", is(1)))
                    .andExpect(jsonPath("$[1]", is(2)))
                    .andExpect(jsonPath("$[2]", is(3)));
        }
    }
}