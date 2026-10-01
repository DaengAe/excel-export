package com.playstory.excel.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.playstory.excel.config.ExportProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportFileStoreTest {
    @TempDir
    Path directory;

    @Test
    void finalizedXlsxIsRecognizedAsDownloadable() throws Exception {
        ExportFileStore store = new ExportFileStore(new ExportProperties(directory, 1000, 100, 10));
        store.ensureDirectories();
        UUID jobId = UUID.randomUUID();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); var output = Files.newOutputStream(store.temporaryPath(jobId))) {
            workbook.createSheet("orders").createRow(0).createCell(0).setCellValue("id");
            workbook.write(output);
        }

        store.moveToFinal(jobId);

        assertThat(store.isValidFinalFile(jobId)).isTrue();
        assertThat(store.requireFinalFile(jobId)).isEqualTo(store.finalPath(jobId));
    }
}
