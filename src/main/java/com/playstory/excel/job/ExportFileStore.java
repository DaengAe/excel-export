package com.playstory.excel.job;

import com.playstory.excel.config.ExportProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Component;

@Component
public class ExportFileStore {
    private final Path root;
    private final Path temporaryRoot;

    public ExportFileStore(ExportProperties properties) {
        this.root = properties.directory();
        this.temporaryRoot = root.resolve(".tmp");
    }

    public void ensureDirectories() throws IOException {
        Files.createDirectories(root);
        Files.createDirectories(temporaryRoot);
    }

    public Path temporaryPath(UUID jobId) { return temporaryRoot.resolve(jobId + ".xlsx"); }
    public Path finalPath(UUID jobId) { return root.resolve(jobId + ".xlsx"); }
    public String logicalPath(UUID jobId) { return "exports/" + jobId + ".xlsx"; }

    public Path moveToFinal(UUID jobId) throws IOException {
        try {
            return Files.move(temporaryPath(jobId), finalPath(jobId), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            // The Docker named volume is normally one filesystem. This fallback keeps local execution portable.
            return Files.move(temporaryPath(jobId), finalPath(jobId), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean isValidFinalFile(UUID jobId) {
        Path path = finalPath(jobId);
        if (!Files.isRegularFile(path)) return false;
        try (ZipFile ignored = new ZipFile(path.toFile())) {
            return Files.size(path) > 0;
        } catch (IOException exception) {
            return false;
        }
    }

    public Path requireFinalFile(UUID jobId) {
        Path path = finalPath(jobId);
        if (!isValidFinalFile(jobId)) throw new ExportFileUnavailableException();
        return path;
    }

    public void discardTemporary(UUID jobId) throws IOException {
        Files.deleteIfExists(temporaryPath(jobId));
    }

    public static class ExportFileUnavailableException extends RuntimeException { }
}
