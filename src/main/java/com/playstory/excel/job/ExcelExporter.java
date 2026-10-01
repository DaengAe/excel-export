package com.playstory.excel.job;

import com.playstory.excel.config.ExportProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ExcelExporter {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ExportProperties properties;

    public ExcelExporter(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, ExportProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    /** A read-only transaction keeps PostgreSQL cursor streaming enabled instead of loading all rows. */
    public long exportTo(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        AtomicLong rows = new AtomicLong();
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(properties.rowWindowSize())) {
            workbook.setCompressTempFiles(true);
            var sheet = workbook.createSheet("orders");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("CUSTOMER_NAME");
            header.createCell(2).setCellValue("EMAIL");
            header.createCell(3).setCellValue("AMOUNT");
            header.createCell(4).setCellValue("ORDERED_AT");

            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.query(connection -> {
                PreparedStatement statement = connection.prepareStatement("SELECT id, customer_name, email, amount, ordered_at FROM order_data ORDER BY id");
                statement.setFetchSize(properties.fetchSize());
                statement.setQueryTimeout(120);
                return statement;
            }, resultSet -> {
                long index = rows.incrementAndGet();
                Row row = sheet.createRow((int) index);
                row.createCell(0).setCellValue(resultSet.getLong("id"));
                row.createCell(1).setCellValue(resultSet.getString("customer_name"));
                row.createCell(2).setCellValue(resultSet.getString("email"));
                row.createCell(3).setCellValue(resultSet.getBigDecimal("amount").doubleValue());
                OffsetDateTime orderedAt = resultSet.getObject("ordered_at", OffsetDateTime.class);
                row.createCell(4).setCellValue(orderedAt.toString());
            }));
            try (var output = Files.newOutputStream(target)) { workbook.write(output); }
            return rows.get();
        }
    }
}
