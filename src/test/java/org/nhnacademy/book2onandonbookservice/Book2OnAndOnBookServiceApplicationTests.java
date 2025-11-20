package org.nhnacademy.book2onandonbookservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "aladin.api.base-url=http://localhost/dummy",
        "aladin.api-ttb-key=test-key",
        "gemini.api.base-url=http://localhost/dummy",
        "gemini.api-key=test-key,test-key,test-key",
        "google.api-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.task.scheduling.enabled=false"
})
class Book2OnAndOnBookServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
