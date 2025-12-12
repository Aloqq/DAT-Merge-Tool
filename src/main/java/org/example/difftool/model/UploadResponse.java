package org.example.difftool.model;

import java.util.List;

public class UploadResponse {
    private String format;
    private List<DatRecord> records;

    public UploadResponse() {
    }

    public UploadResponse(String format, List<DatRecord> records) {
        this.format = format;
        this.records = records;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public List<DatRecord> getRecords() {
        return records;
    }

    public void setRecords(List<DatRecord> records) {
        this.records = records;
    }
}

