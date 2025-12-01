package org.nhnacademy.book2onandonbookservice.service.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.springframework.data.domain.Page;
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

    /**
     * DB에 있는 모든 Book 을 ES 인덱스에 다시 색인
     */
    @Transactional(readOnly = true)
    public void reindexAll() {
        int pageSize = 1000;   // 여유 있게 페이지 크기 조절
        Pageable pageable = PageRequest.of(0, pageSize);

        while (true) {
            Page<Book> page = bookRepository.findAll(pageable);

            if (page.isEmpty()) {
                break;
            }

            log.info("Reindexing books page={} size={} totalElements={}",
                    page.getNumber(), page.getSize(), page.getTotalElements());

            page.forEach(book -> {
                try {
                    bookSearchIndexService.index(book);
                } catch (Exception e) {
                    log.error("ES 인덱싱 실패 - bookId={}", book.getId(), e);
                }
            });

            if (!page.hasNext()) {
                break;
            }
            pageable = pageable.next();
        }

        log.info("=== Book reindex 완료 ===");
    }
}