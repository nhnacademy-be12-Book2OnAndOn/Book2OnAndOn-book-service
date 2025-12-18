package org.nhnacademy.book2onandonbookservice.service.enrichment;

import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.exception.CategoryResolveException;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CategoryEnrichmentService {

    private final CategoryRepository categoryRepository;

    public void enrich(Book book, AladinApiResponse.Item aladinData){
        String categoryPath = aladinData.getCategoryName();

        if(!StringUtils.hasText(categoryPath)){
            throw new CategoryResolveException("알라딘 카테고리 정보 없음");
        }

        Category leaf = resolveCategoryTree(categoryPath);
        book.setCategory(leaf); //항상 덮어씀
    }

    private Category resolveCategoryTree(String path){
        String[] parts = path.split(">");

        Category parent = null;
        Category current = null;

        for(String raw : parts){
            String name = raw.trim();
            if(name.isEmpty()) continue;

            Category finalParent = parent;
            current= categoryRepository
                    .findByCategoryNameAndParent(name, parent)
                    .orElseGet(()-> {
                        Category c = Category.builder().categoryName(name)
                                        .parent(finalParent).build();
                        return categoryRepository.save(c);
                    });
            parent=current;
        }

        if(current==null){
            throw new CategoryResolveException("카테고리 파싱 실패: " + path);
        }

        return current;
    }
}
