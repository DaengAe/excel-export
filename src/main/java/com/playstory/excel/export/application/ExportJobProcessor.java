package com.playstory.excel.export.application;

import com.playstory.excel.export.domain.ExportJob;
import com.playstory.excel.export.port.ExcelFileExporter;
import com.playstory.excel.export.port.ExportFileStorage;
import com.playstory.excel.export.port.ExportJobStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Application service that owns export job state transitions and recovery policy.
 * It intentionally has no Spring scheduling, configuration, logging, or HTTP dependency.
 */
public class ExportJobProcessor {
    private final ExportJobStore jobStore;
    private final ExportFileStorage fileStorage;
    private final ExcelFileExporter excelFileExporter;

    public ExportJobProcessor(ExportJobStore jobStore, ExportFileStorage fileStorage, ExcelFileExporter excelFileExporter) {
        this.jobStore = jobStore;
        this.fileStorage = fileStorage;
        this.excelFileExporter = excelFileExporter;
    }

    public void recover() {
        jobStore.findDone().stream()
                .filter(job -> !fileStorage.isValidFinalFile(job.id()))
                .forEach(job -> jobStore.markFailedFromReconcile(job.id(), "FILE_MISSING_OR_CORRUPT", "Completed file is unavailable."));
        reconcileProcessingFiles();
        jobStore.requeueExpiredProcessing();
    }

    public Optional<ProcessingResult> processOne(int leaseMinutes) {
        reconcileProcessingFiles();
        jobStore.requeueExpiredProcessing();
        return jobStore.claimOne(leaseMinutes).map(this::generate);
    }

    private ProcessingResult generate(ExportJob job) {
        try {
            Path temporary = fileStorage.temporaryPath(job.id());
            fileStorage.discardTemporary(job.id());
            long rowCount = excelFileExporter.exportTo(temporary);
            fileStorage.moveToFinal(job.id());
            if (!fileStorage.isValidFinalFile(job.id())) throw new IOException("Generated xlsx validation failed");
            jobStore.markDone(job.id(), fileStorage.logicalPath(job.id()));
            return ProcessingResult.completed(job.id(), rowCount);
        } catch (Exception exception) {
            try {
                fileStorage.discardTemporary(job.id());
            } catch (IOException ignored) {
                // The original export failure is retained in the job state below.
            }
            jobStore.markFailed(job.id(), classify(exception), exception.getMessage());
            return ProcessingResult.failed(job.id(), classify(exception));
        }
    }

    private void reconcileProcessingFiles() {
        jobStore.findProcessing().stream()
                .filter(job -> fileStorage.isValidFinalFile(job.id()))
                .forEach(job -> jobStore.markDoneFromReconcile(job.id(), fileStorage.logicalPath(job.id())));
    }

    private String classify(Exception exception) {
        if (exception instanceof IOException) return "FILE_WRITE_FAILED";
        return "EXPORT_FAILED";
    }

    public record ProcessingResult(java.util.UUID jobId, Status status, long rowCount, String errorCode) {
        public static ProcessingResult completed(java.util.UUID jobId, long rowCount) {
            return new ProcessingResult(jobId, Status.COMPLETED, rowCount, null);
        }

        public static ProcessingResult failed(java.util.UUID jobId, String errorCode) {
            return new ProcessingResult(jobId, Status.FAILED, 0, errorCode);
        }

        public enum Status { COMPLETED, FAILED }
    }
}
