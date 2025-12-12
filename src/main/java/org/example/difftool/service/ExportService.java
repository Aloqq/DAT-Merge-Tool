package org.example.difftool.service;

import org.example.difftool.model.DatField;
import org.example.difftool.model.DatFormat;
import org.example.difftool.model.DatRecord;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private static final String DEFAULT_LINE_KEY = "value";
    private final MergeService mergeService;

    public ExportService(MergeService mergeService) {
        this.mergeService = mergeService;
    }

    public byte[] export(List<DatRecord> records, DatFormat format) {
        String payload = switch (format) {
            case BLOCK -> exportBlock(records);
            case CONFIG -> exportConfig(records);
            case LINE -> exportLine(records);
            default -> exportLine(records);
        };
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private String exportLine(List<DatRecord> records) {
        return records.stream()
            .filter(record -> !record.isDeleted())
            .map(record -> {
                DatField field = record.getFields().stream()
                    .filter(f -> DEFAULT_LINE_KEY.equalsIgnoreCase(f.getKey()))
                    .findFirst()
                    .orElse(record.getFields().isEmpty() ? null : record.getFields().get(0));
                if (field == null) {
                    return null;
                }
                String merged = mergeService.resolveMergedValue(field);
                if (merged == null) {
                    return null;
                }
                return record.getId() + "\t" + merged;
            })
            .filter(line -> line != null && !line.isEmpty())
            .collect(Collectors.joining("\n"));
    }

    private String exportBlock(List<DatRecord> records) {
        StringBuilder builder = new StringBuilder();
        for (DatRecord record : records) {
            if (record.isDeleted()) {
                continue;
            }
            
            // Определяем тип блока: если есть поле stringID, то это string блок
            boolean isStringBlock = record.getFields().stream()
                .anyMatch(field -> "stringid".equalsIgnoreCase(field.getKey()));
            
            if (isStringBlock) {
                // Однострочный формат string блока
                builder.append("string_begin\t");
                boolean hasStringIdField = false;
                StringBuilder fieldsBuilder = new StringBuilder();
                
                for (DatField field : record.getFields()) {
                    if (field.isDeleted()) {
                        continue;
                    }
                    String merged = mergeService.resolveMergedValue(field);
                    if (merged == null) {
                        continue;
                    }
                    if ("stringid".equalsIgnoreCase(field.getKey())) {
                        hasStringIdField = true;
                        // stringID должен быть первым полем
                        builder.append(field.getKey()).append("=").append(merged).append("\t");
                    } else {
                        fieldsBuilder.append(field.getKey()).append("=").append(merged).append("\t");
                    }
                }
                
                // Если stringID отсутствует, добавляем его из record.getId() в начало
                if (!hasStringIdField) {
                    builder.append("stringID=").append(record.getId()).append("\t");
                }
                
                builder.append(fieldsBuilder);
                builder.append("string_end\n");
            } else {
                // Многострочный формат item_name блока
                builder.append("item_name_begin\n");
                boolean hasIdField = record.getFields().stream()
                    .anyMatch(field -> "id".equalsIgnoreCase(field.getKey()));
                if (!hasIdField) {
                    builder.append("    id=").append(record.getId()).append("\n");
                }
                for (DatField field : record.getFields()) {
                    if (field.isDeleted()) {
                        continue;
                    }
                    String merged = mergeService.resolveMergedValue(field);
                    if (merged == null) {
                        continue;
                    }
                    builder.append("    ").append(field.getKey())
                        .append("=").append(merged).append("\n");
                }
                builder.append("item_name_end\n\n");
            }
        }
        return builder.toString().trim();
    }

    private String exportConfig(List<DatRecord> records) {
        StringBuilder builder = new StringBuilder();
        LinkedHashMap<String, List<String>> sections = new LinkedHashMap<>();

        for (DatRecord record : records) {
            if (record.isDeleted()) {
                continue;
            }
            DatField sectionField = findField(record, "section");
            DatField keyField = findField(record, "key");
            DatField valueField = findField(record, "value");

            String section = resolveFieldValue(sectionField);
            if (section == null || section.isEmpty()) {
                section = "DEFAULT";
            }
            String key = resolveFieldValue(keyField);
            if (key == null || key.isEmpty() || valueField == null) {
                continue;
            }
            String mergedValue = mergeService.resolveMergedValue(valueField);
            if (mergedValue == null) {
                continue;
            }

            sections.computeIfAbsent(section, s -> new ArrayList<>())
                .add(key + "=" + mergedValue);
        }

        sections.forEach((section, lines) -> {
            builder.append("[").append(section).append("]\n");
            for (String line : lines) {
                builder.append(line).append("\n");
            }
            builder.append("\n");
        });

        return builder.toString().trim();
    }

    private DatField findField(DatRecord record, String fieldName) {
        if (record == null || record.getFields() == null) {
            return null;
        }
        return record.getFields().stream()
            .filter(field -> fieldName.equalsIgnoreCase(field.getKey()))
            .findFirst()
            .orElse(null);
    }

    private String resolveFieldValue(DatField field) {
        if (field == null) {
            return null;
        }
        if (field.getMergedValue() != null) {
            return String.valueOf(field.getMergedValue());
        }
        if (field.getNewValue() != null) {
            return String.valueOf(field.getNewValue());
        }
        if (field.getOldValue() != null) {
            return String.valueOf(field.getOldValue());
        }
        return null;
    }
}

