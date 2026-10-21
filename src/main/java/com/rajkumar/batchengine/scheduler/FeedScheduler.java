package com.rajkumar.batchengine.scheduler;

import com.rajkumar.batchengine.domain.TriggerSource;
import com.rajkumar.batchengine.job.DailyFeedJobConfig;
import com.rajkumar.batchengine.service.JobConflictException;
import com.rajkumar.batchengine.service.JobWorkflowService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Built-in cron trigger for environments without an enterprise scheduler.
 * Where Control-M owns the schedule, leave {@code engine.scheduler.enabled=false}
 * and let Control-M call the REST API instead (see /controlm).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "engine.scheduler", name = "enabled", havingValue = "true")
public class FeedScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeedScheduler.class);

    private final JobWorkflowService workflowService;
    private final ZoneId zone;

    public FeedScheduler(JobWorkflowService workflowService,
                         @Value("${engine.scheduler.zone:Europe/London}") String zone) {
        this.workflowService = workflowService;
        this.zone = ZoneId.of(zone);
    }

    /** Processes yesterday's feed every morning. */
    @Scheduled(cron = "${engine.scheduler.daily-feed-cron:0 0 6 * * MON-FRI}", zone = "${engine.scheduler.zone:Europe/London}")
    public void runDailyFeed() {
        LocalDate businessDate = LocalDate.now(zone).minusDays(1);
        try {
            workflowService.launch(DailyFeedJobConfig.JOB_NAME, businessDate, TriggerSource.SCHEDULER,
                    "feed-scheduler", false);
        } catch (JobConflictException e) {
            log.warn("Scheduled run for {} skipped: {}", businessDate, e.getMessage());
        }
    }
}
