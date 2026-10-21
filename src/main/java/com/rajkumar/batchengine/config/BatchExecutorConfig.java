package com.rajkumar.batchengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BatchExecutorConfig {

    /** Threads used to process feed files (partitions) concurrently. */
    @Bean
    TaskExecutor partitionTaskExecutor(EngineProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.gridSize());
        executor.setMaxPoolSize(properties.gridSize());
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("feed-worker-");
        executor.initialize();
        return executor;
    }

    /** Threads used to launch jobs asynchronously so API calls return immediately. */
    @Bean
    TaskExecutor jobLaunchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("job-launcher-");
        executor.initialize();
        return executor;
    }
}
