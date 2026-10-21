package com.rajkumar.batchengine.domain;

/** Thrown by the processor for a record that fails validation. The record is skipped and audited. */
public class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
