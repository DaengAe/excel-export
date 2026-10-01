package com.playstory.excel.export.infrastructure.file;

import com.playstory.excel.export.port.ExcelFileExporter;

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
public class JdbcStreamingExcelExporter implements ExcelFileExporter {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ExportProperties properties;

    public JdbcStreamingExcelExporter(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, ExportProperties properties) {
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
            header.createCell(1).setCellValue("USER_NAME");
            header.createCell(2).setCellValue("PRODUCT_NAME");
            header.createCell(3).setCellValue("CATEGORY");
            header.createCell(4).setCellValue("AMOUNT");
            header.createCell(5).setCellValue("STATUS");
            header.createCell(6).setCellValue("ORDER_DATE");

            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.query(connection -> {
                PreparedStatement statement = connection.prepareStatement("SELECT id, user_name, product_name, category, amount, status, order_date FROM order_data ORDER BY id");
                statement.setFetchSize(properties.fetchSize());
                statement.setQueryTimeout(120);
                return statement;
            }, resultSet -> {
                long index = rows.incrementAndGet();
                Row row = sheet.createRow((int) index);
                row.createCell(0).setCellValue(resultSet.getLong("id"));
                row.createCell(1).setCellValue(resultSet.getString("user_name"));
                row.createCell(2).setCellValue(resultSet.getString("product_name"));
                row.createCell(3).setCellValue(resultSet.getString("category"));
                row.createCell(4).setCellValue(resultSet.getInt("amount"));
                row.createCell(5).setCellValue(resultSet.getString("status"));
                OffsetDateTime orderDate = resultSet.getObject("order_date", OffsetDateTime.class);
                row.createCell(6).setCellValue(orderDate.toString());
            }));
            try (var output = Files.newOutputStream(target)) { workbook.write(output); }
            return rows.get();
        }
    }
}
