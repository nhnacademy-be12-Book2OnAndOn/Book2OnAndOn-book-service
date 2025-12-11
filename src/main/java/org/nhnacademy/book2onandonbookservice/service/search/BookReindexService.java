package org.nhnacademy.book2onandonbookservice.service.search;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookSearchRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookReindexService {

    private final BookRepository bookRepository;
    private final BookSearchIndexService bookSearchIndexService;
    private final BookSearchRepository bookSearchRepository;
    private final EntityManager entityManager;

    /**
     * DB에 있는 모든 Book 을 ES 인덱스에 다시 색인
     */
    @Async
    @Transactional
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

            List<BookSearchDocument> documentList = new ArrayList<>();
            for (Book book : books) {
                try {
                    // IndexService에 createDocument 메서드가 public으로 열려 있어야 함!
                    BookSearchDocument doc = bookSearchIndexService.createDocument(book);
                    documentList.add(doc);
                } catch (Exception e) {
                    log.error("변환 실패 bookId={}", book.getId(), e);
                }
            }
            if (!documentList.isEmpty()) {
                try {
                    bookSearchRepository.saveAll(documentList);
                    log.info("배치 저장 완료: {}건 (Last ID: {})", documentList.size(), books.get(books.size()-1).getId());
                } catch (Exception e) {
                    log.error("배치 저장 중 에러 발생", e);
                }
            }
            Book lastBook = books.get(books.size() - 1);
            lastId = lastBook.getId();

            entityManager.clear();
        }

        log.info("=== Book reindex 종료 ===");
    }

    private void safeIndex(Book book) {
        try {
            bookSearchIndexService.index(book);
        } catch (Exception e) {
            log.error("ES 인덱싱 실패 - bookId={}", book.getId(), e);
        }
    }
}