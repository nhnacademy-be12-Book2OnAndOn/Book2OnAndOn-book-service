package org.nhnacademy.book2onandonbookservice.service.search;

import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookReindexService {

    private final BookRepository bookRepository;
    private final BookSearchIndexService bookSearchIndexService;
    private final EntityManager entityManager;

    /**
     * DB에 있는 모든 Book 을 ES 인덱스에 다시 색인
     */
    @Transactional(readOnly = true)
    public void reindexAll() {
        long lastId = 0L;
        int pageSize = 1000;
        log.info("=== Book reindex 시작 ===");
        while (true) {
            Pageable limit = PageRequest.of(0, pageSize);

            List<Book> books = bookRepository.findAllByIdGreaterThan(lastId, limit);

            if (books.isEmpty()) {
                break;
            }

            books.forEach(this::safeIndex);

            Book lastBook = books.get(books.size() - 1);
            lastId = lastBook.getId();

            log.info("리인덱싱 배치 사이즈={}, 마지막 책 아이디={}", books.size(), lastId);

            entityManager.clear();
        }
    }

    private void safeIndex(Book book) {
        try {
            bookSearchIndexService.index(book);
        } catch (Exception e) {
            log.error("ES 인덱싱 실패 - bookId={}", book.getId(), e);
        }
    }
}