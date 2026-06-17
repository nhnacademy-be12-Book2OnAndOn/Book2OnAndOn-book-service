package org.nhnacademy.book2onandonbookservice.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class ImageMigrationSchedulerTest {

    private ImageMigrationScheduler scheduler;

    @Mock
    private BookRepository bookRepository;
    @Mock
    private ImageUploadService imageUploadService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    // 동기식 실행을 위한 Executor (테스트용)
    private final Executor syncExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        scheduler = new ImageMigrationScheduler(bookRepository, imageUploadService, transactionManager, syncExecutor);
        ReflectionTestUtils.setField(scheduler, "minioDomain", "minio.com");
    }

    @Test
    @DisplayName("migrateImagesChunk - 대상이 없을 경우 종료")
    void migrateImagesChunk_NoTargets() {
        when(bookRepository.findTop100ByThumbnailIsNotNullAndThumbnailNotLike(anyString()))
                .thenReturn(Collections.emptyList());

        scheduler.migrateImagesChunk();

        verify(bookRepository).findTop100ByThumbnailIsNotNullAndThumbnailNotLike(anyString());
        verify(imageUploadService, never()).uploadImageFromUrl(anyString());
    }

    @Test
    @DisplayName("migrateImagesChunk - 성공 (동기식 실행 확인)")
    void migrateImagesChunk_Success() {
        // 실제 객체 사용 (id는 Reflection으로 주입)
        Book book = Book.builder()
                .isbn("12345")
                .thumbnail("http://old-image.com/img.jpg")
                .build();
        ReflectionTestUtils.setField(book, "id", 1L);

        when(bookRepository.findTop100ByThumbnailIsNotNullAndThumbnailNotLike(anyString()))
                .thenReturn(List.of(book));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        
        // TransactionManager 모킹
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        
        // ImageUploadService 모킹
        when(imageUploadService.uploadImageFromUrl(anyString())).thenReturn("http://minio.com/new-img.jpg");

        scheduler.migrateImagesChunk();

        verify(imageUploadService).uploadImageFromUrl("http://old-image.com/img.jpg");
        // book.getThumbnail()이 변경되었는지 확인
        assert book.getThumbnail().equals("http://minio.com/new-img.jpg");
        verify(transactionManager).commit(transactionStatus);
    }
}
