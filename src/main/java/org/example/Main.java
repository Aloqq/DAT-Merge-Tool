package org.example;

import org.example.comparator.DataComparator;
import org.example.comparator.FileComparator;
import org.example.comparator.ItemNameComparator;
import org.example.comparator.JsonComparator;
import org.example.dto.DiffResult;
import org.example.writer.DiffWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;

/**
 * Главный класс приложения для сравнения данных в двух директориях.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final String OLD_DIR = "diff/old";
    private static final String NEW_DIR = "diff/new";
    private static final String OUTPUT_DIR = "diff/output";

    public static void main(String[] args) {
        logger.info("Запуск инструмента сравнения директорий");
        logger.info("Старая директория: {}", OLD_DIR);
        logger.info("Новая директория: {}", NEW_DIR);
        logger.info("Выходная директория: {}", OUTPUT_DIR);

        try {
            // Создаем компоненты
            List<DataComparator> comparators = List.of(
                new JsonComparator(),
                new ItemNameComparator()
            );
            FileComparator fileComparator = new FileComparator(comparators);
            DiffWriter diffWriter = new DiffWriter();

            // Сравниваем директории
            Map<String, DiffResult> results = fileComparator.compareDirectories(OLD_DIR, NEW_DIR);

            if (results.isEmpty()) {
                logger.warn("Не найдено файлов для сравнения");
                return;
            }

            // Записываем результаты
            diffWriter.writeResults(results, OUTPUT_DIR);

            // Выводим статистику
            printStatistics(results);

            logger.info("Сравнение завершено успешно");

        } catch (Exception e) {
            logger.error("Критическая ошибка при выполнении программы: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * Выводит статистику по результатам сравнения.
     */
    private static void printStatistics(Map<String, DiffResult> results) {
        int totalFiles = results.size();
        int filesWithChanges = 0;
        int totalAdded = 0;
        int totalRemoved = 0;
        int totalChanged = 0;
        int totalStructureChanges = 0;

        for (DiffResult result : results.values()) {
            boolean hasChanges = false;

            if (!result.getAdded().isEmpty()) {
                totalAdded += result.getAdded().size();
                hasChanges = true;
            }
            if (!result.getRemoved().isEmpty()) {
                totalRemoved += result.getRemoved().size();
                hasChanges = true;
            }
            if (!result.getChanged().isEmpty()) {
                totalChanged += result.getChanged().size();
                hasChanges = true;
            }
            if (!result.getStructureChanges().isEmpty()) {
                totalStructureChanges += result.getStructureChanges().size();
                hasChanges = true;
            }

            if (hasChanges) {
                filesWithChanges++;
            }
        }

        logger.info("=== Статистика сравнения ===");
        logger.info("Всего файлов обработано: {}", totalFiles);
        logger.info("Файлов с изменениями: {}", filesWithChanges);
        logger.info("Добавлено элементов: {}", totalAdded);
        logger.info("Удалено элементов: {}", totalRemoved);
        logger.info("Изменено полей: {}", totalChanged);
        logger.info("Изменений структуры: {}", totalStructureChanges);
    }
}
