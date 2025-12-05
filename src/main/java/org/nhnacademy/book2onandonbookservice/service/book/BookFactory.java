package org.nhnacademy.book2onandonbookservice.service.book;

import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookUpdateRequest;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.springframework.stereotype.Component;

// Book 엔티티 기본 필드 생성/수정 책임
@Component
@RequiredArgsConstructor
public class BookFactory {
    public Book createFrom(BookSaveRequest request) {
        Long priceStandard = request.getPriceStandard();
        Long priceSales = (request.getPriceSales() != null) ? request.getPriceSales() : priceStandard;
        Integer stockCount = (request.getStockCount() != null) ? request.getStockCount() : 0;
        boolean wrapped = Boolean.TRUE.equals(request.getIsWrapped());

        return Book.builder()
                .title(request.getTitle())
                .isbn(request.getIsbn())
                .volume(request.getVolume())
                .chapter(request.getChapter())
                .description(request.getDescriptionHtml())
                .priceStandard(priceStandard)
                .priceSales(priceSales).isWrapped(wrapped)
                .publishDate(request.getPublishDate())
                .status(request.getStatus())
                .stockCount(stockCount)
                .build();
    }

    // 도서 수정 시 단일 필드 업데이트
    public void updateFields(Book book, BookUpdateRequest req) {

        if (req.getTitle() != null) {
            book.setTitle(req.getTitle());
        }

        if (req.getVolume() != null) {
            book.setVolume(req.getVolume());
        }

        if (req.getChapter() != null) {
            book.setChapter(req.getChapter());
        }

        if (req.getDescriptionHtml() != null) {
            book.setDescription(req.getDescriptionHtml());
        }

        if (req.getIsbn() != null) {
            book.setIsbn(req.getIsbn());
        }

        if (req.getPublishDate() != null) {
            book.setPublishDate(req.getPublishDate());
        }

        if (req.getPriceStandard() != null) {
            book.setPriceStandard(req.getPriceStandard());
        }

        if (req.getPriceSales() != null) {
            book.setPriceSales(req.getPriceSales());
        }

        if (req.getStockCount() != null) {
            book.setStockCount(req.getStockCount());
        }

        if (req.getStatus() != null) {
            book.setStatus(req.getStatus());
        }

        if (req.getIsWrapped() != null) {
            book.setIsWrapped(req.getIsWrapped());
        }
    }
}
