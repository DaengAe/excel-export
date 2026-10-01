package com.playstory.excel.export.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/** Port for the lifecycle of a generated export file. */
public interface ExportFileStorage {
    void ensureDirectories() throws IOException;
    Path temporaryPath(UUID jobId);
    Path finalPath(UUID jobId);
    String logicalPath(UUID jobId);
    Path moveToFinal(UUID jobId) throws IOException;
    boolean isValidFinalFile(UUID jobId);
    Path requireFinalFile(UUID jobId);
    void discardTemporary(UUID jobId) throws IOException;
}
