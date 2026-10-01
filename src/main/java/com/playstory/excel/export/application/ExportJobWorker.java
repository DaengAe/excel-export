package com.playstory.excel.export.application;

import com.playstory.excel.export.domain.ExportJob;
import com.playstory.excel.export.port.ExcelFileExporter;
import com.playstory.excel.export.port.ExportFileStorage;
import com.playstory.excel.export.port.ExportJobStore;

import com.playstory.excel.config.ExportProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExportJobWorker {
    private static final Logger log = LoggerFactory.getLogger(ExportJobWorker.class);
    private final ExportJobStore jobStore;
    private final ExportFileStorage fileStorage;
    private final ExcelFileExporter excelFileExporter;
    private final ExportProperties properties;

    public ExportJobWorker(ExportJobStore jobStore, ExportFileStorage fileStorage, ExcelFileExporter excelFileExporter, ExportProperties properties) {
        this.jobStore = jobStore;
        this.fileStorage = fileStorage;
        this.excelFileExporter = excelFileExporter;
        this.properties = properties;
    }

    @PostConstruct
    void prepare() throws IOException {
        fileStorage.ensureDirectories();
        reconcileFiles();
    }

    @Scheduled(fixedDelayString = "${app.export.worker-delay-ms:1000}")
    public void processOne() {
        reconcileProcessingFiles();
        jobStore.requeueExpiredProcessing();
        Optional<ExportJob> claimed = jobStore.claimOne(properties.leaseMinutes());
        claimed.ifPresent(this::generate);
    }

    private void generate(ExportJob job) {
        try {
            Path temporary = fileStorage.temporaryPath(job.id());
            fileStorage.discardTemporary(job.id());
            long rowCount = excelFileExporter.exportTo(temporary);
            fileStorage.moveToFinal(job.id());
            if (!fileStorage.isValidFinalFile(job.id())) throw new IOException("Generated xlsx validation failed");
            jobStore.markDone(job.id(), fileStorage.logicalPath(job.id()));
            log.info("export job completed: jobId={}, rows={}", job.id(), rowCount);
        } catch (Exception exception) {
            log.warn("export job failed: jobId={}", job.id(), exception);
            try { fileStorage.discardTemporary(job.id()); } catch (IOException cleanupException) { log.warn("temporary cleanup failed: jobId={}", job.id(), cleanupException); }
            jobStore.markFailed(job.id(), classify(exception), exception.getMessage());
        }
    }

    private void reconcileFiles() {
        jobStore.findDone().stream()
                .filter(job -> !fileStorage.isValidFinalFile(job.id()))
                .forEach(job -> jobStore.markFailedFromReconcile(job.id(), "FILE_MISSING_OR_CORRUPT", "Completed file is unavailable."));
        reconcileProcessingFiles();
        int requeued = jobStore.requeueExpiredProcessing();
        if (requeued > 0) log.info("requeued expired export jobs: count={}", requeued);
    }

    private void reconcileProcessingFiles() {
        jobStore.findProcessing().stream()
                .filter(job -> fileStorage.isValidFinalFile(job.id()))
                .forEach(job -> {
                    jobStore.markDoneFromReconcile(job.id(), fileStorage.logicalPath(job.id()));
                    log.info("reconciled completed export file: jobId={}", job.id());
                });
    }

    private String classify(Exception exception) {
        if (exception instanceof IOException) return "FILE_WRITE_FAILED";
        return "EXPORT_FAILED";
    }
}
