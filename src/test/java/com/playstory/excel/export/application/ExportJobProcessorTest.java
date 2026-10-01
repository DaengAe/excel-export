package com.playstory.excel.export.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.playstory.excel.export.domain.ExportJob;
import com.playstory.excel.export.domain.JobStatus;
import com.playstory.excel.export.port.ExcelFileExporter;
import com.playstory.excel.export.port.ExportFileStorage;
import com.playstory.excel.export.port.ExportJobStore;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportJobProcessorTest {
    @Mock ExportJobStore jobStore;
    @Mock ExportFileStorage fileStorage;
    @Mock ExcelFileExporter excelFileExporter;

    @Test
    void claimedJobIsMarkedDoneOnlyAfterAValidXlsxExists() throws Exception {
        UUID jobId = UUID.randomUUID();
        ExportJob job = new ExportJob(jobId, JobStatus.PROCESSING, OffsetDateTime.now(), OffsetDateTime.now(), null, OffsetDateTime.now().plusMinutes(10), null, null, null);
        when(jobStore.findProcessing()).thenReturn(java.util.List.of());
        when(jobStore.requeueExpiredProcessing()).thenReturn(0);
        when(jobStore.claimOne(10)).thenReturn(Optional.of(job));
        when(fileStorage.temporaryPath(jobId)).thenReturn(Path.of("/tmp", jobId + ".xlsx"));
        when(fileStorage.isValidFinalFile(jobId)).thenReturn(true);
        when(fileStorage.logicalPath(jobId)).thenReturn("exports/" + jobId + ".xlsx");
        when(excelFileExporter.exportTo(any())).thenReturn(100_000L);

        ExportJobProcessor processor = new ExportJobProcessor(jobStore, fileStorage, excelFileExporter);

        var result = processor.processOne(10);

        assertThat(result).hasValueSatisfying(value -> {
            assertThat(value.status()).isEqualTo(ExportJobProcessor.ProcessingResult.Status.COMPLETED);
            assertThat(value.rowCount()).isEqualTo(100_000L);
        });
        verify(fileStorage).moveToFinal(jobId);
        verify(jobStore).markDone(jobId, "exports/" + jobId + ".xlsx");
    }
}
