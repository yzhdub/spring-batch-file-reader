package com.cs.coding.config;

import javax.sql.DataSource;

import com.cs.coding.exceptions.ResourceNotFoundException;
import com.cs.coding.exceptions.ResourceNotSpecifiedException;
import com.cs.coding.listener.JobCompletionNotificationListener;
import com.cs.coding.reader.ServerLogJsonLineMapper;
import com.cs.coding.processor.ServerLogProcessor;
import com.cs.coding.models.EventDetail;
import com.cs.coding.models.ServerLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

@Configuration
@EnableBatchProcessing
public class CSBatchConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CSBatchConfiguration.class);

    @Autowired
    public JobBuilderFactory jobBuilderFactory;

    @Autowired
    public StepBuilderFactory stepBuilderFactory;


    @StepScope
    @Bean
    public FlatFileItemReader<ServerLog> serverLogReader(@Value("#{jobParameters[filepath]}") String path) {
        if (path == null || path.length() == 0) {
            throw new ResourceNotSpecifiedException("File resource not specified. Please specify with filepath=<full_path>");
        }

        FlatFileItemReader<ServerLog> reader = new FlatFileItemReader<>();
        FileSystemResource file = new FileSystemResource(path);
        if (!file.exists() || !file.isFile()) {
            throw new ResourceNotFoundException("File resource not exist at " + path);
        }

        reader.setResource(file);

        ServerLogJsonLineMapper lineMapper = new ServerLogJsonLineMapper();

        reader.setLineMapper(lineMapper);

        return reader;
    }

    @Bean
    public ServerLogProcessor serverLogProcessor() {
        return new ServerLogProcessor();
    }

    @Bean
    public JdbcBatchItemWriter<EventDetail> eventDetailWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<EventDetail>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO EVENTDETAIL (id, duration, type, host, alert) VALUES (:id, :duration, :type, :host, :alert)")
                .dataSource(dataSource)
                .build();
    }
    @Bean
    public Job parseServerLogJob(JobCompletionNotificationListener listener, Step step1) {
        return jobBuilderFactory.get("parseServerLogJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .flow(step1)
                .end()
                .build();
    }

    @Bean
    public Step step(JdbcBatchItemWriter<EventDetail> eventDetailWriter) {
        return stepBuilderFactory.get("step")
                .<ServerLog, EventDetail> chunk(10)
                .reader(serverLogReader(null))
                .processor(serverLogProcessor())
                .writer(eventDetailWriter)
                .build();
    }
}