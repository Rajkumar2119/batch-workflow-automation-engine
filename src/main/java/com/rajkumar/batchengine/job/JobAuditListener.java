package com.rajkumar.batchengine.job;

import com.rajkumar.batchengine.domain.JobAudit;
import com.rajkumar.batchengine.domain.TriggerSource;
import com.rajkumar.batchengine.repository.JobAuditRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

/** Writes a {@link JobAudit} row when a job starts and completes it when the job ends. */
@Component
public class JobAuditListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobAuditListener.class);

    private final JobAuditRepository auditRepository;

    public JobAuditListener(JobAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        JobParameters params = jobExecution.getJobParameters();
        JobAudit audit = new JobAudit(
                jobExecution.getId(),
                jobExecution.getJobInstance().getJobName(),
                params.getString(JobParams.RUN_DATE),
                parseSource(params.getString(JobParams.TRIGGER_SOURCE)),
                params.getString(JobParams.TRIGGERED_BY),
                jobExecution.getStatus().name(),
                jobExecution.getStartTime() != null ? jobExecution.getStartTime() : LocalDateTime.now());
        auditRepository.save(audit);
        log.info("Job {} (execution {}) started for run date {} by {}/{}", audit.getJobName(),
                audit.getJobExecutionId(), audit.getRunDate(), audit.getTriggerSource(), audit.getTriggeredBy());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        long read = 0;
        long written = 0;
        long skipped = 0;
        // The partition manager step already aggregates its workers, so only count worker steps.
        for (StepExecution step : jobExecution.getStepExecutions()) {
            if (step.getStepName().contains(":partition")) {
                read += step.getReadCount();
                written += step.getWriteCount();
                skipped += step.getSkipCount();
            }
        }
        final long readCount = read;
        final long writeCount = written;
        final long skipCount = skipped;
        auditRepository.findByJobExecutionId(jobExecution.getId()).ifPresent(audit -> {
            audit.complete(jobExecution.getStatus().name(),
                    jobExecution.getEndTime() != null ? jobExecution.getEndTime() : LocalDateTime.now(),
                    readCount, writeCount, skipCount,
                    jobExecution.getExitStatus().getExitCode(),
                    jobExecution.getExitStatus().getExitDescription());
            auditRepository.save(audit);
        });
        log.info("Job execution {} finished with {}: read={}, written={}, skipped={}", jobExecution.getId(),
                jobExecution.getStatus(), readCount, writeCount, skipCount);
    }

    private static TriggerSource parseSource(String value) {
        if (value == null) {
            return TriggerSource.API;
        }
        try {
            return TriggerSource.valueOf(value);
        } catch (IllegalArgumentException e) {
            return TriggerSource.API;
        }
    }
}
