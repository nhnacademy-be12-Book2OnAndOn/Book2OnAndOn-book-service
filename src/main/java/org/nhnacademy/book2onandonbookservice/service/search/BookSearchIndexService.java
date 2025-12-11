package org.nhnacademy.book2onandonbookservice.service.search;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.Category;
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
        String thumbnail = book.getThumbnail();

        if (thumbnail == null && book.getImages() != null && !book.getImages().isEmpty()) {
            thumbnail = book.getImages().iterator().next().getImagePath();
        }

        List<String> categoryNames = new ArrayList<>();
        List<String> categoryIds = new ArrayList<>();

        Category category = book.getCategory();

        while (category != null) {
            categoryNames.add(0, category.getCategoryName()); // 앞에 추가 (루트가 먼저 오도록)
            categoryIds.add(0, String.valueOf(category.getId()));
            category = category.getParent();
        }

        return BookSearchDocument.builder()
                .id(book.getId())
                .isbn(book.getIsbn())
                .title(book.getTitle())
                .volume(book.getVolume())
                .imagePath(thumbnail)
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
                .categoryNames(categoryNames)
                .categoryIds(categoryIds)
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
