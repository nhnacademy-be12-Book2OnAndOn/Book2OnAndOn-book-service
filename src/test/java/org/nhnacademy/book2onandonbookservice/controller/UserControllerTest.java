package org.nhnacademy.book2onandonbookservice.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nhnacademy.book2onandonbookservice.service.book.BookLikeService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    UserHeaderUtil util;

    @MockitoBean
    BookLikeService bookLikeService;

    @Test
    @DisplayName("GET /books/likes/me - 내가 좋아요한 도서 ID 리스트 조회 API")
    void getMyLikedBooks() throws Exception {
        // given
        Long userId = 10L;
        List<Long> ids = List.of(1L, 2L, 3L);

        given(util.getUserId()).willReturn(userId);
        given(bookLikeService.getMyLikedBookIds(userId)).willReturn(ids);

        // when & then
        mockMvc.perform(get("/books/likes/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]", is(1)))
                .andExpect(jsonPath("$[1]", is(2)))
                .andExpect(jsonPath("$[2]", is(3)));

    }

}