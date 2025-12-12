package org.example.comparator;

import org.example.dto.DiffResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Компаратор для файлов формата ItemName (блоки между item_name_begin/item_name_end).
 */
public class ItemNameComparator implements DataComparator {
    private static final Logger logger = LoggerFactory.getLogger(ItemNameComparator.class);

    private static final String BLOCK_START = "item_name_begin";
    private static final String BLOCK_END = "item_name_end";
    private static final String ID_FIELD = "id";

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".txt");
    }

    @Override
    public DiffResult compare(File oldFile, File newFile) {
        DiffResult result = new DiffResult();

        Map<String, Map<String, String>> oldBlocks = parseFile(oldFile);
        Map<String, Map<String, String>> newBlocks = parseFile(newFile);

        if (oldBlocks.isEmpty() && newBlocks.isEmpty()) {
            logger.warn("Файлы {} и {} не содержат блоков для сравнения",
                oldFile.getName(), newFile.getName());
            return result;
        }

        // Удаленные блоки
        for (String id : oldBlocks.keySet()) {
            if (!newBlocks.containsKey(id)) {
                result.getRemoved().add(oldBlocks.get(id));
            }
        }

        // Добавленные блоки
        for (String id : newBlocks.keySet()) {
            if (!oldBlocks.containsKey(id)) {
                result.getAdded().add(newBlocks.get(id));
            }
        }

        // Измененные блоки
        for (String id : oldBlocks.keySet()) {
            if (newBlocks.containsKey(id)) {
                compareFields(id, oldBlocks.get(id), newBlocks.get(id), result);
            }
        }

        return result;
    }

    @Override
    public DiffResult compareWithMissing(File existingFile, boolean isOld) {
        DiffResult result = new DiffResult();
        Map<String, Map<String, String>> blocks = parseFile(existingFile);

        if (blocks.isEmpty()) {
            return result;
        }

        if (isOld) {
            result.getRemoved().addAll(blocks.values());
        } else {
            result.getAdded().addAll(blocks.values());
        }

        return result;
    }

    private Map<String, Map<String, String>> parseFile(File file) {
        Map<String, Map<String, String>> blocks = new LinkedHashMap<>();

        if (file == null || !file.exists()) {
            return blocks;
        }

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Map<String, String> currentBlock = null;
            String currentId = null;
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                String[] tokens = line.split("\\t");
                for (String rawToken : tokens) {
                    String token = rawToken.trim();
                    if (token.isEmpty()) {
                        continue;
                    }

                    if (token.equals(BLOCK_START)) {
                        currentBlock = new LinkedHashMap<>();
                        currentId = null;
                        continue;
                    }

                    if (token.equals(BLOCK_END)) {
                        if (currentBlock != null) {
                            if (currentId == null) {
                                logger.warn("Пропущен блок без id в файле {} (строка {})",
                                    file.getName(), lineNumber);
                            } else {
                                blocks.put(currentId, currentBlock);
                            }
                        }
                        currentBlock = null;
                        currentId = null;
                        continue;
                    }

                    if (currentBlock != null) {
                        processToken(token, currentBlock);
                        if (currentBlock.containsKey(ID_FIELD)) {
                            currentId = currentBlock.get(ID_FIELD);
                        }
                    }
                }
            }

            // Закрываем висящий блок (если файл не завершился item_name_end)
            if (currentBlock != null) {
                if (currentId == null) {
                    logger.warn("Пропущен блок без id в файле {} (конец файла)", file.getName());
                } else {
                    blocks.put(currentId, currentBlock);
                }
            }

        } catch (IOException e) {
            logger.warn("Не удалось прочитать файл {}: {}", file.getName(), e.getMessage());
        }

        return blocks;
    }

    private void processToken(String token, Map<String, String> block) {
        int eqIndex = token.indexOf('=');
        if (eqIndex < 0) {
            return;
        }

        String key = token.substring(0, eqIndex).trim();
        String value = token.substring(eqIndex + 1).trim();
        value = stripBrackets(value);
        block.put(key, value);
    }

    private String stripBrackets(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private void compareFields(String id,
                               Map<String, String> oldFields,
                               Map<String, String> newFields,
                               DiffResult result) {

        Set<String> oldKeys = new HashSet<>(oldFields.keySet());
        Set<String> newKeys = new HashSet<>(newFields.keySet());

        oldKeys.remove(ID_FIELD);
        newKeys.remove(ID_FIELD);

        // Новые поля
        Set<String> addedFields = new HashSet<>(newKeys);
        addedFields.removeAll(oldKeys);
        for (String field : addedFields) {
            result.getStructureChanges().add(new DiffResult.StructureChange(
                "new_field",
                String.format("Добавлено поле %s", field),
                id
            ));
        }

        // Удаленные поля
        Set<String> removedFields = new HashSet<>(oldKeys);
        removedFields.removeAll(newKeys);
        for (String field : removedFields) {
            result.getStructureChanges().add(new DiffResult.StructureChange(
                "removed_field",
                String.format("Удалено поле %s", field),
                id
            ));
        }

        // Общие поля
        Set<String> commonFields = new HashSet<>(oldKeys);
        commonFields.retainAll(newKeys);
        for (String field : commonFields) {
            String oldValue = oldFields.get(field);
            String newValue = newFields.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                DiffResult.FieldChange change = new DiffResult.FieldChange();
                change.setId(id);
                change.setField(field);
                change.setOld(oldValue);
                change.setNew(newValue);
                result.getChanged().add(change);
            }
        }
    }
}

