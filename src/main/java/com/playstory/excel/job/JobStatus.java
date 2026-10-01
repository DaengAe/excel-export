package com.playstory.excel.job;

import com.fasterxml.jackson.annotation.JsonValue;

public enum JobStatus {
    PENDING, PROCESSING, DONE, FAILED;

    @JsonValue
    public String value() { return name().toLowerCase(); }
}
