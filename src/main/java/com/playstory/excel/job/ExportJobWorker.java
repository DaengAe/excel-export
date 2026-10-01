package com.playstory.excel.job;

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
    private final ExportJobRepository repository;
    private final ExportFileStore fileStore;
    private final ExcelExporter exporter;
    private final ExportProperties properties;

    public ExportJobWorker(ExportJobRepository repository, ExportFileStore fileStore, ExcelExporter exporter, ExportProperties properties) {
        this.repository = repository;
        this.fileStore = fileStore;
        this.exporter = exporter;
        this.properties = properties;
    }

    @PostConstruct
    void prepare() throws IOException {
        fileStore.ensureDirectories();
        reconcileFiles();
    }

    @Scheduled(fixedDelayString = "${app.export.worker-delay-ms:1000}")
    public void processOne() {
        reconcileProcessingFiles();
        repository.requeueExpiredProcessing();
        Optional<ExportJob> claimed = repository.claimOne(properties.leaseMinutes());
        claimed.ifPresent(this::generate);
    }

    private void generate(ExportJob job) {
        try {
            Path temporary = fileStore.temporaryPath(job.id());
            fileStore.discardTemporary(job.id());
            long rowCount = exporter.exportTo(temporary);
            fileStore.moveToFinal(job.id());
            if (!fileStore.isValidFinalFile(job.id())) throw new IOException("Generated xlsx validation failed");
            repository.markDone(job.id(), fileStore.logicalPath(job.id()));
            log.info("export job completed: jobId={}, rows={}", job.id(), rowCount);
        } catch (Exception exception) {
            log.warn("export job failed: jobId={}", job.id(), exception);
            try { fileStore.discardTemporary(job.id()); } catch (IOException cleanupException) { log.warn("temporary cleanup failed: jobId={}", job.id(), cleanupException); }
            repository.markFailed(job.id(), classify(exception), exception.getMessage());
        }
    }

    private void reconcileFiles() {
        repository.findDone().stream()
                .filter(job -> !fileStore.isValidFinalFile(job.id()))
                .forEach(job -> repository.markFailedFromReconcile(job.id(), "FILE_MISSING_OR_CORRUPT", "Completed file is unavailable."));
        reconcileProcessingFiles();
        int requeued = repository.requeueExpiredProcessing();
        if (requeued > 0) log.info("requeued expired export jobs: count={}", requeued);
    }

    private void reconcileProcessingFiles() {
        repository.findProcessing().stream()
                .filter(job -> fileStore.isValidFinalFile(job.id()))
                .forEach(job -> {
                    repository.markDoneFromReconcile(job.id(), fileStore.logicalPath(job.id()));
                    log.info("reconciled completed export file: jobId={}", job.id());
                });
    }

    private String classify(Exception exception) {
        if (exception instanceof IOException) return "FILE_WRITE_FAILED";
        return "EXPORT_FAILED";
    }
}
