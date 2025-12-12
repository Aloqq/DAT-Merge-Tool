package org.example.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.dto.DiffResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Класс для записи результатов сравнения в файлы.
 */
public class DiffWriter {
    private static final Logger logger = LoggerFactory.getLogger(DiffWriter.class);
    private final ObjectMapper objectMapper;

    public DiffWriter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Записывает результаты сравнения в директорию output.
     *
     * @param results карта относительных путей файлов к результатам сравнения
     * @param outputDir директория для сохранения результатов
     */
    public void writeResults(Map<String, DiffResult> results, String outputDir) {
        try {
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);

            for (Map.Entry<String, DiffResult> entry : results.entrySet()) {
                String relativePath = entry.getKey();
                DiffResult result = entry.getValue();

                // Создаем имя выходного файла
                String outputFileName = generateOutputFileName(relativePath);
                File outputFile = outputPath.resolve(outputFileName).toFile();

                // Записываем результат
                objectMapper.writeValue(outputFile, result);
                logger.info("Результат записан в файл: {}", outputFile.getAbsolutePath());
            }

        } catch (IOException e) {
            logger.error("Ошибка при записи результатов: {}", e.getMessage(), e);
        }
    }

    /**
     * Генерирует имя выходного файла на основе относительного пути.
     * Например: "data/users.json" -> "data_users.diff.json"
     */
    private String generateOutputFileName(String relativePath) {
        // Заменяем разделители пути на подчеркивания
        String fileName = relativePath.replace(File.separator, "_")
                                      .replace("/", "_")
                                      .replace("\\", "_");

        // Если файл уже имеет расширение .json, заменяем его на .diff.json
        if (fileName.toLowerCase().endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - 5) + ".diff.json";
        } else {
            fileName = fileName + ".diff.json";
        }

        return fileName;
    }
}

