package org.example.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.DiffResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Класс для сравнения JSON-файлов.
 * Поддерживает сравнение объектов по ID и выявление различий в полях.
 */
public class JsonComparator implements DataComparator {
    private static final Logger logger = LoggerFactory.getLogger(JsonComparator.class);
    private static final String ID_FIELD = "id";
    private final ObjectMapper objectMapper;

    public JsonComparator() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Сравнивает два JSON-файла и возвращает результат сравнения.
     *
     * @param oldFile файл из старой директории
     * @param newFile файл из новой директории
     * @return результат сравнения
     */
    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".json");
    }

    @Override
    public DiffResult compare(File oldFile, File newFile) {
        DiffResult result = new DiffResult();

        try {
            JsonNode oldJson = parseJsonFile(oldFile);
            JsonNode newJson = parseJsonFile(newFile);

            if (oldJson == null && newJson == null) {
                logger.warn("Оба файла {} и {} не содержат валидный JSON", oldFile.getName(), newFile.getName());
                return result;
            }

            if (oldJson == null) {
                // Файл был добавлен
                if (newJson.isArray()) {
                    result.getAdded().addAll(convertToList(newJson));
                } else {
                    result.getAdded().add(convertToObject(newJson));
                }
                return result;
            }

            if (newJson == null) {
                // Файл был удален
                if (oldJson.isArray()) {
                    result.getRemoved().addAll(convertToList(oldJson));
                } else {
                    result.getRemoved().add(convertToObject(oldJson));
                }
                return result;
            }

            // Сравнение структур
            compareStructures(oldJson, newJson, result);

            // Сравнение данных
            if (oldJson.isArray() && newJson.isArray()) {
                compareArrays(oldJson, newJson, result);
            } else if (oldJson.isObject() && newJson.isObject()) {
                compareObjects(oldJson, newJson, result, null);
            } else {
                // Разные типы корневых элементов
                result.getStructureChanges().add(new DiffResult.StructureChange(
                    "format_change",
                    String.format("Изменен тип корневого элемента: %s -> %s", 
                        oldJson.getNodeType(), newJson.getNodeType())
                ));
            }

        } catch (Exception e) {
            logger.error("Ошибка при сравнении файлов {} и {}: {}", 
                oldFile.getName(), newFile.getName(), e.getMessage(), e);
        }

        return result;
    }

    /**
     * Сравнивает два JSON-файла, когда один из них отсутствует.
     */
    @Override
    public DiffResult compareWithMissing(File existingFile, boolean isOld) {
        DiffResult result = new DiffResult();

        try {
            JsonNode json = parseJsonFile(existingFile);
            if (json == null) {
                return result;
            }

            List<Object> data = json.isArray() ? convertToList(json) : List.of(convertToObject(json));

            if (isOld) {
                result.getRemoved().addAll(data);
            } else {
                result.getAdded().addAll(data);
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке файла {}: {}", existingFile.getName(), e.getMessage(), e);
        }

        return result;
    }

    /**
     * Парсит JSON-файл с обработкой ошибок.
     */
    private JsonNode parseJsonFile(File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        try {
            return objectMapper.readTree(file);
        } catch (IOException e) {
            logger.warn("Не удалось распарсить JSON-файл {}: {}", file.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Сравнивает два массива объектов по ID.
     */
    private void compareArrays(JsonNode oldArray, JsonNode newArray, DiffResult result) {
        Map<String, JsonNode> oldMap = createIdMap(oldArray);
        Map<String, JsonNode> newMap = createIdMap(newArray);

        // Находим удаленные элементы
        for (String id : oldMap.keySet()) {
            if (!newMap.containsKey(id)) {
                result.getRemoved().add(convertToObject(oldMap.get(id)));
            }
        }

        // Находим добавленные элементы
        for (String id : newMap.keySet()) {
            if (!oldMap.containsKey(id)) {
                result.getAdded().add(convertToObject(newMap.get(id)));
            }
        }

        // Находим измененные элементы
        for (String id : oldMap.keySet()) {
            if (newMap.containsKey(id)) {
                compareObjects(oldMap.get(id), newMap.get(id), result, id);
            }
        }
    }

    /**
     * Создает карту объектов по ID.
     */
    private Map<String, JsonNode> createIdMap(JsonNode array) {
        Map<String, JsonNode> map = new HashMap<>();

        for (JsonNode node : array) {
            if (node.isObject()) {
                JsonNode idNode = node.get(ID_FIELD);
                if (idNode != null && idNode.isTextual()) {
                    map.put(idNode.asText(), node);
                } else if (idNode != null && idNode.isNumber()) {
                    map.put(String.valueOf(idNode.asLong()), node);
                } else {
                    // Если нет ID, используем хеш как ключ
                    map.put(String.valueOf(node.hashCode()), node);
                }
            }
        }

        return map;
    }

    /**
     * Сравнивает два объекта по полям.
     */
    private void compareObjects(JsonNode oldObj, JsonNode newObj, DiffResult result, String objectId) {
        Set<String> oldFields = new HashSet<>();
        Set<String> newFields = new HashSet<>();

        oldObj.fieldNames().forEachRemaining(oldFields::add);
        newObj.fieldNames().forEachRemaining(newFields::add);

        // Находим новые поля
        Set<String> addedFields = new HashSet<>(newFields);
        addedFields.removeAll(oldFields);
        for (String field : addedFields) {
            result.getStructureChanges().add(new DiffResult.StructureChange(
                "new_field",
                String.format("Добавлено новое поле: %s", field),
                objectId
            ));
        }

        // Находим удаленные поля
        Set<String> removedFields = new HashSet<>(oldFields);
        removedFields.removeAll(newFields);
        for (String field : removedFields) {
            result.getStructureChanges().add(new DiffResult.StructureChange(
                "removed_field",
                String.format("Удалено поле: %s", field),
                objectId
            ));
        }

        // Сравниваем общие поля
        Set<String> commonFields = new HashSet<>(oldFields);
        commonFields.retainAll(newFields);

        for (String field : commonFields) {
            JsonNode oldValue = oldObj.get(field);
            JsonNode newValue = newObj.get(field);

            if (!compareJsonNodes(oldValue, newValue)) {
                // Проверяем тип
                if (oldValue.getNodeType() != newValue.getNodeType()) {
                    result.getStructureChanges().add(new DiffResult.StructureChange(
                        "type_mismatch",
                        String.format("Изменен тип поля %s: %s -> %s", 
                            field, oldValue.getNodeType(), newValue.getNodeType()),
                        objectId
                    ));
                } else {
                    // Значение изменилось
                    DiffResult.FieldChange change = new DiffResult.FieldChange();
                    change.setId(objectId);
                    change.setField(field);
                    change.setOld(convertJsonValue(oldValue));
                    change.setNew(convertJsonValue(newValue));
                    result.getChanged().add(change);
                }
            }
        }
    }

    /**
     * Сравнивает два JSON-узла на равенство.
     */
    private boolean compareJsonNodes(JsonNode oldNode, JsonNode newNode) {
        if (oldNode == null && newNode == null) {
            return true;
        }
        if (oldNode == null || newNode == null) {
            return false;
        }
        return oldNode.equals(newNode);
    }

    /**
     * Преобразует JSON-узел в Java-объект.
     */
    private Object convertJsonValue(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            if (node.isInt()) {
                return node.asInt();
            } else if (node.isLong()) {
                return node.asLong();
            } else {
                return node.asDouble();
            }
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isNull()) {
            return null;
        } else if (node.isArray() || node.isObject()) {
            return convertToObject(node);
        }
        return node.toString();
    }

    /**
     * Преобразует JSON-узел в Map для сериализации.
     */
    private Object convertToObject(JsonNode node) {
        try {
            return objectMapper.convertValue(node, Object.class);
        } catch (Exception e) {
            logger.warn("Не удалось преобразовать JSON-узел: {}", e.getMessage());
            return node.toString();
        }
    }

    /**
     * Преобразует JSON-массив в список объектов.
     */
    private List<Object> convertToList(JsonNode array) {
        List<Object> list = new ArrayList<>();
        for (JsonNode node : array) {
            list.add(convertToObject(node));
        }
        return list;
    }

    /**
     * Сравнивает структуры JSON для выявления общих изменений структуры.
     */
    private void compareStructures(JsonNode oldJson, JsonNode newJson, DiffResult result) {
        if (oldJson.isArray() != newJson.isArray()) {
            result.getStructureChanges().add(new DiffResult.StructureChange(
                "format_change",
                String.format("Изменен формат: массив <-> объект")
            ));
        }
    }
}

