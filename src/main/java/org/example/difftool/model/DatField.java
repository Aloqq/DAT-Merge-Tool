package org.example.difftool.model;

public class DatField {
    private String key;
    private String oldValue;
    private String newValue;
    private String mergedValue;
    private String status;
    private boolean deleted;

    public DatField() {
    }

    public DatField(String key, String oldValue, String newValue, String mergedValue, String status) {
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.mergedValue = mergedValue;
        this.status = status;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getMergedValue() {
        return mergedValue;
    }

    public void setMergedValue(String mergedValue) {
        this.mergedValue = mergedValue;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}

