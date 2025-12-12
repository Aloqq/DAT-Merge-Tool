package org.example.difftool.service;

import org.example.difftool.model.DatField;
import org.example.difftool.model.DatRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DiffService {

    private static final Logger logger = LoggerFactory.getLogger(DiffService.class);

    public List<DatRecord> buildDiff(Map<String, LinkedHashMap<String, String>> oldRecords,
                                     Map<String, LinkedHashMap<String, String>> newRecords) {

        logger.info("Построение diff: OLD записей={}, NEW записей={}", oldRecords.size(), newRecords.size());
        List<DatRecord> result = new ArrayList<>();
        Set<String> allIds = new LinkedHashSet<>();
        allIds.addAll(newRecords.keySet());
        allIds.addAll(oldRecords.keySet());
        logger.debug("Всего уникальных ID: {}", allIds.size());

        for (String id : allIds) {
            LinkedHashMap<String, String> oldFields = oldRecords.getOrDefault(id, new LinkedHashMap<>());
            LinkedHashMap<String, String> newFields = newRecords.getOrDefault(id, new LinkedHashMap<>());

            Set<String> allKeys = new LinkedHashSet<>();
            allKeys.addAll(newFields.keySet());
            allKeys.addAll(oldFields.keySet());

            List<DatField> diffFields = new ArrayList<>();
            for (String key : allKeys) {
                String oldValue = oldFields.get(key);
                String newValue = newFields.get(key);
                
                // Нормализуем значения для сравнения (убираем лишние пробелы)
                String normalizedOld = normalizeValue(oldValue);
                String normalizedNew = normalizeValue(newValue);
                
                DatField field = new DatField();
                field.setKey(key);
                field.setOldValue(oldValue); // Сохраняем оригинальные значения для отображения
                field.setNewValue(newValue);
                field.setMergedValue(newValue != null ? newValue : oldValue);
                field.setStatus(resolveStatus(normalizedOld, normalizedNew)); // Сравниваем нормализованные
                diffFields.add(field);
            }

            DatRecord record = new DatRecord();
            record.setId(id);
            record.setFields(diffFields);
            result.add(record);
        }
        return result;
    }

    private String resolveStatus(String oldValue, String newValue) {
        if (oldValue == null && newValue == null) {
            return "same";
        }
        if (oldValue == null) {
            return "added";
        }
        if (newValue == null) {
            return "removed";
        }
        if (!Objects.equals(oldValue, newValue)) {
            return "changed";
        }
        return "same";
    }
    
    /**
     * Нормализует значение для сравнения: убирает лишние пробелы, но сохраняет структуру.
     */
    private String normalizeValue(String value) {
        if (value == null) {
            return null;
        }
        // Убираем только ведущие и завершающие пробелы, внутренние пробелы сохраняем
        return value.trim();
    }
}

