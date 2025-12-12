package org.nhnacademy.book2onandonbookservice.service.image;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.exception.ImageUploadException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class ImageUploadService {
    private final MinioClient minioClient;
    private final String minioUrl;
    private final String rootBucket;
    private final String bookFolder;
    private final String reviewFolder;

    // 생성자 주입 방식으로 변경 (가장 안전함)
    public ImageUploadService(MinioClient minioClient,
                              @Value("${minio.url}") String minioUrl,
                              @Value("${minio.bucket-name}") String rootBucket,
                              @Value("${minio.folder.book}") String bookFolder,
                              @Value("${minio.folder.review}") String reviewFolder) {
        this.minioClient = minioClient;
        this.minioUrl = minioUrl;
        this.rootBucket = rootBucket;
        this.bookFolder = bookFolder;
        this.reviewFolder = reviewFolder;

        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(rootBucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(rootBucket).build());
                log.info("[MinIO 초기화] 버킷('{}')이 없어서 생성했습니다.", rootBucket);
            } else {
                log.info("[MinIO 초기화] 버킷('{}') 확인 완료. MinIO와 연결 성공!", rootBucket);
            }
        } catch (Exception e) {
            log.error("[MinIO 초기화 실패] 서버 켜질 때 MinIO 연결을 못했습니다. URL이나 계정을 확인하세요.", e);
            // 여기서 에러나면 업로드는 무조건 실패하므로, 로그를 꼭 봐야 합니다.
        }
    }

    public String uploadBookImage(MultipartFile file) {
        return upload(file, bookFolder);
    }

    public String uploadReviewImage(MultipartFile file) {
        return upload(file, reviewFolder);
    }

    private String upload(MultipartFile file, String folderName) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectName = folderName + "/" + UUID.randomUUID() + extension;

            InputStream inputStream = file.getInputStream();

            // MinIO 업로드 시도
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(rootBucket)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("이미지 업로드 성공: {}", objectName);
            return minioUrl + "/" + rootBucket + "/" + objectName;

        } catch (Exception e) {
            // 여기가 핵심! 진짜 에러 원인을 로그에 찍습니다.
            log.error("[이미지 업로드 실패] MinIO 에러 발생! bucket: {}, url: {}", rootBucket, minioUrl, e);
            throw new ImageUploadException("이미지 업로드 실패", e);
        }
    }

    public void remove(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        try {
            String minioPrefix = minioUrl + "/" + rootBucket + "/";
            if (!imageUrl.startsWith(minioPrefix)) {
                return;
            }
            String objectName = imageUrl.substring(minioPrefix.length());
            objectName = URLDecoder.decode(objectName, StandardCharsets.UTF_8);

            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(rootBucket)
                    .object(objectName)
                    .build()
            );
        } catch (Exception e) {
            log.error("MINIO 이미지 삭제 실패 : URL={}", imageUrl, e);
        }
    }
}