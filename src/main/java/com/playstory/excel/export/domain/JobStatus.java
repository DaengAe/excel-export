package com.playstory.excel.export.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum JobStatus {
    PENDING, PROCESSING, DONE, FAILED;

    @JsonValue
    public String value() { return name().toLowerCase(); }
}
