package org.nhnacademy.book2onandonbookservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.repository.BatchInsertRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookBatchService {

    private final BatchInsertRepository batchInsertRepository;
    private final BookRepository bookRepository;

    @Transactional
    public void saveBooksInBatch(List<Book> books) {
        if (books.isEmpty()) {
            return;
        }

        batchInsertRepository.saveAllBooks(books);

        List<String> isbns = books.stream().map(Book::getIsbn).collect(Collectors.toList());
        List<BookRepository.BookIdAndIsbn> savedIds = bookRepository.findByIsbnIn(isbns);
        Map<String, Long> isbnIdMap = savedIds.stream()
                .collect(Collectors.toMap(BookRepository.BookIdAndIsbn::getIsbn, BookRepository.BookIdAndIsbn::getId,
                        (oldVal, newVal) -> oldVal));

        List<BookContributor> allContributors = new ArrayList<>();
        List<BookPublisher> allPublishers = new ArrayList<>();
        List<BookImage> allImages = new ArrayList<>();

        for (Book originalBook : books) {
            Long bookId = isbnIdMap.get(originalBook.getIsbn());
            if (bookId == null) {
                continue;
            }

            Book proxyBook = Book.builder().id(bookId).build();

            for (BookContributor bc : originalBook.getBookContributors()) {
                bc.setBook(proxyBook);
                allContributors.add(bc);
            }
            for (BookPublisher bp : originalBook.getBookPublishers()) {
                bp.setBook(proxyBook);
                allPublishers.add(bp);
            }
            for (BookImage bi : originalBook.getImages()) {
                bi.setBook(proxyBook);
                allImages.add(bi);
            }
        }

        batchInsertRepository.saveBookImages(allImages);
        batchInsertRepository.saveBookRelations(allContributors, allPublishers);
    }
}