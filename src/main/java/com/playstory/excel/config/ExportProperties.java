package com.playstory.excel.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.export")
public record ExportProperties(Path directory, int fetchSize, int rowWindowSize, int leaseMinutes) { }
