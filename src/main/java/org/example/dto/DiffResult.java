package org.example.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO класс для хранения результатов сравнения файлов.
 */
public class DiffResult {
    private List<Object> removed = new ArrayList<>();
    private List<Object> added = new ArrayList<>();
    private List<FieldChange> changed = new ArrayList<>();
    private List<StructureChange> structureChanges = new ArrayList<>();

    public DiffResult() {
    }

    public List<Object> getRemoved() {
        return removed;
    }

    public void setRemoved(List<Object> removed) {
        this.removed = removed;
    }

    public List<Object> getAdded() {
        return added;
    }

    public void setAdded(List<Object> added) {
        this.added = added;
    }

    public List<FieldChange> getChanged() {
        return changed;
    }

    public void setChanged(List<FieldChange> changed) {
        this.changed = changed;
    }

    public List<StructureChange> getStructureChanges() {
        return structureChanges;
    }

    public void setStructureChanges(List<StructureChange> structureChanges) {
        this.structureChanges = structureChanges;
    }

    /**
     * Класс для представления изменения поля объекта.
     */
    public static class FieldChange {
        private String id;
        private String field;
        private Object oldValue;
        private Object newValue;

        public FieldChange() {
        }

        public FieldChange(String id, String field, Object oldValue, Object newValue) {
            this.id = id;
            this.field = field;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public Object getOld() {
            return oldValue;
        }

        public void setOld(Object oldValue) {
            this.oldValue = oldValue;
        }

        public Object getNew() {
            return newValue;
        }

        public void setNew(Object newValue) {
            this.newValue = newValue;
        }
    }

    /**
     * Класс для представления изменений структуры.
     */
    public static class StructureChange {
        private String type; // "new_field", "removed_field", "type_mismatch", "format_change"
        private String description;
        private String id; // ID объекта, если применимо

        public StructureChange() {
        }

        public StructureChange(String type, String description) {
            this.type = type;
            this.description = description;
        }

        public StructureChange(String type, String description, String id) {
            this.type = type;
            this.description = description;
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}

