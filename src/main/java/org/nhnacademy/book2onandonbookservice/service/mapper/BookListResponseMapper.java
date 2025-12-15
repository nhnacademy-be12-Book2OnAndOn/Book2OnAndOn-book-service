package org.nhnacademy.book2onandonbookservice.service.mapper;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchDocument;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class BookListResponseMapper {

    /// JPA 엔티티 → 목록 DTO 변환
    public BookListResponse fromEntity(Book book) {


        List<String> contributorNames = book.getBookContributors().stream()
                .map(bc -> bc.getContributor().getContributorName())
                .toList();

        List<String> publisherNames = book.getBookPublishers().stream()
                .map(bp -> bp.getPublisher().getPublisherName())
                .toList();

        List<String> categoryNames = new ArrayList<>();
        Category category = book.getCategory();
        while (category != null) {
            categoryNames.add(0, category.getCategoryName()); // 앞에 추가 (루트가 먼저)
            category = category.getParent();
        }


        List<String> tagNames = book.getBookTags().stream()
                .map(bt -> bt.getTag().getTagName())
                .toList();

        return BookListResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .volume(book.getVolume())
                .priceStandard(book.getPriceStandard())
                .priceSales(book.getPriceSales())
                .thumbnail(book.getThumbnail())
                .contributorNames(contributorNames)
                .publisherNames(publisherNames)
                .categoryNames(categoryNames)
                .tagNames(tagNames)
                .build();
    }

    /// Elasticsearch Document → 목록 DTO 변환
    public BookListResponse fromDocument(BookSearchDocument doc) {
        return BookListResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .volume(doc.getVolume())
                .priceStandard(doc.getPriceStandard())
                .priceSales(doc.getPriceSales())
                .thumbnail(doc.getImagePath())  // ES에 이미지 안 넣었으면 null
                .contributorNames(doc.getContributorNames())
                .publisherNames(doc.getPublisherNames())
                .categoryNames(doc.getCategoryNames())
                .tagNames(doc.getTagNames())
                .build();
    }
}