package org.example.difftool.model;

import java.util.ArrayList;
import java.util.List;

public class DatRecord {
    private String id;
    private List<DatField> fields = new ArrayList<>();
    private boolean deleted;

    public DatRecord() {
    }

    public DatRecord(String id) {
        this.id = id;
    }

    public DatRecord(String id, List<DatField> fields) {
        this.id = id;
        this.fields = fields;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<DatField> getFields() {
        return fields;
    }

    public void setFields(List<DatField> fields) {
        this.fields = fields;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}

