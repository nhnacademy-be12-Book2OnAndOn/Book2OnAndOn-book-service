package org.nhnacademy.book2onandonbookservice.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.exception.ImageUploadException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {
    @InjectMocks
    private ImageUploadService imageUploadService;

    @Mock
    private MinioClient minioClient;

    private final String testMinioUrl = "http://localhost:9000";
    private final String testRootBucket = "test-bucket";
    private final String testBookFolder = "books";
    private final String testReviewFolder = "reviews";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(imageUploadService, "minioUrl", testMinioUrl);
        ReflectionTestUtils.setField(imageUploadService, "rootBucket", testRootBucket);
        ReflectionTestUtils.setField(imageUploadService, "bookFolder", testBookFolder);
        ReflectionTestUtils.setField(imageUploadService, "reviewFolder", testReviewFolder);
    }

    @Test
    @DisplayName("책 이미지 업로드 성공")
    void uploadBookImage_Success() throws Exception {
        String originalFilename = "test-image.jpg";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                originalFilename,
                "image/jpeg",
                "dummy content".getBytes()
        );

        String resultUrl = imageUploadService.uploadBookImage(file);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());

        PutObjectArgs args = captor.getValue();

        assertThat(args.bucket()).isEqualTo(testRootBucket);

        assertThat(args.object()).startsWith(testBookFolder + "/");
        assertThat(args.object()).endsWith(".jpg");

        String prefix = testMinioUrl + "/" + testRootBucket + "/" + testBookFolder + "/";
        assertThat(resultUrl).startsWith(prefix);
    }

    @Test
    @DisplayName("리뷰 이미지 업로드 성공")
    void uploadReviewImage_Success() throws Exception {
        String originalFilename = "test-image.jpg";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                originalFilename,
                "image/jpeg",
                "dummy content".getBytes()
        );

        String resultUrl = imageUploadService.uploadReviewImage(file);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());

        PutObjectArgs args = captor.getValue();

        assertThat(args.bucket()).isEqualTo(testRootBucket);

        assertThat(args.object()).startsWith(testReviewFolder + "/");
        assertThat(args.object()).endsWith(".jpg");

        String prefix = testMinioUrl + "/" + testRootBucket + "/" + testReviewFolder + "/";
        assertThat(resultUrl).startsWith(prefix);
    }

    @Test
    @DisplayName("이미지 업로드 실패 - Minio 예외 발생시 ImageUploadException 나오게")
    void upload_Fail_MinioException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "dummy content".getBytes()
        );

        willThrow(new RuntimeException("Minio Connection Error"))
                .given(minioClient).putObject(any(PutObjectArgs.class));

        assertThatThrownBy(() -> imageUploadService.uploadBookImage(file))
                .isInstanceOf(ImageUploadException.class)
                .hasMessage("이미지 업로드 실패");
    }

    @Test
    @DisplayName("이미지 삭제 성공")
    void remove_Success() throws Exception {
        String objectName = testBookFolder + "/uuid-test.jpg";
        String validUrl = testMinioUrl + "/" + testRootBucket + "/" + objectName;

        imageUploadService.remove(validUrl);

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());

        RemoveObjectArgs args = captor.getValue();
        assertThat(args.bucket()).isEqualTo(testRootBucket);
        assertThat(args.object()).isEqualTo(objectName);
    }

    @Test
    @DisplayName("이미지 삭제 무시 - URL이 null이거나 비어있으면 동작 안함")
    void remove_Ignore_NullOrEmpty() {
        String nullUrl = null;
        String emptyUrl = "";

        imageUploadService.remove(nullUrl);
        imageUploadService.remove(emptyUrl);
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("이미지 삭제 무시 - 외부링크인 경우 삭제 건너뜀")
    void remove_Ignore_ExternalUrl() {
        String url = "http://google.com/images/test.jpg";
        imageUploadService.remove(url);
        verifyNoMoreInteractions(minioClient);
    }

    @Test
    @DisplayName("이미지 삭제 실패")
    void remove_Fail() throws Exception {
        String objectName = testBookFolder + "/uuid-test.jpg";
        String url = testMinioUrl + "/" + testRootBucket + "/" + objectName;

        willThrow(new RuntimeException("Minio Remove Error"))
                .given(minioClient).removeObject(any(RemoveObjectArgs.class));

        imageUploadService.remove(url);
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }
}