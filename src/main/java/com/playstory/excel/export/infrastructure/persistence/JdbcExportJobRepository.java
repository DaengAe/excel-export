package com.playstory.excel.export.infrastructure.persistence;

import com.playstory.excel.export.domain.ExportJob;
import com.playstory.excel.export.domain.JobStatus;
import com.playstory.excel.export.port.ExportJobStore;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExportJobRepository implements ExportJobStore {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ExportJob> mapper = (rs, rowNum) -> new ExportJob(
            rs.getObject("id", UUID.class),
            JobStatus.valueOf(rs.getString("status")),
            rs.getObject("requested_at", OffsetDateTime.class),
            rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("finished_at", OffsetDateTime.class),
            rs.getObject("lease_until", OffsetDateTime.class),
            rs.getString("logical_file_path"),
            rs.getString("error_code"),
            rs.getString("error_message"));

    public JdbcExportJobRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public ExportJob create() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO export_jobs(id, status, requested_at) VALUES (?, 'PENDING', now())", id);
        return findById(id).orElseThrow();
    }

    public List<ExportJob> findLatest(int limit) {
        return jdbcTemplate.query("SELECT * FROM export_jobs ORDER BY requested_at DESC LIMIT ?", mapper, limit);
    }

    public Optional<ExportJob> findById(UUID id) {
        return jdbcTemplate.query("SELECT * FROM export_jobs WHERE id = ?", mapper, id).stream().findFirst();
    }

    /** Atomically claims one job; the transaction is intentionally short. */
    public Optional<ExportJob> claimOne(int leaseMinutes) {
        String sql = """
                WITH candidate AS (
                    SELECT id FROM export_jobs
                    WHERE status = 'PENDING'
                    ORDER BY requested_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE export_jobs
                SET status = 'PROCESSING', started_at = now(), lease_until = now() + (? * interval '1 minute'),
                    error_code = NULL, error_message = NULL
                WHERE id = (SELECT id FROM candidate)
                RETURNING *
                """;
        return jdbcTemplate.query(sql, mapper, leaseMinutes).stream().findFirst();
    }

    public void markDone(UUID id, String logicalFilePath) {
        jdbcTemplate.update("UPDATE export_jobs SET status='DONE', finished_at=now(), lease_until=NULL, logical_file_path=?, error_code=NULL, error_message=NULL WHERE id=? AND status='PROCESSING'", logicalFilePath, id);
    }

    public void markFailed(UUID id, String code, String message) {
        jdbcTemplate.update("UPDATE export_jobs SET status='FAILED', finished_at=now(), lease_until=NULL, error_code=?, error_message=? WHERE id=? AND status='PROCESSING'", code, abbreviate(message), id);
    }

    public int requeueExpiredProcessing() {
        return jdbcTemplate.update("UPDATE export_jobs SET status='PENDING', started_at=NULL, lease_until=NULL, error_code='LEASE_EXPIRED', error_message='Worker lease expired; job will be regenerated.' WHERE status='PROCESSING' AND lease_until < now()");
    }

    public List<ExportJob> findDone() {
        return jdbcTemplate.query("SELECT * FROM export_jobs WHERE status='DONE'", mapper);
    }

    public List<ExportJob> findProcessing() {
        return jdbcTemplate.query("SELECT * FROM export_jobs WHERE status='PROCESSING'", mapper);
    }

    /** Repairs the file-moved / DB-not-yet-marked-DONE boundary after a restart. */
    public void markDoneFromReconcile(UUID id, String logicalFilePath) {
        jdbcTemplate.update("UPDATE export_jobs SET status='DONE', finished_at=now(), lease_until=NULL, logical_file_path=?, error_code=NULL, error_message=NULL WHERE id=? AND status='PROCESSING'", logicalFilePath, id);
    }

    public void markFailedFromReconcile(UUID id, String code, String message) {
        jdbcTemplate.update("UPDATE export_jobs SET status='FAILED', finished_at=now(), lease_until=NULL, error_code=?, error_message=? WHERE id=?", code, message, id);
    }

    private String abbreviate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
