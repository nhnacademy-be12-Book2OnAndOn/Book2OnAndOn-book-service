package org.nhnacademy.book2onandonbookservice.service.image;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
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
            throw new RuntimeException("이미지 업로드 실패", e);
        }
    }
}
