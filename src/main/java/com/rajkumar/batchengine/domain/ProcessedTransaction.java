package com.rajkumar.batchengine.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A validated, enriched transaction ready to be written to the warehouse table. */
public record ProcessedTransaction(
        String txnId,
        String accountId,
        LocalDate businessDate,
        LocalDate txnDate,
        BigDecimal originalAmount,
        String currency,
        BigDecimal amountGbp,
        String description,
        String sourceFile,
        LocalDateTime processedAt) {
}
