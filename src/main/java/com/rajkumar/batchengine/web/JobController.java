package com.rajkumar.batchengine.web;

import com.rajkumar.batchengine.domain.TriggerSource;
import com.rajkumar.batchengine.service.JobRunStatus;
import com.rajkumar.batchengine.service.JobWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@Validated
public class JobController {

    private final JobWorkflowService workflowService;

    public JobController(JobWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    public List<String> jobs() {
        return workflowService.jobNames();
    }

    /** Trigger a run. Returns 202 straight away; poll the Location header for progress. */
    @PostMapping("/{jobName}/runs")
    public ResponseEntity<JobRunStatus> launch(@PathVariable String jobName,
                                               @Valid @RequestBody(required = false) LaunchJobRequest request) {
        LaunchJobRequest body = request != null ? request : new LaunchJobRequest(null, null, null, false);
        JobRunStatus status = workflowService.launch(
                jobName,
                body.runDate() != null ? body.runDate() : LocalDate.now(),
                body.triggerSource() != null ? body.triggerSource() : TriggerSource.API,
                body.triggeredBy(),
                body.forceRerun());
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/jobs/runs/" + status.executionId()))
                .body(status);
    }

    @GetMapping("/{jobName}/runs")
    public List<JobRunStatus> recentRuns(@PathVariable String jobName,
                                         @RequestParam(defaultValue = "10") @Min(1) @Max(100) int count) {
        return workflowService.recentRuns(jobName, count);
    }

    @GetMapping("/runs/{executionId}")
    public JobRunStatus status(@PathVariable long executionId) {
        return workflowService.status(executionId);
    }

    @PostMapping("/runs/{executionId}/stop")
    public JobRunStatus stop(@PathVariable long executionId) {
        return workflowService.stop(executionId);
    }

    @PostMapping("/runs/{executionId}/restart")
    public ResponseEntity<JobRunStatus> restart(@PathVariable long executionId) {
        JobRunStatus status = workflowService.restart(executionId);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/jobs/runs/" + status.executionId()))
                .body(status);
    }
}
