package org.nhnacademy.book2onandonbookservice.config;


import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S") // 락이 풀리지 않을 경우를 대비한 기본 제한 시간 30초
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) { //Redis를 락저장소로 쓰겠다는 빈
        return new RedisLockProvider(connectionFactory, "book-service:lock");
    }
}
