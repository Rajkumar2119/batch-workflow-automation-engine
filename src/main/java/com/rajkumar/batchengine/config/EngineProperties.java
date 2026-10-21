package com.rajkumar.batchengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the engine, bound from the {@code engine.*} properties.
 *
 * @param inputBaseDir root folder for inbound feeds; each run reads {@code <inputBaseDir>/<runDate>/*.csv}
 * @param chunkSize    records per transaction in the worker step
 * @param gridSize     number of feed files processed in parallel
 * @param skipLimit    maximum bad records tolerated per file before the step fails
 */
@ConfigurationProperties(prefix = "engine")
public record EngineProperties(String inputBaseDir, int chunkSize, int gridSize, int skipLimit) {

    public EngineProperties {
        if (inputBaseDir == null || inputBaseDir.isBlank()) {
            inputBaseDir = "./sample-data";
        }
        if (chunkSize <= 0) {
            chunkSize = 500;
        }
        if (gridSize <= 0) {
            gridSize = 4;
        }
        if (skipLimit <= 0) {
            skipLimit = 1000;
        }
    }
}
