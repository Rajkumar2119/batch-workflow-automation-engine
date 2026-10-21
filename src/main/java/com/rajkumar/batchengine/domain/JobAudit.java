package com.rajkumar.batchengine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * One row per job execution: who triggered it, from where, and what it did.
 * This is the record operations teams and auditors look at, separate from
 * Spring Batch's internal metadata tables.
 */
@Entity
@Table(name = "job_audit")
public class JobAudit {

    private static final int MAX_DESCRIPTION = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_execution_id", nullable = false, unique = true)
    private Long jobExecutionId;

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    @Column(name = "run_date", length = 10)
    private String runDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 20)
    private TriggerSource triggerSource;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "read_count")
    private long readCount;

    @Column(name = "write_count")
    private long writeCount;

    @Column(name = "skip_count")
    private long skipCount;

    @Column(name = "exit_code", length = 20)
    private String exitCode;

    @Column(name = "exit_description", length = MAX_DESCRIPTION)
    private String exitDescription;

    protected JobAudit() {
    }

    public JobAudit(Long jobExecutionId, String jobName, String runDate, TriggerSource triggerSource,
                    String triggeredBy, String status, LocalDateTime startTime) {
        this.jobExecutionId = jobExecutionId;
        this.jobName = jobName;
        this.runDate = runDate;
        this.triggerSource = triggerSource;
        this.triggeredBy = triggeredBy;
        this.status = status;
        this.startTime = startTime;
    }

    public void complete(String status, LocalDateTime endTime, long readCount, long writeCount,
                         long skipCount, String exitCode, String exitDescription) {
        this.status = status;
        this.endTime = endTime;
        this.readCount = readCount;
        this.writeCount = writeCount;
        this.skipCount = skipCount;
        this.exitCode = exitCode;
        this.exitDescription = exitDescription != null && exitDescription.length() > MAX_DESCRIPTION
                ? exitDescription.substring(0, MAX_DESCRIPTION)
                : exitDescription;
    }

    public Long getId() {
        return id;
    }

    public Long getJobExecutionId() {
        return jobExecutionId;
    }

    public String getJobName() {
        return jobName;
    }

    public String getRunDate() {
        return runDate;
    }

    public TriggerSource getTriggerSource() {
        return triggerSource;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public long getReadCount() {
        return readCount;
    }

    public long getWriteCount() {
        return writeCount;
    }

    public long getSkipCount() {
        return skipCount;
    }

    public String getExitCode() {
        return exitCode;
    }

    public String getExitDescription() {
        return exitDescription;
    }
}
