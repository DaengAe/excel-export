package com.playstory.excel.export.presentation;

import com.playstory.excel.export.domain.ExportJob;
import com.playstory.excel.export.domain.JobStatus;
import com.playstory.excel.export.port.ExportFileStorage;
import com.playstory.excel.export.port.ExportFileUnavailableException;
import com.playstory.excel.export.port.ExportJobStore;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/export-jobs")
public class ExportJobController {
    private final ExportJobStore jobStore;
    private final ExportFileStorage fileStorage;

    public ExportJobController(ExportJobStore jobStore, ExportFileStorage fileStorage) {
        this.jobStore = jobStore;
        this.fileStorage = fileStorage;
    }

    @PostMapping
    public ResponseEntity<ExportJobResponse> create() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ExportJobResponse.from(jobStore.create()));
    }

    @GetMapping
    public List<ExportJobResponse> list() { return jobStore.findLatest(50).stream().map(ExportJobResponse::from).toList(); }

    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable UUID id) {
        ExportJob job = jobStore.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
        if (job.status() != JobStatus.DONE) throw new ResponseStatusException(HttpStatus.CONFLICT, "JOB_NOT_COMPLETED");
        Path path;
        try { path = fileStorage.requireFinalFile(id); }
        catch (ExportFileUnavailableException exception) { throw new ResponseStatusException(HttpStatus.CONFLICT, "EXPORT_FILE_UNAVAILABLE"); }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export-" + id + ".xlsx")
                .body(new FileSystemResource(path));
    }
}
