package org.nhnacademy.book2onandonbookservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.service.category.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(SpringExtension.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getCategory() throws Exception {
        CategoryDto categoryDto = CategoryDto.builder()
                .id(1L)
                .name("General")
                .build();
        List<CategoryDto> categories = List.of(categoryDto);

        given(categoryService.getCategories()).willReturn(categories);

        mockMvc.perform(get("/categories")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("General"));

        verify(categoryService).getCategories();
    }

    @Test
    void getBooksByCategory() throws Exception {
        Long categoryId = 1L;
        Pageable pageable = PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "publishDate"));

        BookListResponse bookResponse = BookListResponse.builder()
                .id(100L)
                .title("Test Book")
                .build();
        Page<BookListResponse> pageResult = new PageImpl<>(List.of(bookResponse), pageable, 1);

        given(categoryService.getBooksByCategory(eq(categoryId), any(Pageable.class)))
                .willReturn(pageResult);

        mockMvc.perform(get("/categories/{categoryId}", categoryId)
                        .param("page", "0")
                        .param("size", "12")
                        .param("sort", "publishDate,desc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100L))
                .andExpect(jsonPath("$.content[0].title").value("Test Book"));

        verify(categoryService).getBooksByCategory(eq(categoryId), any(Pageable.class));
    }

    @Test
    void getCategoryInfo() throws Exception {
        Long categoryId = 1L;
        CategoryDto categoryDto = CategoryDto.builder()
                .id(categoryId)
                .name("Technology")
                .build();

        given(categoryService.getCategory(categoryId)).willReturn(categoryDto);

        mockMvc.perform(get("/categories/{categoryId}/info", categoryId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId))
                .andExpect(jsonPath("$.name").value("Technology"));

        verify(categoryService).getCategory(categoryId);
    }
}