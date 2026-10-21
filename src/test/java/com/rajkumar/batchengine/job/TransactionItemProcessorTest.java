package com.rajkumar.batchengine.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.rajkumar.batchengine.domain.InvalidTransactionException;
import com.rajkumar.batchengine.domain.ProcessedTransaction;
import com.rajkumar.batchengine.domain.TransactionRecord;
import com.rajkumar.batchengine.service.ExchangeRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionItemProcessorTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 20);

    @Mock
    private ExchangeRateService exchangeRateService;

    private TransactionItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TransactionItemProcessor(exchangeRateService, BUSINESS_DATE, "feed.csv");
        when(exchangeRateService.rateToGbp(anyString())).thenReturn(Optional.empty());
        when(exchangeRateService.rateToGbp("USD")).thenReturn(Optional.of(new BigDecimal("0.77")));
        when(exchangeRateService.rateToGbp("GBP")).thenReturn(Optional.of(BigDecimal.ONE));
    }

    @Test
    void convertsValidRecordToGbp() {
        TransactionRecord record = new TransactionRecord("T1", "gb10000001", "2026-10-20", "100.00", "usd",
                "  Online subscription ");

        ProcessedTransaction result = processor.process(record);

        assertThat(result.txnId()).isEqualTo("T1");
        assertThat(result.accountId()).isEqualTo("GB10000001");
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.amountGbp()).isEqualByComparingTo("77.00");
        assertThat(result.businessDate()).isEqualTo(BUSINESS_DATE);
        assertThat(result.description()).isEqualTo("Online subscription");
        assertThat(result.sourceFile()).isEqualTo("feed.csv");
    }

    @Test
    void roundsHalfEvenToPence() {
        TransactionRecord record = new TransactionRecord("T2", "GB10000001", "2026-10-20", "0.125", "GBP", null);

        assertThat(processor.process(record).amountGbp()).isEqualByComparingTo("0.12");
    }

    @ParameterizedTest(name = "{6}")
    @CsvSource({
            "'',     GB10000001, 2026-10-20, 10, GBP, txnId is missing,               missing txn id",
            "T3,     GB1,        2026-10-20, 10, GBP, must be 2 letters + 8 digits,   short account id",
            "T4,     GB10000001, 20/10/2026, 10, GBP, is not yyyy-MM-dd,              bad date format",
            "T5,     GB10000001, 2026-10-21, 10, GBP, is after business date,         future date",
            "T6,     GB10000001, 2026-10-20, abc, GBP, is not a number,               non numeric amount",
            "T7,     GB10000001, 2026-10-20, -1, GBP, amount must be positive,        negative amount",
            "T8,     GB10000001, 2026-10-20, 10, JPY, no FX rate for currency JPY,    unknown currency",
            "T9,     GB10000001, 2026-10-20, 10, US1, is not an ISO code,             bad currency code"
    })
    void rejectsInvalidRecords(String txnId, String accountId, String txnDate, String amount, String currency,
                               String expectedMessage, String caseName) {
        TransactionRecord record = new TransactionRecord(txnId, accountId, txnDate, amount, currency, "x");

        assertThatThrownBy(() -> processor.process(record))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining(expectedMessage);
    }
}
