package org.nhnacademy.book2onandonbookservice.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.service.category.CategoryService;
import org.nhnacademy.book2onandonbookservice.service.search.BookReindexService;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchSyncService;
import org.nhnacademy.book2onandonbookservice.service.tag.TagService;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReindexController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableAspectJAutoProxy
class ReindexControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BookReindexService bookReindexService;

    @MockitoBean
    BookSearchSyncService bookSearchSyncService;

    @MockitoBean
    CategoryService categoryService;

    @MockitoBean
    TagService tagService;

    @MockitoBean
    UserHeaderUtil util;

    @Test
    @DisplayName("전체 재색인 (관리자)")
    void reindexAll() throws Exception {
        given(util.getUserRole()).willReturn("ROLE_SUPER_ADMIN");
        doNothing().when(bookReindexService).reindexAll();
        mockMvc.perform(post("/admin/reindex"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("카테고리별 재색인")
    void reindexByCategory() throws Exception {
        Long categoryId = 1L;
        given(bookSearchSyncService.reindexByCategoryId(categoryId)).willReturn(10L);

        mockMvc.perform(post("/admin/reindex/category/{categoryId}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Reindexed 10 books for categoryId=1"));
    }

    @Test
    @DisplayName("태그별 재색인")
    void reindexByTag() throws Exception {
        Long tagId = 1L;
        given(bookSearchSyncService.reindexByTagId(tagId)).willReturn(10L);

        mockMvc.perform(post("/admin/reindex/tag/{tagId}", tagId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Reindexed 10 books for tagId=1"));
    }

    @Test
    @DisplayName("카테고리 이름 변경")
    void updateCategoryName() throws Exception {
        Long categoryId = 1L;
        String requestBody = "{\"newName\":\"새로운 카테고리\"}";

        Category mockCategory = Category.builder()
                .id(categoryId)
                .categoryName("새로운 카테고리")
                .build();
        given(categoryService.updateCategoryName(categoryId, "새로운 카테고리")).willReturn(mockCategory);

        mockMvc.perform(put("/admin/category/{categoryId}", categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("새로운 카테고리"));
    }

    @Test
    @DisplayName("태그 이름 변경")
    void updateTagName() throws Exception {
        Long tagId = 1L;
        String requestBody = "{\"newName\":\"새로운 태그\"}";

        Tag mockTag = Tag.builder()
                .id(tagId)
                .tagName("새로운 태그")
                .build();
        given(tagService.updateTagName(tagId, "새로운 태그")).willReturn(mockTag);

        mockMvc.perform(put("/admin/tag/{tagId}", tagId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagName").value("새로운 태그"));
    }
}