package com.playstory.excel.export.domain;

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
