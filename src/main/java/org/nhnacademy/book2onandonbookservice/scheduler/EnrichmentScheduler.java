package org.nhnacademy.book2onandonbookservice.scheduler;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.BookEnrichmentService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrichmentScheduler {

    private final BookRepository bookRepository;
    private final BookEnrichmentService enrichmentService;

    @Scheduled(fixedDelay = 5000)
    public void runEnrichment() {
        List<Book> books = bookRepository.findBooksNeedingEnrichment(PageRequest.of(0, 20));

        if (books.isEmpty()) {
            //log.info("모든 데이터 보강 완료");
            return;
        }
        
        for (Book book : books) {
            enrichmentService.enrichBookData(book.getId());

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}