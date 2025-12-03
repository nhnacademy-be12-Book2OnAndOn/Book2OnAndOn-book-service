package org.nhnacademy.book2onandonbookservice.service.image;

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

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.bucket-name}")
    private String rootBucket; //book2onandon

    @Value("${minio.folder.book}")
    private String bookFolder; //book2onandon/books

    @Value("${minio.folder.review}")
    private String reviewFolder; //book2onandon/reviews

    public ImageUploadService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * Book Image 등록 서비스
     *
     * @param file 올릴 파일
     * @return upload 메서드를 통해 bookFolder 이름 붙여서 리턴
     */
    public String uploadBookImage(MultipartFile file) {
        return upload(file, bookFolder);
    }

    /**
     * Review Image 등록 서비스
     *
     * @param file 올릴 파일
     * @return upload 메서드를 통해 reviewFolder 이름 붙여서 리턴
     */
    public String uploadReviewImage(MultipartFile file) {
        return upload(file, reviewFolder);
    }


    /**
     * 공통 파일 업로드 로직
     *
     * @param file       올릴 파일
     * @param folderName 책 이미지인지, 리뷰 이미지 인지 확인
     * @return minio 어디에 저장되어있는지의 url 이 url를 db에 저장 추후에 등록된 이미지가 필요할때 1. db에서 minio url을 찾는다 2. minio url을 통해 minio에 저장된
     * 이미지를 찾아서 리턴 즉, 업로드 : view -> controller -> minio저장 -> minio url을 db에 저장 리턴 : db에서 minio url 가져오기 -> minio url을
     * 가지고 minio에서 이미지 찾기 -> controller -> view로 리턴 or 다른 서비스에게 전달
     */
    private String upload(MultipartFile file, String folderName) {
        try {
            String originalFilename = file.getOriginalFilename();

            String extension = "";

            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectName = folderName + "/" + UUID.randomUUID().toString() + extension;

            InputStream inputStream = file.getInputStream();

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(rootBucket)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return minioUrl + "/" + rootBucket + "/" + objectName;
        } catch (Exception e) {
            throw new ImageUploadException("이미지 업로드 실패", e);
        }
    }

    /**
     * 공통 파일 삭제 로직 Book 이미지든 Review 이미지든 MINIO 면 삭제
     */
    public void remove(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        try {
            String minioPrefix = minioUrl + "/" + rootBucket + "/";

            if (!imageUrl.startsWith(minioPrefix)) {
                log.info("외부 링크이므로 삭제 건너뜀: {}", imageUrl);
                return;
            }

            String objectName = imageUrl.substring(minioPrefix.length());
            objectName = URLDecoder.decode(objectName, StandardCharsets.UTF_8);

            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(rootBucket)
                    .object(objectName)
                    .build()
            );

            log.info("MINIO 삭제 성공: {}", objectName);
        } catch (Exception e) {
            log.error("MINIO 이미지 삭제 실패 : URL={}", imageUrl, e);
        }
    }


}
