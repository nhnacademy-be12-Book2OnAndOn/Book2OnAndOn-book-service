package org.nhnacademy.book2onandonbookservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageMigrationScheduler {

    private final BookRepository bookRepository;
    private final ImageUploadService imageUploadService;
    private final PlatformTransactionManager transactionManager;

    @Value("${minio.public-url}")
    private String minioDomain;

    private final Executor taskExecutor = Executors.newFixedThreadPool(10);

    @Scheduled(fixedDelay = 3000)
    @SchedulerLock(name="ImageMigrationTask", lockAtLeastFor = "PT2S", lockAtMostFor = "PT10S")
    public void migrateImagesChunk() {

        List<Book> targets = bookRepository.findTop100ByThumbnailIsNotNullAndThumbnailNotLike("%" + minioDomain + "%");

        if (targets.isEmpty()) {
             // log.info("이미지 마이그레이션 대상 없음 (모두 완료됨)");
            return;
        }

        List<Long> targetIds = targets.stream().map(Book::getId).toList();

        log.info("[Scheduler] 이미지 변환 배치 시작: {}건 (Redis Lock 적용됨)", targetIds.size());
        long start = System.currentTimeMillis();

        // 2. ID를 기반으로 각 스레드에 작업 할당
        List<CompletableFuture<Void>> futures = targetIds.stream()
                .map(bookId -> CompletableFuture.runAsync(() -> processSingleBookInTransaction(bookId), taskExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long end = System.currentTimeMillis();
        log.info("배치 {}건 처리 완료. 소요시간: {}ms", targetIds.size(), (end - start));
    }

    private void processSingleBook(Book book) {
        String originalUrl = book.getThumbnail();
        
        if (originalUrl.contains(minioDomain)) {
            return;
        }

        try {
            String newUrl = imageUploadService.uploadImageFromUrl(originalUrl);

            if (newUrl != null) {
                book.setThumbnail(newUrl);

                boolean imageFound = false;

                for (BookImage image : book.getImages()) {
                    if (image.isThumbnail()) {
                        image.setImagePath(newUrl);
                        imageFound = true;
                    }
                }

                if (!imageFound) {
                    book.getImages().add(BookImage.builder()
                            .book(book)
                            .imagePath(newUrl)
                            .isThumbnail(true)
                            .build());
                }

            } else {
                log.warn("이미지 다운 실패 -> NULL 처리: {}", book.getIsbn());
                book.setThumbnail(null);
            }
        } catch (Exception e) {
            log.error("이미지 변환 중 에러 발생: ISBN {}", book.getIsbn(), e);
        }
    }
    private void processSingleBookInTransaction(Long bookId) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // 트랜잭션 시작
        transactionTemplate.execute(status -> {
            try {
                // 1. 이 스레드만의 영속성 컨텍스트에서 다시 조회
                Book book = bookRepository.findById(bookId).orElse(null);
                if (book == null) return null;

                processSingleBook(book);

                // 더티 체킹(Dirty Checking)에 의해 변경사항 자동 감지되어 커밋됨
                return null;
            } catch (Exception e) {
                log.error("이미지 변환 트랜잭션 실패: BookID {}", bookId, e);
                status.setRollbackOnly(); // 에러 발생 시 롤백
                return null;
            }
        });
    }
}