package org.nhnacademy.book2onandonbookservice.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.exception.ImageUploadException;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {

    private ImageUploadService imageUploadService;

    @Mock
    private MinioClient minioClient;

    private final String minioUrl = "http://localhost:9000";
    private final String publicUrl = "dummy";
    private final String rootBucket = "test-bucket";
    private final String bookFolder = "book";
    private final String reviewFolder = "review";

    @BeforeEach
    void setUp() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        imageUploadService = new ImageUploadService(minioClient, minioUrl,publicUrl ,rootBucket, bookFolder, reviewFolder);
    }

    @Test
    @DisplayName("생성자: 버킷이 없으면 생성")
    void constructor_BucketNotExists_CreatesBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        new ImageUploadService(minioClient, minioUrl,publicUrl ,rootBucket, bookFolder, reviewFolder);

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("생성자: 초기화 중 예외 발생해도 어플리케이션은 실행됨")
    void constructor_Exception_LogAndContinue() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("MinIO Connection Error"));

        assertThatCode(() -> new ImageUploadService(minioClient, minioUrl,publicUrl ,rootBucket, bookFolder, reviewFolder))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("uploadBookImage: 성공")
    void uploadBookImage_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "test data".getBytes()
        );

        String result = imageUploadService.uploadBookImage(file);

        assertThat(result).startsWith(publicUrl + "/" + rootBucket + "/" + bookFolder + "/").endsWith(".jpg");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("uploadReviewImage: 성공")
    void uploadReviewImage_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "image", "review.png", "image/png", "test data".getBytes()
        );

        String result = imageUploadService.uploadReviewImage(file);

        assertThat(result).startsWith(publicUrl + "/" + rootBucket + "/" + reviewFolder + "/").endsWith(".png");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("upload: MinIO 예외 발생 시 ImageUploadException 던짐")
    void upload_MinioException_ThrowsImageUploadException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "test data".getBytes()
        );

        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("Upload Fail"));

        assertThatThrownBy(() -> imageUploadService.uploadBookImage(file))
                .isInstanceOf(ImageUploadException.class)
                .hasMessage("이미지 업로드 실패");
    }

    @Test
    @DisplayName("upload: 확장자가 없는 파일")
    void upload_NoExtension_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "image", "testfile", "application/octet-stream", "data".getBytes()
        );

        String result = imageUploadService.uploadBookImage(file);

        assertThat(result).startsWith(publicUrl + "/" + rootBucket + "/" + bookFolder + "/");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("uploadImageFromUrl: URL이 null이거나 빈 값이면 null 반환")
    void uploadImageFromUrl_NullOrEmpty_ReturnsNull() {
        assertThat(imageUploadService.uploadImageFromUrl(null)).isNull();
        assertThat(imageUploadService.uploadImageFromUrl("")).isNull();
    }

    @Test
    @DisplayName("uploadImageFromUrl: 로컬 파일 URL을 통해 업로드 성공")
    void uploadImageFromUrl_Success() throws Exception {
        File tempFile = File.createTempFile("test-image", ".jpg");
        tempFile.deleteOnExit();
        String fileUrl = tempFile.toURI().toURL().toString();

        String result = imageUploadService.uploadImageFromUrl(fileUrl);

        assertThat(result).startsWith(publicUrl + "/" + rootBucket + "/" + bookFolder + "/").endsWith(".jpg");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("uploadImageFromUrl: 잘못된 URL이면 null 반환")
    void uploadImageFromUrl_InvalidUrl_ReturnsNull() {
        String result = imageUploadService.uploadImageFromUrl("invalid-url");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("remove: 성공")
    void remove_Success() throws Exception {
        String objectKey = bookFolder + "/test-image.jpg";
        String fullUrl = publicUrl + "/" + rootBucket + "/" + objectKey;

        imageUploadService.remove(fullUrl);

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("remove: URL 인코딩된 객체 이름 디코딩 후 삭제")
    void remove_DecodesUrl_Success() throws Exception {
        String encodedKey = bookFolder + "/test%20image.jpg";
        String fullUrl = publicUrl + "/" + rootBucket + "/" + encodedKey;

        imageUploadService.remove(fullUrl);

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("remove: URL이 null이거나 빈 값이면 무시")
    void remove_NullOrEmpty_Ignored() throws Exception {
        imageUploadService.remove(null);
        imageUploadService.remove("");

        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("remove: MinIO URL 접두사가 일치하지 않으면 무시")
    void remove_InvalidPrefix_Ignored() throws Exception {
        String invalidUrl = "http://other-storage.com/bucket/image.jpg";

        imageUploadService.remove(invalidUrl);

        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("remove: MinIO 예외 발생 시 로그 찍고 무시")
    void remove_Exception_Ignored() throws Exception {
        String objectKey = bookFolder + "/test.jpg";
        String fullUrl = publicUrl + "/" + rootBucket + "/" + objectKey;

        doThrow(new RuntimeException("Remove Fail"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatCode(() -> imageUploadService.remove(fullUrl))
                .doesNotThrowAnyException();
    }
}