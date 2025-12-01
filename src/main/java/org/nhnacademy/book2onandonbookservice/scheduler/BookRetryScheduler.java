package org.nhnacademy.book2onandonbookservice.scheduler;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.BookEnrichmentService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookRetryScheduler {

    private final BookRepository bookRepository;
    private final BookEnrichmentService bookEnrichmentService;

    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(name = "runEnrichment", lockAtLeastFor = "4s", lockAtMostFor = "10s")
    public void runEnrichment() {
        List<Book> books = bookRepository.findBooksNeedingEnrichment(PageRequest.of(0, 20));

        if (books.isEmpty()) {
            return;
        }

        log.info("[스케줄러 재시도] 알라딘 정보 누락 도서 {}권 발견 ---> 보강 시작", books.size());
        for (Book book : books) {
            bookEnrichmentService.enrichBookData(book.getId());

        }
    }


    @Scheduled(fixedDelay = 30000)
    @SchedulerLock(name = "retryMissingTags", lockAtLeastFor = "29s", lockAtMostFor = "50s")
    public void retryMissingTags() {
        List<Book> books = bookRepository.findBooksNeedingTags(PageRequest.of(0, 5));

        if (books.isEmpty()) {
            return; // 할일 없을 때는 쉬기
        }

        log.info("[스케줄러 재시도] 태그 누락 도서 {}권 발견 ---> 보강 시작", books.size());

        for (Book book : books) {
            bookEnrichmentService.enrichBookData(book.getId());
        }
    }
}
