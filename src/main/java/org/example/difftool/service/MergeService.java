package org.example.difftool.service;

import org.example.difftool.model.DatField;
import org.example.difftool.model.DatRecord;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class MergeService {

    public void copyOldToMerged(DatField field) {
        field.setMergedValue(field.getOldValue());
        field.setDeleted(false);
    }

    public void copyNewToMerged(DatField field) {
        field.setMergedValue(field.getNewValue());
        field.setDeleted(false);
    }

    public void resetMerged(DatField field) {
        field.setMergedValue(field.getNewValue() != null ? field.getNewValue() : field.getOldValue());
        field.setDeleted(false);
    }

    public void deleteField(DatField field) {
        field.setDeleted(true);
    }

    public void restoreField(DatField field) {
        field.setDeleted(false);
    }

    public void deleteRecord(DatRecord record) {
        record.setDeleted(true);
        record.getFields().forEach(f -> f.setDeleted(true));
    }

    public void restoreRecord(DatRecord record) {
        record.setDeleted(false);
        record.getFields().forEach(f -> f.setDeleted(false));
    }

    public String resolveMergedValue(DatField field) {
        if (field.isDeleted()) {
            return null;
        }
        String merged = field.getMergedValue();
        if (merged == null || merged.isEmpty()) {
            merged = field.getNewValue() != null ? field.getNewValue() : field.getOldValue();
        }
        return merged;
    }

    public boolean isRemovedField(DatField field) {
        return Objects.equals(field.getStatus(), "removed");
    }
}

