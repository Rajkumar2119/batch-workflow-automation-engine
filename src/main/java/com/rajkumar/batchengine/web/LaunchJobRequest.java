package com.rajkumar.batchengine.web;

import com.rajkumar.batchengine.domain.TriggerSource;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body for starting a run. Every field is optional:
 * runDate defaults to today, triggerSource to API.
 */
public record LaunchJobRequest(
        LocalDate runDate,
        TriggerSource triggerSource,
        @Size(max = 100) String triggeredBy,
        boolean forceRerun) {
}
