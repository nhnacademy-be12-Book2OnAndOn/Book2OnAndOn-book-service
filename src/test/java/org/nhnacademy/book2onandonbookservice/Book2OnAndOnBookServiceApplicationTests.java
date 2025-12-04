package org.nhnacademy.book2onandonbookservice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.config.DataInitializer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
        "aladin.api.base-url=http://localhost/dummy-aladin",
        "aladin.api-ttb-key=dummy-ttb-key",
        "gemini.api.base-url=http://localhost/dummy-gemini",
        "gemini.api-key=dummy-gemini-key",

        // 7. 스케줄러 비활성화 (테스트 중에 스케줄러 돌면 로그 지저분해지고 에러 가능성 있음)
        "spring.task.scheduling.enabled=false",
        //8. RabbitMQ 리스너 자동 시작 비활성화
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class Book2OnAndOnBookServiceApplicationTests {

    @MockitoBean
    DataInitializer dataInitializer;

    @Test
    void contextLoads() {
        //애플리케이션 컨텍스트 오류 없이 정상적으로 로드되는지 확인하는 테스트입니다.
    }

    @TestConfiguration // 테스트 환경에서만 사용되는 설정 클래스임을 나타냄
    //기존 Bean을 덮어쓰거나 테스트에만 필요한 새로운 빈을 추가할 때 사용된다고 함
    //테스트 할때만 몰래 사용하는 가짜 설정 파일을 만들어서 실제 RabbitMq 서버대신 가짜 연결 객체를 끼워넣음
    //사용이유: 실제 애플리케이션은 RabbitMQ서버에 연결하려고 하지만, 테스트 환경에는 RabbitMQ가 설치되어 있지 않거나 켜져 있지 않음
    //따라서 테스트 때는 실제 연결 설정을 무시하고 이 설정을 써라! 라고 나타내 주는것
    static class TestRabbitConfig {
        @Bean
        @Primary
        public ConnectionFactory connectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    @Test
    @DisplayName("main 메서드 실행 테스트 (커버리지용)")
    void main() {
        assertDoesNotThrow(() -> {
            Book2OnAndOnBookServiceApplication.main(new String[]{
                    // 1. 인프라 (Eureka, Config Server) 연결 끄기
                    "--spring.cloud.config.enabled=false",
                    "--spring.config.import=optional:file:.",
                    "--eureka.client.enabled=false",

                    // 2. 데이터베이스 (MySQL 대신 H2 인메모리 DB 사용)
                    // 실제 DB에 붙으면 데이터가 꼬이거나 연결 에러가 날 수 있으므로 H2를 씁니다.
                    "--spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL",
                    "--spring.datasource.driver-class-name=org.h2.Driver",
                    "--spring.datasource.username=sa",
                    "--spring.datasource.password=",
                    "--spring.jpa.hibernate.ddl-auto=create-drop",

                    // 3. Redis (가짜 설정 - 실제 연결 안 해도 빈 생성만 되면 됨)
                    "--spring.data.redis.host=localhost",
                    "--spring.data.redis.port=6379",
                    "--spring.data.redis.password=",
                    "--spring.data.redis.repositories.enabled=false",

                    // 4. Elasticsearch (가짜 설정)
                    "--spring.elasticsearch.uris=http://localhost:9200",
                    "--spring.elasticsearch.username=dummy",
                    "--spring.elasticsearch.password=dummy",

                    // 5. MinIO (가짜 설정 - @Value 주입 에러 방지용)
                    "--minio.url=http://localhost:9000",
                    "--minio.access-key=dummy-access",
                    "--minio.secret-key=dummy-secret",
                    "--minio.bucket-name=dummy-bucket",
                    "--minio.folder.book=books",
                    "--minio.folder.review=reviews",

                    // 6. 외부 API 키 (가짜 설정 - 실제 호출 안 함)
                    "--aladin.api.base-url=http://localhost/dummy-aladin",
                    "--aladin.api-ttb-key=dummy-ttb-key",
                    "--gemini.api.base-url=http://localhost/dummy-gemini",
                    "--gemini.api-key=dummy-gemini-key",

                    // 7. 스케줄러 비활성화 (테스트 중에 스케줄러 돌면 로그 지저분해지고 에러 가능성 있음)
                    "--spring.task.scheduling.enabled=false",
                    //8. RabbitMQ 리스너 자동 시작 비활성화
                    "--spring.rabbitmq.listener.simple.auto-startup=false"
            });

        });
    }

}
