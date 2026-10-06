package com.example.coffeeshop.domain.outbox.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
public class OutboxConfiguration {
    @Bean
    public ThreadPoolTaskExecutor outboxExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("outbox-");
        return executor;
    }
}
