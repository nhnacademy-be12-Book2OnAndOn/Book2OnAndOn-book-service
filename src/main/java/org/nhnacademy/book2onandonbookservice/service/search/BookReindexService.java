package org.nhnacademy.book2onandonbookservice.service.search;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
        long lastId = 0;
        int pageSize = 1000;
        log.info("=== Book reindex 시작 ===");
        while (true) {
            Pageable limit = PageRequest.of(0, pageSize);

            List<Book> books = bookRepository.findAllByIdGreaterThan(lastId, limit);

            if (books.isEmpty()) {
                break;
            }

            List<BookSearchDocument> documentList = books.stream()
                    .map(book -> {
                        try {
                            return bookSearchIndexService.createDocumentWithoutEmbedding(book);
                        } catch (Exception e) {
                            log.error("객체 변환 실패 bookId={}", book.getId(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            documentList.parallelStream().forEach(doc -> {
                bookSearchIndexService.injectEmbedding(doc);
            });
            if (!documentList.isEmpty()) {
                try {
                    bookSearchRepository.saveAll(documentList);
                    long batchLastId = documentList.get(documentList.size() - 1).getId();
                    log.info("배치 저장 완료: {}건 (Last ID: {})", documentList.size(),batchLastId );
                    lastId = batchLastId;
                } catch (Exception e) {
                    log.error("배치 저장 중 에러 발생", e);
                    lastId = books.get(books.size() - 1).getId();
                }
            }

            entityManager.clear();
        }

        log.info("=== Book reindex 종료 ===");
    }

}