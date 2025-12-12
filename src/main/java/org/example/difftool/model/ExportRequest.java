package org.example.difftool.model;

import java.util.List;

public class ExportRequest {
    private String format;
    private List<DatRecord> records;

    public ExportRequest() {
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

