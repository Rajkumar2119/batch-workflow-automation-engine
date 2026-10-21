package com.rajkumar.batchengine.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.rajkumar.batchengine.domain.JobAudit;
import com.rajkumar.batchengine.repository.JobAuditRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class DailyFeedJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private Job dailyFeedJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobAuditRepository auditRepository;

    @TempDir
    Path inbound;

    @BeforeEach
    void setUp() {
        jobRepositoryTestUtils.removeJobExecutions();
        jdbcTemplate.update("DELETE FROM processed_transaction");
        jdbcTemplate.update("DELETE FROM rejected_record");
        auditRepository.deleteAll();
        jobLauncherTestUtils.setJob(dailyFeedJob);
    }

    @Test
    void processesAllFilesInParallelAndRejectsBadRecords() throws Exception {
        Path day = Files.createDirectories(inbound.resolve("2026-10-20"));
        Files.writeString(day.resolve("feed_a.csv"), """
                txnId,accountId,txnDate,amount,currency,description
                A-1,GB10000001,2026-10-20,100.00,GBP,ok
                A-2,GB10000002,2026-10-20,10.00,USD,ok usd
                A-3,BAD,2026-10-20,10.00,GBP,bad account
                A-4,GB10000004,2026-10-20,20.00,EUR,ok eur
                """);
        Files.writeString(day.resolve("feed_b.csv"), """
                txnId,accountId,txnDate,amount,currency,description
                B-1,IN20000001,2026-10-20,1000.00,INR,ok inr
                B-2,IN20000002,2026-10-21,5.00,GBP,future dated
                B-3,IN20000003,2026-10-20,5.00,JPY,no rate
                B-1,IN20000009,2026-10-20,1.00,GBP,duplicate txn id in same file
                """);

        JobExecution execution = jobLauncherTestUtils.launchJob(params(day));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(count("processed_transaction")).isEqualTo(4);
        assertThat(count("rejected_record")).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT amount_gbp FROM processed_transaction WHERE txn_id = 'A-2'", java.math.BigDecimal.class))
                .isEqualByComparingTo("7.70");

        JobAudit audit = auditRepository.findByJobExecutionId(execution.getId()).orElseThrow();
        assertThat(audit.getStatus()).isEqualTo("COMPLETED");
        assertThat(audit.getRunDate()).isEqualTo("2026-10-20");
        assertThat(audit.getReadCount()).isEqualTo(8);
        assertThat(audit.getWriteCount()).isEqualTo(4);
        assertThat(audit.getSkipCount()).isEqualTo(4);
    }

    @Test
    void rerunOfSameDayReplacesPreviousOutput() throws Exception {
        Path day = Files.createDirectories(inbound.resolve("2026-10-20"));
        Files.writeString(day.resolve("feed.csv"), """
                txnId,accountId,txnDate,amount,currency,description
                R-1,GB10000001,2026-10-20,1.00,GBP,first
                """);

        jobLauncherTestUtils.launchJob(params(day));
        JobExecution rerun = jobLauncherTestUtils.launchJob(new JobParametersBuilder(params(day))
                .addLong(JobParams.RUN_ID, System.nanoTime())
                .toJobParameters());

        assertThat(rerun.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(count("processed_transaction")).isEqualTo(1);
    }

    @Test
    void failsWhenFeedHasNotArrived() throws Exception {
        Path missingDay = inbound.resolve("2026-10-19");

        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString(JobParams.RUN_DATE, "2026-10-19")
                .addString(JobParams.INPUT_DIR, missingDay.toString(), false)
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getAllFailureExceptions())
                .anySatisfy(e -> assertThat(e).hasMessageContaining("No feed files"));
        assertThat(auditRepository.findByJobExecutionId(execution.getId()))
                .get().extracting(JobAudit::getStatus).isEqualTo("FAILED");
    }

    private JobParameters params(Path day) {
        return new JobParametersBuilder()
                .addString(JobParams.RUN_DATE, "2026-10-20")
                .addString(JobParams.INPUT_DIR, day.toString(), false)
                .addString(JobParams.TRIGGER_SOURCE, "API", false)
                .addString(JobParams.TRIGGERED_BY, "integration-test", false)
                .toJobParameters();
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
