package org.nhnacademy.book2onandonbookservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewCreateRequest;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewUpdateRequest;
import org.nhnacademy.book2onandonbookservice.service.review.PurchaseVerificationService;
import org.nhnacademy.book2onandonbookservice.service.review.ReviewService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private PurchaseVerificationService purchaseVerificationService;

    @MockitoBean
    private UserHeaderUtil userHeaderUtil;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void createReview() throws Exception {
        Long bookId = 1L;
        Long reviewId = 10L;

        ReviewCreateRequest req = ReviewCreateRequest.builder()
                .title("title")
                .content("content length must be more than 10 characters")
                .score(5)
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
                "content".getBytes()
        );

        when(reviewService.createReview(eq(bookId), any(ReviewCreateRequest.class), anyList()))
                .thenReturn(reviewId);

        mockMvc.perform(multipart("/books/{bookId}/reviews", bookId)
                        .file(reqPart)
                        .file(imagePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "1")
                )
                .andExpect(status().isCreated())
                .andExpect(content().string(String.valueOf(reviewId)));

        verify(reviewService).createReview(eq(bookId), any(ReviewCreateRequest.class), anyList());
    }

    @Test
    void updateReview() throws Exception {
        Long reviewId = 100L;

        ReviewUpdateRequest req = ReviewUpdateRequest.builder()
                .title("updated title")
                .content("updated content must be more than 10 characters")
                .score(4)
                .build();

        String reqJson = objectMapper.writeValueAsString(req);

        MockMultipartFile reqPart = new MockMultipartFile(
                "request",
                "",
                "application/json",
                reqJson.getBytes(StandardCharsets.UTF_8)
        );

        willDoNothing().given(reviewService)
                .updateReview(eq(reviewId), any(ReviewUpdateRequest.class), any());

        mockMvc.perform(multipart("/books/reviews/{reviewId}", reviewId)
                        .file(reqPart)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .header("X-User-Id", "1")
                )
                .andExpect(status().isOk());

        verify(reviewService).updateReview(eq(reviewId), any(ReviewUpdateRequest.class), any());
    }

    @Test
    void checkReviewEligibility_True() throws Exception {
        Long bookId = 1L;
        Long userId = 10L;

        given(purchaseVerificationService.verifyPurchase(userId, bookId)).willReturn(true);

        mockMvc.perform(get("/books/{bookId}/reviews/eligibility", bookId)
                        .header("X-User-Id", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(purchaseVerificationService).verifyPurchase(userId, bookId);
    }

    @Test
    void checkReviewEligibility_False() throws Exception {
        Long bookId = 1L;
        Long userId = 10L;

        given(purchaseVerificationService.verifyPurchase(userId, bookId)).willReturn(false);

        mockMvc.perform(get("/books/{bookId}/reviews/eligibility", bookId)
                        .header("X-User-Id", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));

        verify(purchaseVerificationService).verifyPurchase(userId, bookId);
    }
}