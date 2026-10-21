package com.rajkumar.batchengine.service;

import com.rajkumar.batchengine.config.EngineProperties;
import com.rajkumar.batchengine.domain.TriggerSource;
import com.rajkumar.batchengine.job.JobParams;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Trigger, monitor, stop and restart jobs. Used by the REST API, the internal scheduler
 * and (through the REST API) Control-M.
 */
@Service
public class JobWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(JobWorkflowService.class);

    private final Map<String, Job> jobs;
    private final JobExplorer jobExplorer;
    private final JobRepository jobRepository;
    private final EngineProperties properties;
    private final TaskExecutorJobLauncher asyncLauncher;

    public JobWorkflowService(List<Job> jobs, JobExplorer jobExplorer, JobRepository jobRepository,
                              EngineProperties properties,
                              @Qualifier("jobLaunchExecutor") TaskExecutor jobLaunchExecutor) throws Exception {
        this.jobs = jobs.stream().collect(Collectors.toMap(Job::getName, Function.identity()));
        this.jobExplorer = jobExplorer;
        this.jobRepository = jobRepository;
        this.properties = properties;
        // Kept private (not a bean) so it doesn't clash with Spring Boot's synchronous JobLauncher.
        this.asyncLauncher = new TaskExecutorJobLauncher();
        this.asyncLauncher.setJobRepository(jobRepository);
        this.asyncLauncher.setTaskExecutor(jobLaunchExecutor);
        this.asyncLauncher.afterPropertiesSet();
    }

    public List<String> jobNames() {
        return jobs.keySet().stream().sorted().toList();
    }

    /** Starts a run for a business date and returns immediately with the execution id. */
    public JobRunStatus launch(String jobName, LocalDate runDate, TriggerSource source, String triggeredBy,
                               boolean forceRerun) {
        Job job = findJob(jobName);
        Path inputDir = Path.of(properties.inputBaseDir(), runDate.toString());

        JobParametersBuilder builder = new JobParametersBuilder()
                .addString(JobParams.RUN_DATE, runDate.toString())
                .addString(JobParams.INPUT_DIR, inputDir.toString(), false)
                .addString(JobParams.TRIGGER_SOURCE, source.name(), false)
                .addString(JobParams.TRIGGERED_BY, triggeredBy == null ? source.name().toLowerCase() : triggeredBy,
                        false);
        if (forceRerun) {
            builder.addLong(JobParams.RUN_ID, System.currentTimeMillis());
        }
        return run(job, builder.toJobParameters());
    }

    /** Restarts a FAILED or STOPPED execution from the step/partition where it stopped. */
    public JobRunStatus restart(long executionId) {
        JobExecution execution = findExecution(executionId);
        if (execution.getStatus() != BatchStatus.FAILED && execution.getStatus() != BatchStatus.STOPPED) {
            throw new JobConflictException("Only FAILED or STOPPED executions can be restarted; execution "
                    + executionId + " is " + execution.getStatus());
        }
        Job job = findJob(execution.getJobInstance().getJobName());
        return run(job, execution.getJobParameters());
    }

    /** Asks a running execution to stop at the next chunk boundary. */
    public JobRunStatus stop(long executionId) {
        JobExecution execution = findExecution(executionId);
        if (!execution.isRunning()) {
            throw new JobConflictException("Execution " + executionId + " is not running (" + execution.getStatus() + ")");
        }
        execution.setStatus(BatchStatus.STOPPING);
        jobRepository.update(execution);
        log.info("Stop requested for execution {}", executionId);
        return toStatus(execution);
    }

    public JobRunStatus status(long executionId) {
        return toStatus(findExecution(executionId));
    }

    /** Latest executions of a job, newest first. */
    public List<JobRunStatus> recentRuns(String jobName, int count) {
        findJob(jobName);
        List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, count);
        return instances.stream()
                .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                .sorted(Comparator.comparing(JobExecution::getId).reversed())
                .limit(count)
                .map(this::toStatus)
                .toList();
    }

    private JobRunStatus run(Job job, JobParameters parameters) {
        try {
            JobExecution execution = asyncLauncher.run(job, parameters);
            log.info("Launched {} as execution {} with {}", job.getName(), execution.getId(), parameters);
            return toStatus(execution);
        } catch (JobExecutionAlreadyRunningException e) {
            throw new JobConflictException("Job " + job.getName() + " is already running for these parameters", e);
        } catch (JobInstanceAlreadyCompleteException e) {
            throw new JobConflictException("Job " + job.getName() + " already completed for "
                    + parameters.getString(JobParams.RUN_DATE) + "; set forceRerun=true to run it again", e);
        } catch (JobRestartException e) {
            throw new JobConflictException("Job " + job.getName() + " cannot be restarted: " + e.getMessage(), e);
        } catch (JobParametersInvalidException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private Job findJob(String jobName) {
        Job job = jobs.get(jobName);
        if (job == null) {
            throw new JobNotFoundException("Unknown job '" + jobName + "'. Known jobs: " + jobNames());
        }
        return job;
    }

    private JobExecution findExecution(long executionId) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null) {
            throw new JobNotFoundException("No job execution with id " + executionId);
        }
        return execution;
    }

    private JobRunStatus toStatus(JobExecution execution) {
        Map<String, Object> params = new LinkedHashMap<>();
        execution.getJobParameters().getParameters().forEach((key, value) -> params.put(key, value.getValue()));

        List<JobRunStatus.StepStatus> steps = execution.getStepExecutions().stream()
                .sorted(Comparator.comparing(se -> se.getId() == null ? Long.MAX_VALUE : se.getId()))
                .map(se -> new JobRunStatus.StepStatus(se.getStepName(), se.getStatus().name(),
                        se.getReadCount(), se.getWriteCount(), se.getSkipCount(), se.getCommitCount(),
                        se.getStartTime(), se.getEndTime()))
                .toList();

        return new JobRunStatus(execution.getId(), execution.getJobInstance().getJobName(),
                execution.getStatus().name(), execution.getExitStatus().getExitCode(),
                execution.getExitStatus().getExitDescription(), execution.getStartTime(), execution.getEndTime(),
                params, steps);
    }
}
