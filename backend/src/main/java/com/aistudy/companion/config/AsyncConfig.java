package com.aistudy.companion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated executor for background jobs (document processing, learning
 * workflows) so they never block request-handling threads. This is our
 * lightweight stand-in for a real job queue (see README for the
 * production-scale alternative: SQS/RabbitMQ + worker service).
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "backgroundExecutor")
    public ThreadPoolTaskExecutor backgroundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("bg-job-");
        executor.initialize();
        return executor;
    }
}
