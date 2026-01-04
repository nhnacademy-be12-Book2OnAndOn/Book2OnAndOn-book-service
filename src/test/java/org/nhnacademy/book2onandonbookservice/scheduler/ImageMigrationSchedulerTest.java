package org.nhnacademy.book2onandonbookservice.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class ImageMigrationSchedulerTest {

    @InjectMocks
    private ImageMigrationScheduler imageMigrationScheduler;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ImageUploadService imageUploadService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private final String minioDomain = "minio.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(imageMigrationScheduler, "minioDomain", minioDomain);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    @Test
    @DisplayName("migrateImagesChunk - 대상 데이터가 없는 경우")
    void migrateImagesChunk_emptyTargets() {
        when(bookRepository.findTop100ByThumbnailIsNotNullAndThumbnailNotLike(anyString()))
                .thenReturn(Collections.emptyList());

        imageMigrationScheduler.migrateImagesChunk();

        verify(bookRepository).findTop100ByThumbnailIsNotNullAndThumbnailNotLike(anyString());
        verify(imageUploadService, never()).uploadImageFromUrl(anyString());
    }

    @Test
    @DisplayName("migrateImagesChunk - Happy Path: 정상적인 이미지 마이그레이션")
    void migrateImagesChunk_success() {
        Book book = Book.builder()
                .isbn("12345")
                .thumbnail("http://external.com/image.jpg")
                .images(new HashSet<>())
                .build();
        ReflectionTestUtils.setField(book, "id", 1L);

        when(bookRepository.findTop100ByThumbnailIsNotNullAndThumbnailNotLike(anyString()))
                .thenReturn(List.of(book));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(imageUploadService.uploadImageFromUrl(anyString())).thenReturn("http://minio.com/new-image.jpg");

        imageMigrationScheduler.migrateImagesChunk();

        verify(imageUploadService).uploadImageFromUrl("http://external.com/image.jpg");
        verify(transactionManager).commit(any());
    }

    @Test
    @DisplayName("migrateImagesChunk - Fail Path: 이미지 업로드 서비스가 null을 반환할 때 (NULL 처리)")
    void migrateImagesChunk_uploadReturnsNull() {
        Book book = Book.builder()
                .isbn("12345")
                .thumbnail("http://external.com/image.jpg")
                .images(new HashSet<>())
                .build();
        ReflectionTestUtils.setField(book, "id", 1L);

        when(bookRepository.findTop100ByThumbnailIsNotNullAndThumbnailNotLike(anyString()))
                .thenReturn(List.of(book));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(imageUploadService.uploadImageFromUrl(anyString())).thenReturn(null);

        imageMigrationScheduler.migrateImagesChunk();

        verify(imageUploadService).uploadImageFromUrl(anyString());
        verify(transactionManager).commit(any());
    }

    @Test
    @DisplayName("migrateImagesChunk - Fail Path: 이미 Minio 도메인이 포함된 경우 스킵")
    void migrateImagesChunk_alreadyMigrated() {
        Book book = Book.builder()
                .isbn("12345")
                .thumbnail("http://minio.com/image.jpg")
                .build();
        ReflectionTestUtils.setField(book, "id", 1L);

        when(bookRepository.findTop100ByThumbnailIsNotNullAndThumbnailNotLike(anyString()))
                .thenReturn(List.of(book));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        imageMigrationScheduler.migrateImagesChunk();

        verify(imageUploadService, never()).uploadImageFromUrl(anyString());
    }

    @Test
    @DisplayName("migrateImagesChunk - Happy Path: 기존 썸네일 BookImage가 있을 때 업데이트")
    void migrateImagesChunk_updateExistingBookImage() {
        Book book = Book.builder()
                .isbn("12345")
                .thumbnail("http://external.com/image.jpg")
                .build();
        ReflectionTestUtils.setField(book, "id", 1L);

        BookImage existingThumb = BookImage.builder()
                .book(book)
                .imagePath("old-path")
                .isThumbnail(true)
                .build();
        Set<BookImage> images = new HashSet<>();
        images.add(existingThumb);
        ReflectionTestUtils.setField(book, "images", images);

        when(bookRepository.findTop100ByThumbnailIsNotNullAndThumbnailNotLike(anyString()))
                .thenReturn(List.of(book));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(imageUploadService.uploadImageFromUrl(anyString())).thenReturn("http://minio.com/new.jpg");

        imageMigrationScheduler.migrateImagesChunk();

        verify(imageUploadService).uploadImageFromUrl(anyString());
        verify(transactionManager).commit(any());
    }

    @Test
    @DisplayName("migrateImagesChunk - Fail Path: 트랜잭션 내부에서 예외 발생 시 롤백")
    void migrateImagesChunk_transactionFailure() {
        Book book = Book.builder().isbn("12345").build();
        ReflectionTestUtils.setField(book, "id", 1L);

        when(bookRepository.findTop100ByThumbnailIsNotNullAndThumbnailNotLike(anyString()))
                .thenReturn(List.of(book));
        when(bookRepository.findById(1L)).thenThrow(new RuntimeException("DB Error"));

        imageMigrationScheduler.migrateImagesChunk();

        verify(transactionStatus).setRollbackOnly();
    }

    @Test
    @DisplayName("migrateImagesChunk - Fail Path: ID 조회 결과가 없을 때")
    void migrateImagesChunk_bookNotFoundInThread() {
        Book book = Book.builder().isbn("12345").build();
        ReflectionTestUtils.setField(book, "id", 1L);

        when(bookRepository.findTop100ByThumbnailIsNotNullAndThumbnailNotLike(anyString()))
                .thenReturn(List.of(book));
        when(bookRepository.findById(1L)).thenReturn(Optional.empty());

        imageMigrationScheduler.migrateImagesChunk();

        verify(imageUploadService, never()).uploadImageFromUrl(anyString());
        verify(transactionManager).commit(any());
    }
}