package com.playstory.excel.job;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportJob(
        UUID id,
        JobStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime leaseUntil,
        String logicalFilePath,
        String errorCode,
        String errorMessage) { }
