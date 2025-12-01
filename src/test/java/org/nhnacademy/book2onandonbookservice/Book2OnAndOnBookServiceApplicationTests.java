package org.nhnacademy.book2onandonbookservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        // 1. 인프라 (Eureka, Config Server) 연결 끄기
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:file:.",
        "eureka.client.enabled=false",

        // 2. 데이터베이스 (MySQL 대신 H2 인메모리 DB 사용)
        // 실제 DB에 붙으면 데이터가 꼬이거나 연결 에러가 날 수 있으므로 H2를 씁니다.
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",

        // 3. Redis (가짜 설정 - 실제 연결 안 해도 빈 생성만 되면 됨)
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.data.redis.password=",
        "spring.data.redis.repositories.enabled=false",

        // 4. Elasticsearch (가짜 설정)
        "spring.elasticsearch.uris=http://localhost:9200",
        "spring.elasticsearch.username=dummy",
        "spring.elasticsearch.password=dummy",

        // 5. MinIO (가짜 설정 - @Value 주입 에러 방지용)
        "minio.url=http://localhost:9000",
        "minio.access-key=dummy-access",
        "minio.secret-key=dummy-secret",
        "minio.bucket-name=dummy-bucket",
        "minio.folder.book=books",
        "minio.folder.review=reviews",

        // 6. 외부 API 키 (가짜 설정 - 실제 호출 안 함)
        "google.api-key=dummy-google-key",
        "aladin.api.base-url=http://localhost/dummy-aladin",
        "aladin.api-ttb-key=dummy-ttb-key",
        "gemini.api.base-url=http://localhost/dummy-gemini",
        "gemini.api-key=dummy-gemini-key",

        // 7. 스케줄러 비활성화 (테스트 중에 스케줄러 돌면 로그 지저분해지고 에러 가능성 있음)
        "spring.task.scheduling.enabled=false"
})
class Book2OnAndOnBookServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
