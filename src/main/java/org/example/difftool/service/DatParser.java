package org.example.difftool.service;

import org.example.difftool.model.DatFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class DatParser {

    private static final Logger logger = LoggerFactory.getLogger(DatParser.class);
    private static final String BLOCK_START_ITEM = "item_name_begin";
    private static final String BLOCK_END_ITEM = "item_name_end";
    private static final String BLOCK_START_STRING = "string_begin";
    private static final String BLOCK_END_STRING = "string_end";
    private static final String DEFAULT_KEY = "value";
    private static final Pattern CONFIG_SECTION_PATTERN = Pattern.compile("^\\s*\\[[^]]+]", Pattern.MULTILINE);

    public ParseResult parse(String content) {
        if (content == null) {
            logger.warn("Передан null контент");
            return new ParseResult(DatFormat.LINE, new LinkedHashMap<>());
        }
        DatFormat format = detectFormat(content);
        logger.debug("Определен формат: {}", format);
        Map<String, LinkedHashMap<String, String>> records;
        switch (format) {
            case BLOCK -> records = parseBlock(content);
            case CONFIG -> records = parseConfig(content);
            case LINE -> records = parseLine(content);
            default -> records = parseLine(content);
        }
        logger.info("Распарсено записей: {}", records.size());
        return new ParseResult(format, records);
    }

    private DatFormat detectFormat(String content) {
        if (content == null) {
            return DatFormat.LINE;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.contains(BLOCK_START_ITEM) || lower.contains(BLOCK_START_STRING)) {
            return DatFormat.BLOCK;
        }
        if (CONFIG_SECTION_PATTERN.matcher(content).find()) {
            return DatFormat.CONFIG;
        }
        return DatFormat.LINE;
    }

    private Map<String, LinkedHashMap<String, String>> parseLine(String content) {
        LinkedHashMap<String, LinkedHashMap<String, String>> records = new LinkedHashMap<>();
        String[] lines = content.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            int tabIdx = line.indexOf('\t');
            if (tabIdx < 0) {
                continue;
            }
            String id = line.substring(0, tabIdx).trim();
            String value = line.substring(tabIdx + 1).trim();
            if (id.isEmpty()) {
                continue;
            }
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(DEFAULT_KEY, value);
            records.put(id, fields);
        }
        return records;
    }

    private Map<String, LinkedHashMap<String, String>> parseBlock(String content) {
        LinkedHashMap<String, LinkedHashMap<String, String>> records = new LinkedHashMap<>();

        LinkedHashMap<String, String> currentBlock = null;
        String currentId = null;
        String[] lines = content.split("\\R");
        logger.debug("Обработка {} строк в BLOCK режиме", lines.length);

        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            
            // Проверяем, является ли это однострочным блоком (string формат)
            if (line.contains(BLOCK_START_STRING) && line.contains(BLOCK_END_STRING)) {
                // Однострочный string блок
                parseSingleLineStringBlock(line, records);
                continue;
            }
            
            // Многострочный блок (item_name или string)
            String[] tokens = line.split("\\t");
            for (String token : tokens) {
                String piece = token.trim();
                if (piece.isEmpty()) {
                    continue;
                }
                if (piece.equalsIgnoreCase(BLOCK_START_ITEM) || piece.equalsIgnoreCase(BLOCK_START_STRING)) {
                    currentBlock = new LinkedHashMap<>();
                    currentId = null;
                    continue;
                }
                if (piece.equalsIgnoreCase(BLOCK_END_ITEM) || piece.equalsIgnoreCase(BLOCK_END_STRING)) {
                    if (currentBlock != null && currentId != null) {
                        records.put(currentId, currentBlock);
                        logger.trace("Добавлен блок с id={}, полей={}", currentId, currentBlock.size());
                    } else if (currentBlock != null) {
                        logger.warn("Найден блок без id, пропускаем. Поля: {}", currentBlock.keySet());
                    }
                    currentBlock = null;
                    currentId = null;
                    continue;
                }
                if (currentBlock != null) {
                    int eq = piece.indexOf('=');
                    if (eq < 0) {
                        continue;
                    }
                    String key = piece.substring(0, eq).trim();
                    String value = piece.substring(eq + 1).trim();
                    // Сохраняем значение как есть (с квадратными скобками, если есть)
                    currentBlock.put(key, value);
                    // Для item_name блоков используется "id", для string блоков - "stringID"
                    if ("id".equalsIgnoreCase(key) || "stringid".equalsIgnoreCase(key)) {
                        // Для ID убираем скобки для использования как ключа
                        currentId = stripBrackets(value);
                        if (currentId == null || currentId.isEmpty()) {
                            currentId = value; // Если скобок нет, используем как есть
                        }
                    }
                }
            }
        }

        if (currentBlock != null && currentId != null) {
            records.put(currentId, currentBlock);
            logger.debug("Добавлен финальный блок с id={}, полей={}", currentId, currentBlock.size());
        } else if (currentBlock != null) {
            logger.warn("Остался незавершенный блок без id");
        }

        logger.info("Итого распарсено блоков: {}", records.size());
        return records;
    }
    
    /**
     * Парсит однострочный string блок формата: string_begin	stringID=1	string=[...]	string_end
     */
    private void parseSingleLineStringBlock(String line, Map<String, LinkedHashMap<String, String>> records) {
        LinkedHashMap<String, String> block = new LinkedHashMap<>();
        String[] tokens = line.split("\\t");
        String currentId = null;
        
        for (String token : tokens) {
            String piece = token.trim();
            if (piece.isEmpty() || 
                piece.equalsIgnoreCase(BLOCK_START_STRING) || 
                piece.equalsIgnoreCase(BLOCK_END_STRING)) {
                continue;
            }
            
            int eq = piece.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = piece.substring(0, eq).trim();
            String value = piece.substring(eq + 1).trim();
            block.put(key, value);
            
            if ("stringid".equalsIgnoreCase(key)) {
                currentId = stripBrackets(value);
                if (currentId == null || currentId.isEmpty()) {
                    currentId = value;
                }
            }
        }
        
        if (currentId != null && !block.isEmpty()) {
            records.put(currentId, block);
            logger.trace("Добавлен однострочный string блок с id={}, полей={}", currentId, block.size());
        } else {
            logger.warn("Не удалось распарсить однострочный string блок: {}", line);
        }
    }

    private Map<String, LinkedHashMap<String, String>> parseConfig(String content) {
        LinkedHashMap<String, LinkedHashMap<String, String>> records = new LinkedHashMap<>();
        String[] lines = content.split("\\R");
        String currentSection = "DEFAULT";

        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                continue;
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length() - 1).trim();
                if (currentSection.isEmpty()) {
                    currentSection = "DEFAULT";
                }
                continue;
            }

            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (key.isEmpty()) {
                continue;
            }

            String recordId = currentSection + "::" + key;
            String uniqueId = recordId;
            int duplicateIndex = 1;
            while (records.containsKey(uniqueId)) {
                uniqueId = recordId + "#" + duplicateIndex++;
            }

            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("section", currentSection);
            fields.put("key", key);
            fields.put("value", value);
            records.put(uniqueId, fields);
        }

        logger.info("CONFIG формат: распарсено {} записей", records.size());
        return records;
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

    public static class ParseResult {
        private final DatFormat format;
        private final Map<String, LinkedHashMap<String, String>> records;

        public ParseResult(DatFormat format, Map<String, LinkedHashMap<String, String>> records) {
            this.format = format;
            this.records = records;
        }

        public DatFormat getFormat() {
            return format;
        }

        public Map<String, LinkedHashMap<String, String>> getRecords() {
            return records;
        }
    }
}

