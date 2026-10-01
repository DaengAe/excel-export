package com.playstory.excel.export.port;

import java.io.IOException;
import java.nio.file.Path;

/** Port for writing order data to an XLSX target. */
public interface ExcelFileExporter {
    long exportTo(Path target) throws IOException;
}
