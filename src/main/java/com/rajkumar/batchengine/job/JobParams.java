package com.rajkumar.batchengine.job;

/** Names of the job parameters used across the engine. */
public final class JobParams {

    /** Business date being processed (yyyy-MM-dd). Identifying: one job instance per date. */
    public static final String RUN_DATE = "runDate";
    /** Folder holding that day's feed files. */
    public static final String INPUT_DIR = "inputDir";
    public static final String TRIGGER_SOURCE = "triggerSource";
    public static final String TRIGGERED_BY = "triggeredBy";
    /** Added only for a forced re-run of a date that already completed. */
    public static final String RUN_ID = "run.id";

    private JobParams() {
    }
}
