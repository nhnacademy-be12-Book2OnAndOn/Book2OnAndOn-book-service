package org.nhnacademy.book2onandonbookservice.service.search;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookReindexService {

    private final BookRepository bookRepository;
    private final BookSearchIndexService bookSearchIndexService;

    @Transactional(readOnly = true)
    public void reindexAll() {
        List<Book> books = bookRepository.findAll();
        books.forEach(bookSearchIndexService::index);
    }
}