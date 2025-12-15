package org.nhnacademy.book2onandonbookservice;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.nhnacademy.book2onandonbookservice.config.DataInitializer;
import org.nhnacademy.book2onandonbookservice.config.ElasticsearchIndexConfig;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class Book2OnAndOnBookServiceApplicationTests {

    @MockitoBean
    DataInitializer dataInitializer;
    @MockitoBean
    ElasticsearchIndexConfig elasticsearchIndexConfig;

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
}
