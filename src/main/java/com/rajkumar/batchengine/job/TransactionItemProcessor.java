package com.rajkumar.batchengine.job;

import com.rajkumar.batchengine.domain.InvalidTransactionException;
import com.rajkumar.batchengine.domain.ProcessedTransaction;
import com.rajkumar.batchengine.domain.TransactionRecord;
import com.rajkumar.batchengine.service.ExchangeRateService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.batch.item.ItemProcessor;

/**
 * Validates a raw feed line and enriches it with the GBP amount.
 * Invalid lines throw {@link InvalidTransactionException}; the step is configured to skip
 * those and the {@link RejectedRecordListener} stores them for follow-up.
 */
public class TransactionItemProcessor implements ItemProcessor<TransactionRecord, ProcessedTransaction> {

    private static final Pattern ACCOUNT_ID = Pattern.compile("^[A-Z]{2}\\d{8}$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Za-z]{3}$");

    private final ExchangeRateService exchangeRateService;
    private final LocalDate businessDate;
    private final String sourceFile;

    public TransactionItemProcessor(ExchangeRateService exchangeRateService, LocalDate businessDate,
                                    String sourceFile) {
        this.exchangeRateService = exchangeRateService;
        this.businessDate = businessDate;
        this.sourceFile = sourceFile;
    }

    @Override
    public ProcessedTransaction process(TransactionRecord item) {
        String txnId = require(item.txnId(), "txnId");
        String accountId = require(item.accountId(), "accountId").toUpperCase(Locale.ROOT);
        if (!ACCOUNT_ID.matcher(accountId).matches()) {
            throw new InvalidTransactionException("accountId '" + accountId + "' must be 2 letters + 8 digits");
        }

        LocalDate txnDate = parseDate(require(item.txnDate(), "txnDate"));
        if (txnDate.isAfter(businessDate)) {
            throw new InvalidTransactionException("txnDate " + txnDate + " is after business date " + businessDate);
        }

        BigDecimal amount = parseAmount(require(item.amount(), "amount"));

        String currency = require(item.currency(), "currency").toUpperCase(Locale.ROOT);
        if (!CURRENCY.matcher(currency).matches()) {
            throw new InvalidTransactionException("currency '" + currency + "' is not an ISO code");
        }
        BigDecimal rate = exchangeRateService.rateToGbp(currency)
                .orElseThrow(() -> new InvalidTransactionException("no FX rate for currency " + currency));

        BigDecimal amountGbp = amount.multiply(rate).setScale(2, RoundingMode.HALF_EVEN);
        String description = item.description() == null ? null : item.description().trim();

        return new ProcessedTransaction(txnId, accountId, businessDate, txnDate, amount, currency,
                amountGbp, description, sourceFile, LocalDateTime.now());
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidTransactionException(field + " is missing");
        }
        return value.trim();
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidTransactionException("txnDate '" + value + "' is not yyyy-MM-dd");
        }
    }

    private static BigDecimal parseAmount(String value) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new InvalidTransactionException("amount '" + value + "' is not a number");
        }
        if (amount.signum() <= 0) {
            throw new InvalidTransactionException("amount must be positive");
        }
        return amount;
    }
}
