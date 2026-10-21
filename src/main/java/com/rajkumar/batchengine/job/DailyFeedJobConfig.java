package com.rajkumar.batchengine.job;

import com.rajkumar.batchengine.config.EngineProperties;
import com.rajkumar.batchengine.domain.InvalidTransactionException;
import com.rajkumar.batchengine.domain.ProcessedTransaction;
import com.rajkumar.batchengine.domain.TransactionRecord;
import com.rajkumar.batchengine.service.ExchangeRateService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.MultiResourcePartitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * dailyFeedJob
 * <pre>
 *   prepareFeedStep          check files arrived, clear old rows for the date (idempotent re-runs)
 *        |
 *   processFilesManager      one partition per CSV file, run in parallel on a thread pool
 *        |-- processFileWorker:partition0   read -> validate/enrich -> batch insert
 *        |-- processFileWorker:partition1
 *        `-- ...
 * </pre>
 */
@Configuration
public class DailyFeedJobConfig {

    public static final String JOB_NAME = "dailyFeedJob";

    private static final String INSERT_SQL = """
            INSERT INTO processed_transaction
                (txn_id, account_id, business_date, txn_date, original_amount, currency,
                 amount_gbp, description, source_file, processed_at)
            VALUES
                (:txnId, :accountId, :businessDate, :txnDate, :originalAmount, :currency,
                 :amountGbp, :description, :sourceFile, :processedAt)
            """;

    private final EngineProperties properties;

    public DailyFeedJobConfig(EngineProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Job dailyFeedJob(JobRepository jobRepository, Step prepareFeedStep, Step processFilesManager,
                            JobAuditListener auditListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(auditListener)
                .start(prepareFeedStep)
                .next(processFilesManager)
                .build();
    }

    // ---- step 1: prepare -------------------------------------------------------------------

    @Bean
    public Step prepareFeedStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                PrepareFeedTasklet prepareFeedTasklet) {
        return new StepBuilder("prepareFeedStep", jobRepository)
                .tasklet(prepareFeedTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public PrepareFeedTasklet prepareFeedTasklet(JdbcTemplate jdbcTemplate,
            @Value("#{jobParameters['" + JobParams.RUN_DATE + "']}") String runDate,
            @Value("#{jobParameters['" + JobParams.INPUT_DIR + "']}") String inputDir) {
        return new PrepareFeedTasklet(jdbcTemplate, LocalDate.parse(runDate), Path.of(inputDir));
    }

    // ---- step 2: partitioned processing ----------------------------------------------------

    @Bean
    public Step processFilesManager(JobRepository jobRepository, MultiResourcePartitioner feedFilePartitioner,
                                    Step processFileWorker,
                                    @Qualifier("partitionTaskExecutor") TaskExecutor partitionTaskExecutor) {
        return new StepBuilder("processFilesManager", jobRepository)
                .partitioner("processFileWorker", feedFilePartitioner)
                .step(processFileWorker)
                .gridSize(properties.gridSize())
                .taskExecutor(partitionTaskExecutor)
                .build();
    }

    /** One partition per CSV file in the run's input folder; each partition gets {@code fileName}. */
    @Bean
    @JobScope
    public MultiResourcePartitioner feedFilePartitioner(
            @Value("#{jobParameters['" + JobParams.INPUT_DIR + "']}") String inputDir) throws IOException {
        MultiResourcePartitioner partitioner = new MultiResourcePartitioner();
        String base = Path.of(inputDir).toAbsolutePath().toUri().toString();
        String pattern = (base.endsWith("/") ? base : base + "/") + "*.csv";
        partitioner.setResources(new PathMatchingResourcePatternResolver().getResources(pattern));
        return partitioner;
    }

    @Bean
    public Step processFileWorker(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                  FlatFileItemReader<TransactionRecord> feedFileReader,
                                  TransactionItemProcessor transactionItemProcessor,
                                  JdbcBatchItemWriter<ProcessedTransaction> processedTransactionWriter,
                                  RejectedRecordListener rejectedRecordListener) {
        return new StepBuilder("processFileWorker", jobRepository)
                .<TransactionRecord, ProcessedTransaction>chunk(properties.chunkSize(), transactionManager)
                .reader(feedFileReader)
                .processor(transactionItemProcessor)
                .writer(processedTransactionWriter)
                .faultTolerant()
                .skip(InvalidTransactionException.class)
                .skip(FlatFileParseException.class)
                .skip(DuplicateKeyException.class)
                .skipLimit(properties.skipLimit())
                .listener(rejectedRecordListener)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<TransactionRecord> feedFileReader(
            @Value("#{stepExecutionContext['fileName']}") String fileName) {
        return new FlatFileItemReaderBuilder<TransactionRecord>()
                .name("feedFileReader")
                .resource(new DefaultResourceLoader().getResource(fileName))
                .linesToSkip(1)
                .delimited()
                .names("txnId", "accountId", "txnDate", "amount", "currency", "description")
                .fieldSetMapper(fs -> new TransactionRecord(
                        fs.readString("txnId"),
                        fs.readString("accountId"),
                        fs.readString("txnDate"),
                        fs.readString("amount"),
                        fs.readString("currency"),
                        fs.readString("description")))
                .build();
    }

    @Bean
    @StepScope
    public TransactionItemProcessor transactionItemProcessor(ExchangeRateService exchangeRateService,
            @Value("#{jobParameters['" + JobParams.RUN_DATE + "']}") String runDate,
            @Value("#{stepExecutionContext['fileName']}") String fileName) {
        return new TransactionItemProcessor(exchangeRateService, LocalDate.parse(runDate),
                RejectedRecordListener.shortName(fileName));
    }

    @Bean
    public JdbcBatchItemWriter<ProcessedTransaction> processedTransactionWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<ProcessedTransaction>()
                .dataSource(dataSource)
                .sql(INSERT_SQL)
                .itemSqlParameterSourceProvider(item -> new MapSqlParameterSource()
                        .addValue("txnId", item.txnId())
                        .addValue("accountId", item.accountId())
                        .addValue("businessDate", item.businessDate())
                        .addValue("txnDate", item.txnDate())
                        .addValue("originalAmount", item.originalAmount())
                        .addValue("currency", item.currency())
                        .addValue("amountGbp", item.amountGbp())
                        .addValue("description", item.description())
                        .addValue("sourceFile", item.sourceFile())
                        .addValue("processedAt", item.processedAt()))
                .build();
    }

    @Bean
    @StepScope
    public RejectedRecordListener rejectedRecordListener(JdbcTemplate jdbcTemplate,
            @Value("#{stepExecution}") StepExecution stepExecution,
            @Value("#{jobParameters['" + JobParams.RUN_DATE + "']}") String runDate,
            @Value("#{stepExecutionContext['fileName']}") String fileName) {
        return new RejectedRecordListener(jdbcTemplate, stepExecution.getJobExecutionId(),
                LocalDate.parse(runDate), fileName);
    }
}
