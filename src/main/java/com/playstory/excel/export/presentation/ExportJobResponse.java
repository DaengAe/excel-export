package com.playstory.excel.export.presentation;

import com.playstory.excel.export.domain.ExportJob;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/** HTTP response contract. Domain records are not serialized directly. */
public record ExportJobResponse(
        UUID id,
        String status,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime leaseUntil,
        String logicalFilePath,
        String errorCode,
        String errorMessage) {

    public static ExportJobResponse from(ExportJob job) {
        return new ExportJobResponse(
                job.id(),
                job.status().name().toLowerCase(Locale.ROOT),
                job.requestedAt(),
                job.startedAt(),
                job.finishedAt(),
                job.leaseUntil(),
                job.logicalFilePath(),
                job.errorCode(),
                job.errorMessage());
    }
}
