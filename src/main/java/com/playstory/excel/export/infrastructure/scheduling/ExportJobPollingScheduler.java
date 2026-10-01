package com.playstory.excel.export.infrastructure.scheduling;

import com.playstory.excel.config.ExportProperties;
import com.playstory.excel.export.application.ExportJobProcessor;
import com.playstory.excel.export.port.ExcelFileExporter;
import com.playstory.excel.export.port.ExportFileStorage;
import com.playstory.excel.export.port.ExportJobStore;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Spring adapter that drives the application service on startup and on a fixed delay. */
@Component
public class ExportJobPollingScheduler {
    private static final Logger log = LoggerFactory.getLogger(ExportJobPollingScheduler.class);
    private final ExportJobProcessor processor;
    private final ExportFileStorage fileStorage;
    private final ExportProperties properties;

    public ExportJobPollingScheduler(
            ExportJobStore jobStore,
            ExportFileStorage fileStorage,
            ExcelFileExporter excelFileExporter,
            ExportProperties properties) {
        this.processor = new ExportJobProcessor(jobStore, fileStorage, excelFileExporter);
        this.fileStorage = fileStorage;
        this.properties = properties;
    }

    @PostConstruct
    void initialize() throws IOException {
        fileStorage.ensureDirectories();
        processor.recover();
    }

    @Scheduled(fixedDelayString = "${app.export.worker-delay-ms:1000}")
    void poll() {
        try {
            processor.processOne(properties.leaseMinutes()).ifPresent(result -> {
                if (result.status() == ExportJobProcessor.ProcessingResult.Status.COMPLETED) {
                    log.info("export job completed: jobId={}, rows={}", result.jobId(), result.rowCount());
                } else {
                    log.warn("export job failed: jobId={}, errorCode={}", result.jobId(), result.errorCode());
                }
            });
        } catch (RuntimeException exception) {
            log.error("export job polling failed; the next fixed-delay run will retry", exception);
        }
    }
}
