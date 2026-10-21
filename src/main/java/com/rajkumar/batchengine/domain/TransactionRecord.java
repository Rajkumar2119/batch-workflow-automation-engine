package com.rajkumar.batchengine.domain;

/** One raw line from an inbound CSV feed, before validation. All fields are kept as text. */
public record TransactionRecord(
        String txnId,
        String accountId,
        String txnDate,
        String amount,
        String currency,
        String description) {

    public String toCsv() {
        return String.join(",", nullSafe(txnId), nullSafe(accountId), nullSafe(txnDate),
                nullSafe(amount), nullSafe(currency), nullSafe(description));
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
