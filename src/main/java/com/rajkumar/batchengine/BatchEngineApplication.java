package com.rajkumar.batchengine;

import com.rajkumar.batchengine.config.EngineProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(EngineProperties.class)
public class BatchEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchEngineApplication.class, args);
    }
}
