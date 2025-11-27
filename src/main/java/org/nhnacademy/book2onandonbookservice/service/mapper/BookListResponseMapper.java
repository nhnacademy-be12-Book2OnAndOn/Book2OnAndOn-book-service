package org.nhnacademy.book2onandonbookservice.service.mapper;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchDocument;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookListResponseMapper {

    /// JPA 엔티티 → 목록 DTO 변환
    public BookListResponse fromEntity(Book book) {

        String imagePath = book.getImages().stream()
                .findFirst()
                .map(BookImage::getImagePath)
                .orElse(null);

        List<String> contributorNames = book.getBookContributors().stream()
                .map(bc -> bc.getContributor().getContributorName())
                .toList();

        List<String> publisherNames = book.getBookPublishers().stream()
                .map(bp -> bp.getPublisher().getPublisherName())
                .toList();

        List<String> categoryIds = book.getBookCategories().stream()
                .map(bc -> String.valueOf(bc.getCategory().getId()))
                .toList();

        List<String> tagNames = book.getBookTags().stream()
                .map(bt -> bt.getTag().getTagName())
                .toList();

        return BookListResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .volume(book.getVolume())
                .priceStandard(book.getPriceStandard())
                .priceSales(book.getPriceSales())
                .imagePath(imagePath)
                .contributorNames(contributorNames)
                .publisherNames(publisherNames)
                .categoryIds(categoryIds)
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
                .imagePath(null)  // ES에 이미지 안 넣었으면 null
                .contributorNames(doc.getContributorNames())
                .publisherNames(doc.getPublisherNames())
                .categoryIds(doc.getCategoryNames())
                .tagNames(doc.getTagNames())
                .build();
    }
}