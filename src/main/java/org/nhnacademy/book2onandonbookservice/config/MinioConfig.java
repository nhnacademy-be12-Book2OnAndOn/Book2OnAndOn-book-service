package org.nhnacademy.book2onandonbookservice.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
    //minio url
    @Value("${minio.url}")
    private String url;
    //minio 따로 발급받은 access key
    @Value("${minio.access-key}")
    private String accessKey;
    //minio 따로 발급받은 secret key
    @Value("${minio.secret-key}")
    private String secretKey;

    //minio client bean 등록 해당 url로 accessKey와 secretKey를 이용해서 빌드 -> 사용처 : ImageUploadService 확인
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }
}
