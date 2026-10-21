package com.rajkumar.batchengine.job;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Step 1: fail fast if the day's feed has not arrived, then clear any rows a previous run
 * wrote for the same business date. This makes a re-run of a day idempotent.
 */
public class PrepareFeedTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(PrepareFeedTasklet.class);

    private final JdbcTemplate jdbcTemplate;
    private final LocalDate businessDate;
    private final Path inputDir;

    public PrepareFeedTasklet(JdbcTemplate jdbcTemplate, LocalDate businessDate, Path inputDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.businessDate = businessDate;
        this.inputDir = inputDir;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws IOException {
        long files = countFeedFiles(inputDir);
        if (files == 0) {
            throw new IllegalStateException("No feed files (*.csv) found in " + inputDir.toAbsolutePath());
        }

        Date date = Date.valueOf(businessDate);
        int removed = jdbcTemplate.update("DELETE FROM processed_transaction WHERE business_date = ?", date);
        int removedRejects = jdbcTemplate.update("DELETE FROM rejected_record WHERE business_date = ?", date);
        log.info("Business date {}: {} feed file(s) found, cleared {} old rows and {} old rejects",
                businessDate, files, removed, removedRejects);

        contribution.getStepExecution().getExecutionContext().putLong("feedFileCount", files);
        return RepeatStatus.FINISHED;
    }

    static long countFeedFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".csv")).count();
        }
    }
}
