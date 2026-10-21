package com.rajkumar.batchengine.job;

import com.rajkumar.batchengine.domain.ProcessedTransaction;
import com.rajkumar.batchengine.domain.TransactionRecord;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Stores every skipped record in {@code rejected_record} with the reason, so bad data is
 * never silently dropped and can be corrected and replayed.
 */
public class RejectedRecordListener implements SkipListener<TransactionRecord, ProcessedTransaction> {

    private static final Logger log = LoggerFactory.getLogger(RejectedRecordListener.class);
    private static final String INSERT_SQL = """
            INSERT INTO rejected_record
                (job_execution_id, business_date, source_file, line_number, raw_record, reason, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Long jobExecutionId;
    private final LocalDate businessDate;
    private final String sourceFile;

    public RejectedRecordListener(JdbcTemplate jdbcTemplate, Long jobExecutionId, LocalDate businessDate,
                                  String sourceFile) {
        this.jdbcTemplate = jdbcTemplate;
        this.jobExecutionId = jobExecutionId;
        this.businessDate = businessDate;
        this.sourceFile = sourceFile;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        if (t instanceof FlatFileParseException parseException) {
            save(parseException.getLineNumber(), parseException.getInput(), "unparseable line");
        } else {
            save(null, null, "read error: " + t.getMessage());
        }
    }

    @Override
    public void onSkipInProcess(TransactionRecord item, Throwable t) {
        save(null, item.toCsv(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(ProcessedTransaction item, Throwable t) {
        save(null, item.txnId(), "write rejected (likely duplicate txnId): " + rootMessage(t));
    }

    private void save(Integer lineNumber, String raw, String reason) {
        log.warn("Rejected record in {} line {}: {}", sourceFile, lineNumber, reason);
        jdbcTemplate.update(INSERT_SQL, jobExecutionId, Date.valueOf(businessDate), shortName(sourceFile),
                lineNumber, truncate(raw, 1000), truncate(reason, 500), Timestamp.valueOf(LocalDateTime.now()));
    }

    static String shortName(String resourceUrl) {
        if (resourceUrl == null) {
            return null;
        }
        int slash = Math.max(resourceUrl.lastIndexOf('/'), resourceUrl.lastIndexOf('\\'));
        return slash >= 0 ? resourceUrl.substring(slash + 1) : resourceUrl;
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
