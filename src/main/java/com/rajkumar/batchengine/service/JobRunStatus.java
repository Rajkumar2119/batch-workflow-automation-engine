package com.rajkumar.batchengine.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** API view of a job execution and its steps. */
public record JobRunStatus(
        Long executionId,
        String jobName,
        String status,
        String exitCode,
        String exitDescription,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Map<String, Object> parameters,
        List<StepStatus> steps) {

    public record StepStatus(
            String stepName,
            String status,
            long readCount,
            long writeCount,
            long skipCount,
            long commitCount,
            LocalDateTime startTime,
            LocalDateTime endTime) {
    }
}
