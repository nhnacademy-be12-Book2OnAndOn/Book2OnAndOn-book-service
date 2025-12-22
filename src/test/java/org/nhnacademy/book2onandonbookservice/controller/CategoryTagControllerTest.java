package org.nhnacademy.book2onandonbookservice.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.exception.GlobalExceptionHandler;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundCategoryException;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundTagException;
import org.nhnacademy.book2onandonbookservice.service.category.CategoryService;
import org.nhnacademy.book2onandonbookservice.service.tag.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryTagUpdateController.class)
@AutoConfigureMockMvc(addFilters = false) // Security Filter 무시
@Import(GlobalExceptionHandler.class) // 예외 핸들러 명시적 포함 (404 검증을 위해 필수)
class CategoryTagControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    CategoryService categoryService;

    @MockitoBean
    TagService tagService;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // --- Happy Path (성공) ---

    @Test
    @DisplayName("[성공] 카테고리 이름 수정")
    void updateCategoryName_Success() throws Exception {
        Long categoryId = 1L;
        String newName = "수정된 카테고리";
        CategoryTagUpdateController.UpdateRequest request = new CategoryTagUpdateController.UpdateRequest(newName);

        Category updatedCategory = Category.builder()
                .id(categoryId)
                .categoryName(newName)
                .build();

        given(categoryService.updateCategoryName(categoryId, newName))
                .willReturn(updatedCategory);

        mockMvc.perform(put("/admin/categories/{categoryId}", categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId))
                .andExpect(jsonPath("$.name").value(newName)); // DTO가 name으로 반환되는지 확인
    }

    @Test
    @DisplayName("[성공] 태그 이름 수정")
    void updateTagName_Success() throws Exception {
        Long tagId = 10L;
        String newName = "수정된 태그";
        CategoryTagUpdateController.UpdateRequest request = new CategoryTagUpdateController.UpdateRequest(newName);

        // Service가 반환할 Mock Entity
        Tag updatedTag = Tag.builder()
                .id(tagId)
                .tagName(newName) // Entity 필드명 주의
                .build();

        given(tagService.updateTagName(tagId, newName))
                .willReturn(updatedTag);

        mockMvc.perform(put("/admin/tags/{tagId}", tagId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tagId))
                .andExpect(jsonPath("$.name").value(newName)); // DTO가 name으로 반환되는지 확인
    }

    // --- Fail Path (실패) ---

    @Test
    @DisplayName("[실패] 존재하지 않는 카테고리 수정 - 404 반환")
    void updateCategoryName_NotFound() throws Exception {
        Long categoryId = 999L;
        String newName = "없는 카테고리";
        CategoryTagUpdateController.UpdateRequest request = new CategoryTagUpdateController.UpdateRequest(newName);

        given(categoryService.updateCategoryName(categoryId, newName))
                .willThrow(new NotFoundCategoryException(categoryId));

        mockMvc.perform(put("/admin/categories/{categoryId}", categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound()) // 404 Not Found 확인
                .andExpect(jsonPath("$.error").value("Not Found")); // GlobalExceptionHandler 응답 검증
    }

    @Test
    @DisplayName("[실패] 존재하지 않는 태그 수정 - 404 반환")
    void updateTagName_NotFound() throws Exception {
        Long tagId = 999L;
        String newName = "없는 태그";
        CategoryTagUpdateController.UpdateRequest request = new CategoryTagUpdateController.UpdateRequest(newName);

        given(tagService.updateTagName(tagId, newName))
                .willThrow(new NotFoundTagException(tagId));

        mockMvc.perform(put("/admin/tags/{tagId}", tagId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound()) // 404 Not Found 확인
                .andExpect(jsonPath("$.error").value("Not Found")); // GlobalExceptionHandler 응답 검증
    }
}