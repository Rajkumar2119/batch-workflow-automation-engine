package com.rajkumar.batchengine.web;

import com.rajkumar.batchengine.domain.JobAudit;
import com.rajkumar.batchengine.repository.JobAuditRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only audit trail of every job execution. */
@RestController
@RequestMapping("/api/v1/audit")
@Validated
public class AuditController {

    private final JobAuditRepository auditRepository;

    public AuditController(JobAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping
    public PageResponse<JobAudit> search(@RequestParam(required = false) String jobName,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        Page<JobAudit> result;
        if (jobName != null && status != null) {
            result = auditRepository.findByJobNameAndStatus(jobName, status, pageable);
        } else if (jobName != null) {
            result = auditRepository.findByJobName(jobName, pageable);
        } else if (status != null) {
            result = auditRepository.findByStatus(status, pageable);
        } else {
            result = auditRepository.findAll(pageable);
        }
        return PageResponse.of(result);
    }
}
