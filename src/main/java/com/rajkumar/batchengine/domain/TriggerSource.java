package com.rajkumar.batchengine.domain;

/** Who asked for a job run. Kept on every audit record. */
public enum TriggerSource {
    API,
    SCHEDULER,
    CONTROL_M
}
