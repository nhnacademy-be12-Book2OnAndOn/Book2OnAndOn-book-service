package org.nhnacademy.book2onandonbookservice.service.book;

import java.util.List;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookUpdateRequest;
import org.springframework.stereotype.Component;

@Component
public class BookValidator {
    // 도서 등록 검증 로직
    public void validateForCreate(BookSaveRequest request) {
        validateCommonRequiredField(request);
        validateCategoryCountForCreate(request.getCategoryIds());
    }

    // 도서 수정 검증 로직
    public void validateForUpdate(BookUpdateRequest request) {
        validatePriceRangeIfPresent(request);
        validateCategoryCountIfPresent(request.getCategoryIds());
    }

    // 등록 공통 필수값 검증
    private void validateCommonRequiredField(BookSaveRequest request) {
        if (isBlank(request.getTitle())) {
            throw new IllegalArgumentException("도서 제목은 필수 작성 항목입니다.");
        }
        if (isBlank(request.getIsbn())) {
            throw new IllegalArgumentException("ISBN은 필수 작성 항목입니다.");
        }
        if (request.getPublishDate() == null) {
            throw new IllegalArgumentException("출판일은 필수 작성 항목입니다.");
        }
        if (request.getPriceStandard() == null) {
            throw new IllegalArgumentException("정가는 필수입니다.");
        }
        if (request.getPriceStandard() < 0) {
            throw new IllegalArgumentException("정가는 0원 이상이어야 합니다.");
        }
        if (request.getPriceSales() != null && request.getPriceSales() < 0) {
            throw new IllegalArgumentException("판매가는 0원 이상이어야 합니다.");
        }
    }

    // 등록 시 카테고리는 반드시 1개 ~ 10개
    private void validateCategoryCountForCreate(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new IllegalArgumentException("카테고리는 최소 1개 이상 등록되어야 합니다.");
        }
        if (categoryIds.size() > 10) {
            throw new IllegalArgumentException("카테고리는 최대 10개까지 등록 가능합니다.");
        }
    }

    // 수정 시 카테고리가 넘어온 경우에만 카테고리 개수 확인
    private void validateCategoryCountIfPresent(List<Long> categoryIds) {
        // 수정 시 카테고리를 수정하지 않은 경우
        if (categoryIds == null) {
            return;
        }
        if (categoryIds.isEmpty()) {
            throw new IllegalArgumentException("카테고리는 최소 1개 이상 등록되어야 합니다.");
        }
        if (categoryIds.size() > 10) {
            throw new IllegalArgumentException("카테고리는 최대 10개까지 등록 가능합니다.");
        }
    }

    private void validatePriceRangeIfPresent(BookUpdateRequest request) {
        if (request.getPriceStandard() != null && request.getPriceStandard() < 0) {
            throw new IllegalArgumentException("정가는 0원 이상이어야 합니다.");
        }
        if (request.getPriceSales() != null && request.getPriceSales() < 0) {
            throw new IllegalArgumentException("판매가는 0원 이상이어야 합니다.");
        }
    }

    // 공통 문자열 공백 체크
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
