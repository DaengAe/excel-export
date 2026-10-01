package com.playstory.excel.export.port;

import com.playstory.excel.export.domain.ExportJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port for the durable job queue and its state transitions. */
public interface ExportJobStore {
    ExportJob create();
    List<ExportJob> findLatest(int limit);
    Optional<ExportJob> findById(UUID id);
    Optional<ExportJob> claimOne(int leaseMinutes);
    void markDone(UUID id, String logicalFilePath);
    void markFailed(UUID id, String code, String message);
    int requeueExpiredProcessing();
    List<ExportJob> findDone();
    List<ExportJob> findProcessing();
    void markDoneFromReconcile(UUID id, String logicalFilePath);
    void markFailedFromReconcile(UUID id, String code, String message);
}
