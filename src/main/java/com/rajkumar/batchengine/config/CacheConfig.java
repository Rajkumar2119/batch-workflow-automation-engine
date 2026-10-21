package com.rajkumar.batchengine.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Reference data (FX rates) is read for every record, so it is cached in Redis
 * to keep the database out of the hot path.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String FX_RATES = "fx-rates";

    @Bean
    RedisCacheManagerBuilderCustomizer fxRateCacheCustomizer(
            @Value("${engine.cache.fx-rates-ttl:PT1H}") Duration ttl) {
        return builder -> builder.withCacheConfiguration(FX_RATES,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl));
    }
}
