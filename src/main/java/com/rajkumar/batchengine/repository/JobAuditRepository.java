package com.rajkumar.batchengine.repository;

import com.rajkumar.batchengine.domain.JobAudit;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAuditRepository extends JpaRepository<JobAudit, Long> {

    Optional<JobAudit> findByJobExecutionId(Long jobExecutionId);

    Page<JobAudit> findByJobName(String jobName, Pageable pageable);

    Page<JobAudit> findByStatus(String status, Pageable pageable);

    Page<JobAudit> findByJobNameAndStatus(String jobName, String status, Pageable pageable);
}
