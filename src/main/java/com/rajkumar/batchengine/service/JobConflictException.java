package com.rajkumar.batchengine.service;

/** The requested action clashes with the job's current state (already running, already complete...). */
public class JobConflictException extends RuntimeException {

    public JobConflictException(String message, Throwable cause) {
        super(message, cause);
    }

    public JobConflictException(String message) {
        super(message);
    }
}
