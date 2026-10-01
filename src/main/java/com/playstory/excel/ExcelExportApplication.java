package com.playstory.excel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ExcelExportApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExcelExportApplication.class, args);
    }
}
