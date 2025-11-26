package org.nhnacademy.book2onandonbookservice.service.search;

import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookCategory;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.repository.BookSearchRepository;
import org.springframework.stereotype.Service;

// Book - ES 인덱싱 서비스
@Service
@RequiredArgsConstructor
public class BookSearchIndexService {
    private final BookSearchRepository bookSearchRepository;

    // Book 엔티티를 ES 인덱스에 저장/갱신
    public void index(Book book) {
        BookSearchDocument bookSearchDocument = toDocument(book);
        bookSearchRepository.save(bookSearchDocument);
    }

    // 도서 삭제 시 ES 인덱스에서도 같이 삭제
    public void deleteIndex(Long bookId) {
        bookSearchRepository.deleteById(bookId);
    }

    private BookSearchDocument toDocument(Book book) {
        return BookSearchDocument.builder()
                .id(book.getId())
                .isbn(book.getIsbn())
                .title(book.getTitle())
                .volume(book.getVolume())
                .contributorNames(
                        book.getBookContributors().stream()
                                .map(bc -> bc.getContributor().getContributorName())
                                .toList()
                )
                .publisherNames(
                        book.getBookPublishers().stream()
                                .map(BookPublisher::getPublisher)
                                .map(p -> p.getPublisherName())
                                .toList()
                )
                .categoryNames(
                        book.getBookCategories().stream()
                                .map(BookCategory::getCategory)
                                .map(c -> c.getCategoryName())
                                .toList()
                )
                .tagNames(
                        book.getBookTags().stream()
                                .map(BookTag::getTag)
                                .map(t -> t.getTagName())
                                .toList()
                )
                .publishDate(book.getPublishDate())
                .priceStandard(book.getPriceStandard())
                .priceSales(book.getPriceSales())
                .build();
    }
}
