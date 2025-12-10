package org.nhnacademy.book2onandonbookservice.controller;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewCreateRequest;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewDto;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewUpdateRequest;
import org.nhnacademy.book2onandonbookservice.service.review.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(SpringExtension.class)
class ReviewControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    ReviewService reviewService;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("리뷰 생성 성공")
    void createReview_success() throws Exception {
        Long bookId = 1L;
        Long reviewId = 10L;

        ReviewCreateRequest req = ReviewCreateRequest.builder()
                .title("제목")
                .content("내용을 10자 이상 적지 않으면..... 테스트 통과가ㅏ 안돼요")
                .score(3)
                .build();

        String jsonContent = objectMapper.writeValueAsString(req);

        MockMultipartFile reqPart = new MockMultipartFile(
                "request",
                "",
                "application/json",
                jsonContent.getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile imagePart = new MockMultipartFile(
                "images",
                "test.jpg",
                "image/jpeg",
                "fake-image-content".getBytes()
        );

        when(reviewService.createReview(eq(bookId), any(ReviewCreateRequest.class), anyList())).thenReturn(reviewId);

        mockMvc.perform(multipart("/books/{bookId}/reviews", bookId)
                        .file(reqPart)
                        .file(imagePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(content().string(String.valueOf(reviewId)));

        verify(reviewService).createReview(eq(bookId), any(ReviewCreateRequest.class), anyList());
    }

    @Test
    @DisplayName("리뷰 수정 성공")
    void updateReview() throws Exception {
        ReviewDto reviewDtos = ReviewDto.builder()
                .id(1L)
                .userId(1L)
                .bookId(1L)
                .title("제목이고")
                .content("내용은 10자이상 작성해야되니깐 길게")
                .score(3)
                .build();

        ReviewUpdateRequest req = ReviewUpdateRequest.builder()
                .title("제목 필수래요")
                .content("내용10인데 수정인거 이건 수정 내용만 바꾸자")
                .score(5)
                .build();

        String reqJson = objectMapper.writeValueAsString(req);
        MockMultipartFile reqPart = new MockMultipartFile(
                "request",
                "",
                "application/json",
                reqJson.getBytes(StandardCharsets.UTF_8)
        );
        willDoNothing().given(reviewService)
                .updateReview(eq(reviewDtos.getId()), any(ReviewUpdateRequest.class), any());

        mockMvc.perform(multipart("/books/reviews/{reviewId}", reviewDtos.getId())
                        .file(reqPart)
                        .with(reqst -> {
                            reqst.setMethod("PUT");
                            return reqst;
                        }))
                .andDo(print())
                .andExpect(status().isOk());

        verify(reviewService).updateReview(eq(reviewDtos.getId()), any(ReviewUpdateRequest.class), any());
    }
}