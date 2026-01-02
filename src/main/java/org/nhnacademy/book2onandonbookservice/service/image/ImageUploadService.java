package org.nhnacademy.book2onandonbookservice.service.image;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.exception.ImageUploadException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class ImageUploadService {
    private final MinioClient minioClient;
    private final String minioUrl;
    private final String publicUrl;
    private final String rootBucket;
    private final String bookFolder;
    private final String reviewFolder;

    // 생성자 주입 방식으로 변경 (가장 안전함)
    public ImageUploadService(MinioClient minioClient,
                              @Value("${minio.url}") String minioUrl,
                              @Value("${minio.public-url}")String publicUrl,
                              @Value("${minio.bucket-name}") String rootBucket,
                              @Value("${minio.folder.book}") String bookFolder,
                              @Value("${minio.folder.review}") String reviewFolder) {
        this.minioClient = minioClient;
        this.minioUrl = minioUrl;
        this.publicUrl = publicUrl;
        this.rootBucket = rootBucket;
        this.bookFolder = bookFolder;
        this.reviewFolder = reviewFolder;

        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(rootBucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(rootBucket).build());
                String policyJson = """
                        {
                          "Version": "2012-10-17",
                          "Statement": [
                            {
                              "Effect": "Allow",
                              "Principal": "*",
                              "Action": ["s3:GetObject"],
                              "Resource": ["arn:aws:s3:::%s/*"]
                            }
                          ]
                        }
                        """.formatted(rootBucket);

                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder()
                                .bucket(rootBucket)
                                .config(policyJson)
                                .build()
                );

                log.info("[MinIO 초기화] 버킷('{}') 생성 및 공개 정책 설정 완료.", rootBucket);
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

            return publicUrl + "/" + rootBucket + "/" + objectName;

        } catch (Exception e) {
            log.error("[이미지 업로드 실패] MinIO 에러 발생! bucket: {}, url: {}", rootBucket, minioUrl, e);
            throw new ImageUploadException("이미지 업로드 실패", e);
        }
    }

    public String uploadImageFromUrl(String imageUrl){
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }

        // 1. 화질 개선: _1.jpg(또는 다른 숫자)를 _5.jpg로 변경 시도
        // 정규식을 사용하여 파일명 끝의 _숫자 부분을 _5로 바꿉니다.
        String highResUrl = imageUrl.replaceAll("_(\\d+)\\.jpg$", "_5.jpg");

        try {
            // 먼저 고화질 URL로 시도
            return uploadInternal(highResUrl, "image/jpeg");
        } catch (Exception e) {
            // 2. 실패 시 원본 URL로 최종 시도
            try {
                return uploadInternal(imageUrl, "image/jpeg");
            } catch (RejectedExecutionException | InterruptedIOException ie) {
                // 시스템 종료나 쓰레드 풀 종료로 인한 에러는 ERROR 로그 안 찍음
                log.warn("이미지 업로드 중단 (시스템 종료 또는 재시작 감지): {}", imageUrl);
                return null;
            } catch (java.io.FileNotFoundException fe) {
                log.warn("이미지 소스 없음(404): {}", imageUrl);
                return null;
            } catch (Exception ex) {
                // 진짜 에러만 ERROR로 출력
                log.error("이미지 업로드 최종 실패: {}", imageUrl, ex);
                return null;
            }        }
    }

    public void remove(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        try {
            String decodedUrl = URLDecoder.decode(imageUrl, StandardCharsets.UTF_8);

            int bucketIndex = decodedUrl.indexOf(rootBucket);

            if (bucketIndex == -1) {
                log.warn("[이미지 삭제 건너뜀] URL에 버킷명({})이 없습니다: {}", rootBucket, imageUrl);
                return;
            }

            String objectName = decodedUrl.substring(bucketIndex + rootBucket.length() + 1);

            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(rootBucket)
                    .object(objectName)
                    .build()
            );

            log.info("기존 MinIO 이미지 삭제 완료: {}", objectName);

        } catch (Exception e) {
            log.error("MINIO 이미지 삭제 실패 : URL={}", imageUrl, e);
        }
    }
    private String uploadOriginalUrl(String imageUrl) {
        try {
            return uploadInternal(imageUrl, "image/jpeg");
        } catch (Exception e) {
            log.error("원본 이미지 다운로드까지 실패 (무시하고 진행): {}", imageUrl, e);
            return null;
        }
    }
    private String uploadInternal(String imageUrl, String contentType) throws Exception {
        URL url = URI.create(imageUrl).toURL();

        try (InputStream inputStream = url.openStream()) {
            String savedFileName = UUID.randomUUID() + ".jpg";
            String objectName = bookFolder + "/" + savedFileName;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(rootBucket)
                            .object(objectName)
                            .stream(inputStream, -1, 10485760) // 최대 10MB
                            .contentType(contentType)
                            .build()
            );

            return publicUrl + "/" + rootBucket + "/" + objectName;
        }
    }
}