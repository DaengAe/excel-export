package com.playstory.excel.web;

import com.playstory.excel.job.ExportFileStore;
import com.playstory.excel.job.ExportJob;
import com.playstory.excel.job.ExportJobRepository;
import com.playstory.excel.job.JobStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
    private final ExportJobRepository repository;
    private final ExportFileStore fileStore;

    public ExportJobController(ExportJobRepository repository, ExportFileStore fileStore) {
        this.repository = repository;
        this.fileStore = fileStore;
    }

    @PostMapping
    public ResponseEntity<ExportJob> create() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(repository.create());
    }

    @GetMapping
    public List<ExportJob> list() { return repository.findLatest(50); }

    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable UUID id) {
        ExportJob job = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
        if (job.status() != JobStatus.DONE) throw new ResponseStatusException(HttpStatus.CONFLICT, "JOB_NOT_COMPLETED");
        Path path;
        try { path = fileStore.requireFinalFile(id); }
        catch (ExportFileStore.ExportFileUnavailableException exception) { throw new ResponseStatusException(HttpStatus.CONFLICT, "EXPORT_FILE_UNAVAILABLE"); }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export-" + id + ".xlsx")
                .body(new FileSystemResource(path));
    }
}
